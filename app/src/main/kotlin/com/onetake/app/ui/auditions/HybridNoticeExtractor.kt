package com.onetake.app.ui.auditions

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
data class LlmParsedNotice(
    val documentKind: String = "",
    val projectTitle: String = "",
    val projectType: String = "",
    val targetRoleName: String = "",
    val allAvailableRoles: List<String> = emptyList(),
    val roleSize: String = "",
    val tapeDueIsoDate: String = "",
    val tapeDueTime24h: String = "",
    val destination: String = "",
    val asks: List<String> = emptyList(),
    val characterNormalizationMap: Map<String, String> = emptyMap(),
    val firstScriptLineAnchor: String = ""
)

class HybridNoticeExtractor internal constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "HybridNoticeExtractor"
        const val LLM_INFERENCE_TIMEOUT_MS = 12000L
        const val MAX_INPUT_CHAR_SLICE = 1800

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        @Volatile
        private var instance: HybridNoticeExtractor? = null

        fun getInstance(context: Context): HybridNoticeExtractor =
            instance ?: synchronized(this) {
                instance ?: HybridNoticeExtractor(context.applicationContext).also { instance = it }
            }

        internal fun resetInstanceForTesting() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        fun buildPrompt(inputSlice: String, today: LocalDate): String {
            return """
Extract audition notice details as JSON (Today's date is $today):
{
  "documentKind": "CastingNotice|SidesOnly|NoticeWithSides",
  "projectTitle": "",
  "projectType": "",
  "targetRoleName": "",
  "allAvailableRoles": [],
  "roleSize": "",
  "tapeDueIsoDate": "YYYY-MM-DD",
  "tapeDueTime24h": "HH:MM",
  "destination": "",
  "asks": ["Slate first", "Landscape", "Full length", "Both profiles", "Under 3 min"],
  "characterNormalizationMap": {},
  "firstScriptLineAnchor": ""
}
Output valid JSON only.

Notice text:
$inputSlice
""".trimIndent()
        }

        fun parseLlmJson(rawOutput: String): LlmParsedNotice? {
            val startIndex = rawOutput.indexOf('{')
            val endIndex = rawOutput.lastIndexOf('}')
            if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
                return null
            }
            val jsonString = rawOutput.substring(startIndex, endIndex + 1)
            return runCatching {
                json.decodeFromString<LlmParsedNotice>(jsonString)
            }.getOrNull()
        }
    }

    private val mutex = Mutex()
    private var engine: Engine? = null

    internal var inferenceTimeoutMs: Long = LLM_INFERENCE_TIMEOUT_MS
    internal var testInferenceOverride: ((String) -> String?)? = null

    internal fun getEngineForTesting(): Engine? = engine

    fun warmUp(scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        scope.launch {
            performWarmUp()
        }
    }

    suspend fun performWarmUp() = withContext(Dispatchers.IO) {
        val downloadManager = ModelDownloadManager.getInstance(context)
        try {
            val client = Generation.getClient()
            val status = client.checkStatus()
            when (status) {
                FeatureStatus.AVAILABLE -> {
                    downloadManager.setUsingSystemAiCore()
                    runCatching { client.warmup() }
                }
                FeatureStatus.DOWNLOADABLE -> {
                    client.download().collect { /* downloading in background */ }
                }
                FeatureStatus.DOWNLOADING -> {
                    // System download in progress
                }
                FeatureStatus.UNAVAILABLE -> {
                    downloadManager.enqueueBackgroundDownload(context)
                }
                else -> {
                    downloadManager.enqueueBackgroundDownload(context)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            downloadManager.enqueueBackgroundDownload(context)
        }
    }

    suspend fun extractNotice(
        rawText: String,
        today: LocalDate = LocalDate.now()
    ): LlmParsedNotice? = withContext(Dispatchers.IO) {
        val inputSlice = rawText.take(MAX_INPUT_CHAR_SLICE)
        val prompt = buildPrompt(inputSlice, today)

        testInferenceOverride?.let { override ->
            val result = withTimeoutOrNull(inferenceTimeoutMs) {
                withContext(Dispatchers.Default) {
                    override(prompt)
                }
            }
            return@withContext result?.let { parseLlmJson(it) }
        }

        mutex.withLock {
            // Step 1: System AICore (Tier 1)
            try {
                val client = Generation.getClient()
                val status = client.checkStatus()
                if (status == FeatureStatus.AVAILABLE) {
                    val response = withTimeoutOrNull(inferenceTimeoutMs) {
                        client.generateContent(prompt)
                    }
                    val text = response?.candidates?.firstOrNull()?.text
                    if (!text.isNullOrBlank()) {
                        val parsed = parseLlmJson(text)
                        if (parsed != null) {
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // System AICore unavailable or failed, fall through to Tier 2
            }

            // Step 2: LiteRT-LM GPU -> CPU Cascade (Tier 2)
            val downloadManager = ModelDownloadManager.getInstance(context)
            val modelFile = downloadManager.ensureModelReadyOrStartDownload()
            if (modelFile == null || !modelFile.exists()) {
                return@withContext null
            }

            // Separate Engine Initialization from Inference Timeout:
            // Engine initialization (~3.2s on WebGPU) runs first and is not counted against inference timeout.
            val initStart = System.currentTimeMillis()
            val currentEngine = try {
                ensureEngineInitialized(modelFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize engine: ${e.message}", e)
                return@withContext null
            }
            val initMs = System.currentTimeMillis() - initStart
            if (initMs > 0) {
                Log.i(TAG, "Engine ready in ${initMs}ms")
            }

            // Wrapped strictly around inference execution
            val inferStart = System.currentTimeMillis()
            val rawOutput = withTimeoutOrNull(inferenceTimeoutMs) {
                runLiteRtLmInference(currentEngine, modelFile, prompt)
            }
            val inferMs = System.currentTimeMillis() - inferStart

            if (rawOutput != null) {
                Log.i(TAG, "LLM raw output (${inferMs}ms): $rawOutput")
                parseLlmJson(rawOutput)
            } else {
                Log.w(TAG, "LLM inference timed out or returned null after ${inferMs}ms")
                null
            }
        }
    }

    internal fun ensureEngineInitialized(modelFile: File): Engine {
        var current = engine
        if (current != null) return current
        val created = createAndInitEngine(modelFile)
        engine = created
        return created
    }

    private fun runLiteRtLmInference(
        currentEngine: Engine,
        modelFile: File,
        prompt: String
    ): String? {
        val conversationConfig = ConversationConfig(
            thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0)
        )

        // First inference attempt
        try {
            val conversation = currentEngine.createConversation(conversationConfig)
            val message = try {
                conversation.sendMessage(prompt)
            } finally {
                runCatching { conversation.close() }
            }
            val text = extractTextFromMessage(message)
            if (text.isNotBlank()) return text
        } catch (e: Throwable) {
            // NEVER destroy or close the engine on timeout or coroutine cancellation!
            if (e is CancellationException) throw e

            // Handle ColorOS 17 / Android 17 GPU context eviction: close stale engine, recreate once, and retry
            Log.w(TAG, "GPU inference failed with ${e.javaClass.simpleName}, attempting engine reinit: ${e.message}")
            runCatching { currentEngine.close() }
            engine = null

            try {
                val retryEngine = createAndInitEngine(modelFile)
                engine = retryEngine
                val retryConversation = retryEngine.createConversation(conversationConfig)
                val retryMessage = try {
                    retryConversation.sendMessage(prompt)
                } finally {
                    runCatching { retryConversation.close() }
                }
                val retryText = extractTextFromMessage(retryMessage)
                if (retryText.isNotBlank()) return retryText
            } catch (retryError: Throwable) {
                if (retryError is CancellationException) throw retryError
                Log.e(TAG, "Retry inference failed: ${retryError.message}")
                runCatching { engine?.close() }
                engine = null
                return null
            }
        }

        return null
    }

    private fun createAndInitEngine(modelFile: File): Engine {
        val cacheDir = context.cacheDir.absolutePath
        return try {
            val gpuConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                maxNumTokens = 1024,
                cacheDir = cacheDir
            )
            Engine(gpuConfig).also { it.initialize() }
        } catch (gpuError: Throwable) {
            if (gpuError is CancellationException) throw gpuError
            Log.w(TAG, "GPU engine init failed, falling back to CPU: ${gpuError.message}")
            val cpuConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = 1024,
                cacheDir = cacheDir
            )
            Engine(cpuConfig).also { it.initialize() }
        }
    }

    private fun extractTextFromMessage(message: Message): String {
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }
}
