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
    @Volatile
    private var isGpuAvailable: Boolean = true

    internal var inferenceTimeoutMs: Long = LLM_INFERENCE_TIMEOUT_MS
    internal var testInferenceOverride: ((String) -> String?)? = null

    internal fun getEngineForTesting(): Engine? = engine
    internal fun isGpuAvailableForTesting(): Boolean = isGpuAvailable
    internal fun setGpuAvailableForTesting(available: Boolean) {
        isGpuAvailable = available
    }

    fun warmUp(scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        scope.launch {
            performWarmUp()
        }
    }

    suspend fun performWarmUp() = withContext(Dispatchers.IO) {
        val downloadManager = ModelDownloadManager.getInstance(context)
        Log.i(TAG, "performWarmUp started")
        try {
            val client = Generation.getClient()
            val status = client.checkStatus()
            Log.i(TAG, "Tier 1 System AICore status: $status")
            when (status) {
                FeatureStatus.AVAILABLE -> {
                    downloadManager.setUsingSystemAiCore()
                    runCatching { client.warmup() }
                    return@withContext
                }
                FeatureStatus.DOWNLOADABLE -> {
                    client.download().collect { /* downloading in background */ }
                    return@withContext
                }
                FeatureStatus.DOWNLOADING -> {
                    // System download in progress
                    return@withContext
                }
                else -> {
                    // Fall through to Tier 2
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.i(TAG, "Tier 1 AICore check threw ${e.javaClass.simpleName}: ${e.message}")
        }

        // Tier 2: Check if model file already exists on disk
        val existingModel = downloadManager.checkExistingModel()
        if (existingModel != null && existingModel.exists()) {
            Log.i(TAG, "Tier 2 model found on disk at ${existingModel.absolutePath}. Pre-initializing engine in background...")
            runCatching {
                ensureEngineInitialized(existingModel)
                Log.i(TAG, "Tier 2 engine warmed up successfully in background!")
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Tier 2 engine background warmup encountered error: ${err.message}", err)
            }
        } else {
            Log.i(TAG, "Tier 2 model not on disk. Enqueueing background download.")
            downloadManager.enqueueBackgroundDownload(context)
        }
    }

    suspend fun extractNotice(
        rawText: String,
        today: LocalDate = LocalDate.now()
    ): LlmParsedNotice? = withContext(Dispatchers.IO) {
        val inputSlice = rawText.take(MAX_INPUT_CHAR_SLICE)
        val prompt = buildPrompt(inputSlice, today)
        Log.i(TAG, "extractNotice started. Input slice: ${inputSlice.length} chars (rawText=${rawText.length} chars)")

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
                Log.i(TAG, "Tier 1 System AICore checkStatus: $status")
                if (status == FeatureStatus.AVAILABLE) {
                    val response = withTimeoutOrNull(inferenceTimeoutMs) {
                        client.generateContent(prompt)
                    }
                    val text = response?.candidates?.firstOrNull()?.text
                    Log.i(TAG, "Tier 1 AICore raw response: $text")
                    if (!text.isNullOrBlank()) {
                        val parsed = parseLlmJson(text)
                        if (parsed != null) {
                            Log.i(TAG, "Tier 1 AICore parsed JSON successfully!")
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.i(TAG, "Tier 1 AICore skipped/failed: ${e.message}. Proceeding to Tier 2.")
            }

            // Step 2: LiteRT-LM GPU -> CPU Cascade (Tier 2)
            val downloadManager = ModelDownloadManager.getInstance(context)
            val modelFile = downloadManager.ensureModelReadyOrStartDownload()
            if (modelFile == null || !modelFile.exists()) {
                Log.w(TAG, "Tier 2 LiteRT-LM model not ready on disk (status=${downloadManager.status.value}). Falling back to Tier 3 baseline.")
                return@withContext null
            }
            Log.i(TAG, "Tier 2 model ready: ${modelFile.name} (${modelFile.length()} bytes) at ${modelFile.absolutePath}")

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
            Log.i(TAG, "Engine ready in ${initMs}ms")

            // Wrapped strictly around inference execution
            val inferStart = System.currentTimeMillis()
            Log.i(TAG, "Starting LiteRT-LM inference (timeout=${inferenceTimeoutMs}ms)...")
            val rawOutput = withTimeoutOrNull(inferenceTimeoutMs) {
                runLiteRtLmInference(currentEngine, modelFile, prompt)
            }
            val inferMs = System.currentTimeMillis() - inferStart

            if (rawOutput != null) {
                Log.i(TAG, "LLM raw output (${inferMs}ms): $rawOutput")
                val parsed = parseLlmJson(rawOutput)
                if (parsed != null) {
                    Log.i(TAG, "LiteRT-LM parsed JSON successfully! (project='${parsed.projectTitle}', role='${parsed.targetRoleName}', roles=${parsed.allAvailableRoles.size})")
                    parsed
                } else {
                    Log.w(TAG, "Failed to parse JSON from LLM output: $rawOutput")
                    null
                }
            } else {
                Log.w(TAG, "LLM inference timed out or returned null after ${inferMs}ms")
                null
            }
        }
    }

    internal fun ensureEngineInitialized(modelFile: File): Engine {
        var current = engine
        if (current != null) return current
        val created = createAndInitEngine(modelFile, forceCpu = !isGpuAvailable)
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

            // Handle ColorOS 17 / Android 17 GPU context eviction or missing OpenCL: close stale engine, recreate once, and retry
            Log.w(TAG, "Inference failed with ${e.javaClass.simpleName}: ${e.message}")
            runCatching { currentEngine.close() }
            engine = null

            val isOpenClOrGpuError = e.message?.contains("OpenCL", ignoreCase = true) == true ||
                e.message?.contains("GPU", ignoreCase = true) == true ||
                e.message?.contains("clGetPlatformIDs", ignoreCase = true) == true ||
                e.message?.contains("clCreateContext", ignoreCase = true) == true

            if (isOpenClOrGpuError) {
                Log.w(TAG, "OpenCL/GPU error during nativeSendMessage ('${e.message}'). Disabling GPU and falling back to CPU engine.")
                isGpuAvailable = false
            }

            try {
                val retryEngine = createAndInitEngine(modelFile, forceCpu = isOpenClOrGpuError || !isGpuAvailable)
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
                if (isGpuAvailable && (retryError.message?.contains("OpenCL", ignoreCase = true) == true || retryError.message?.contains("GPU", ignoreCase = true) == true)) {
                    Log.w(TAG, "Retry on GPU failed with OpenCL/GPU error ('${retryError.message}'). Attempting final CPU fallback.")
                    isGpuAvailable = false
                    runCatching { engine?.close() }
                    engine = null
                    return runCatching {
                        val cpuEngine = createAndInitEngine(modelFile, forceCpu = true)
                        engine = cpuEngine
                        val cpuConv = cpuEngine.createConversation(conversationConfig)
                        try {
                            val cpuMsg = cpuConv.sendMessage(prompt)
                            extractTextFromMessage(cpuMsg)
                        } finally {
                            runCatching { cpuConv.close() }
                        }
                    }.getOrNull()
                }
                Log.e(TAG, "Retry inference failed: ${retryError.message}")
                runCatching { engine?.close() }
                engine = null
                return null
            }
        }

        return null
    }

    private fun createAndInitEngine(modelFile: File, forceCpu: Boolean = false): Engine {
        val cacheDir = context.cacheDir.absolutePath
        if (isGpuAvailable && !forceCpu) {
            try {
                val gpuConfig = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    maxNumTokens = 1024,
                    cacheDir = cacheDir
                )
                return Engine(gpuConfig).also {
                    it.initialize()
                    Log.i(TAG, "Initialized LiteRT-LM GPU engine for ${modelFile.name}")
                }
            } catch (gpuError: Throwable) {
                if (gpuError is CancellationException) throw gpuError
                Log.w(TAG, "GPU engine init failed, falling back to CPU: ${gpuError.message}")
                isGpuAvailable = false
            }
        }

        val cpuConfig = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.CPU(),
            maxNumTokens = 1024,
            cacheDir = cacheDir
        )
        return Engine(cpuConfig).also {
            it.initialize()
            Log.i(TAG, "Initialized LiteRT-LM CPU engine for ${modelFile.name}")
        }
    }

    private fun extractTextFromMessage(message: Message): String {
        val contentsText = message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
        if (contentsText.isNotBlank()) return contentsText

        val channelsText = message.channels.values.joinToString("\n")
        if (channelsText.isNotBlank()) return channelsText

        return ""
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
        isGpuAvailable = true
    }
}
