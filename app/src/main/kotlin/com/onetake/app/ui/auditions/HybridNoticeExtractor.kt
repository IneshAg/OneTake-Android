package com.onetake.app.ui.auditions

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
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
        const val LLM_INFERENCE_TIMEOUT_MS = 3000L
        const val MAX_INPUT_CHAR_SLICE = 4000

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
You are an expert casting assistant. Analyze the following audition notice and sides text.
Output ONLY a JSON object matching this schema:
{
  "documentKind": "CastingNotice" | "SidesOnly" | "NoticeWithSides",
  "projectTitle": "Clean project or production title",
  "projectType": "Feature film" | "TV Series" | "Commercial" | "Short film" | "Theatre" | etc.,
  "targetRoleName": "Character role name targeted for the audition",
  "allAvailableRoles": ["Role 1", "Role 2"],
  "roleSize": "Lead" | "Supporting" | "Guest Star" | "Co-Star" | "Series Regular" | etc.,
  "tapeDueIsoDate": "YYYY-MM-DD",
  "tapeDueTime24h": "HH:MM",
  "destination": "Actors Access" | "Eco-Cast" | "Casting Networks" | "Email" | etc.,
  "asks": ["Slate first", "Landscape", "Full length", "Both profiles", "Under 3 min"],
  "characterNormalizationMap": {"OCR_TYPO": "CORRECT_NAME"},
  "firstScriptLineAnchor": "Verbatim line where screenplay sides begin"
}

Important Instructions:
1. Today's date is $today. If only day and month are provided, use $today to calculate the ISO year.
2. If multiple roles are present, list all distinct roles in "allAvailableRoles".
3. If deliverables (asks) like slate, landscape framing, full-length shot, or time limits are requested in the notice, include them in "asks". Do NOT include directions that appear inside screenplay dialogue or action lines.
4. Output ONLY valid JSON enclosed within { and }.

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

    internal var testInferenceOverride: ((String) -> String?)? = null

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
        } catch (e: Throwable) {
            downloadManager.enqueueBackgroundDownload(context)
        }
    }

    suspend fun extractNotice(
        rawText: String,
        today: LocalDate = LocalDate.now()
    ): LlmParsedNotice? = withTimeoutOrNull(LLM_INFERENCE_TIMEOUT_MS) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                extractNoticeInternal(rawText, today)
            }
        }
    }

    private suspend fun extractNoticeInternal(
        rawText: String,
        today: LocalDate
    ): LlmParsedNotice? {
        val inputSlice = rawText.take(MAX_INPUT_CHAR_SLICE)
        val prompt = buildPrompt(inputSlice, today)

        testInferenceOverride?.let { override ->
            val result = override(prompt)
            return result?.let { parseLlmJson(it) }
        }

        // Step 1: System AICore (Tier 1)
        try {
            val client = Generation.getClient()
            val status = client.checkStatus()
            if (status == FeatureStatus.AVAILABLE) {
                val response = client.generateContent(prompt)
                val text = response.candidates.firstOrNull()?.text
                if (!text.isNullOrBlank()) {
                    val parsed = parseLlmJson(text)
                    if (parsed != null) {
                        return parsed
                    }
                }
            }
        } catch (e: Throwable) {
            // System AICore unavailable or failed, fall through to Tier 2
        }

        // Step 2: LiteRT-LM GPU -> CPU Cascade (Tier 2)
        val downloadManager = ModelDownloadManager.getInstance(context)
        val modelFile = downloadManager.ensureModelReadyOrStartDownload()
        if (modelFile == null || !modelFile.exists()) {
            return null
        }

        val rawOutput = runLiteRtLmInference(modelFile, prompt) ?: return null
        return parseLlmJson(rawOutput)
    }

    private fun runLiteRtLmInference(modelFile: File, prompt: String): String? {
        if (engine == null) {
            engine = createAndInitEngine(modelFile)
        }
        val currentEngine = engine ?: return null

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
            // Handle ColorOS 17 / Android 17 GPU context eviction: close stale engine, recreate once, and retry
            runCatching { engine?.close() }
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
                cacheDir = cacheDir
            )
            Engine(gpuConfig).also { it.initialize() }
        } catch (gpuError: Throwable) {
            val cpuConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
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
