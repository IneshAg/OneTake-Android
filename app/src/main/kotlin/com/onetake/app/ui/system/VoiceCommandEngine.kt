package com.onetake.app.ui.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

enum class VoiceCommand {
    Record,
    Cut,
    CapturePhoto,
    ReadyMark,
    Next,
    Again,
}

internal data class VoiceErrorDecision(
    val stopPermanently: Boolean,
    val recreateRecognizer: Boolean,
    val fallbackToStandardRecognizer: Boolean,
    val retryDelayMs: Long,
)

/**
 * Pure, deterministic recovery policy for Android [SpeechRecognizer] errors.
 *
 * Crucially handles Android 13+ (`TIRAMISU`) on-device speech recognizer failures
 * (`ERROR_SERVER_DISCONNECTED = 11`, `ERROR_LANGUAGE_NOT_SUPPORTED = 12`,
 * `ERROR_LANGUAGE_UNAVAILABLE = 13`, `ERROR_CANNOT_CHECK_SUPPORT = 14`, `15`)
 * by permanently falling back to the standard [SpeechRecognizer] without `EXTRA_PREFER_OFFLINE`.
 */
internal object VoiceErrorRecoveryPolicy {
    private const val ERROR_SERVER_DISCONNECTED = 11
    private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    private const val ERROR_CANNOT_CHECK_SUPPORT = 14
    private const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15

    fun decide(
        error: Int,
        usingOnDevice: Boolean,
        consecutiveErrors: Int,
    ): VoiceErrorDecision = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceErrorDecision(
            stopPermanently = true,
            recreateRecognizer = false,
            fallbackToStandardRecognizer = false,
            retryDelayMs = 0L,
        )

        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceErrorDecision(
            stopPermanently = false,
            recreateRecognizer = false,
            fallbackToStandardRecognizer = false,
            retryDelayMs = 25L,
        )

        ERROR_LANGUAGE_NOT_SUPPORTED,
        ERROR_LANGUAGE_UNAVAILABLE,
        ERROR_CANNOT_CHECK_SUPPORT,
        ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> VoiceErrorDecision(
            stopPermanently = false,
            recreateRecognizer = true,
            fallbackToStandardRecognizer = true,
            retryDelayMs = 40L,
        )

        ERROR_SERVER_DISCONNECTED -> VoiceErrorDecision(
            stopPermanently = false,
            recreateRecognizer = true,
            fallbackToStandardRecognizer = usingOnDevice,
            retryDelayMs = 80L,
        )

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceErrorDecision(
            stopPermanently = false,
            recreateRecognizer = consecutiveErrors >= 3,
            fallbackToStandardRecognizer = false,
            retryDelayMs = 200L,
        )

        SpeechRecognizer.ERROR_CLIENT -> VoiceErrorDecision(
            stopPermanently = false,
            recreateRecognizer = true,
            fallbackToStandardRecognizer = usingOnDevice && consecutiveErrors >= 2,
            retryDelayMs = (120L * consecutiveErrors.coerceAtLeast(1)).coerceAtMost(800L),
        )

        SpeechRecognizer.ERROR_AUDIO,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceErrorDecision(
            stopPermanently = false,
            recreateRecognizer = consecutiveErrors >= 2,
            fallbackToStandardRecognizer = usingOnDevice && error == SpeechRecognizer.ERROR_SERVER,
            retryDelayMs = (100L * consecutiveErrors.coerceAtLeast(1)).coerceAtMost(750L),
        )

        else -> VoiceErrorDecision(
            stopPermanently = false,
            recreateRecognizer = consecutiveErrors >= 2,
            fallbackToStandardRecognizer = usingOnDevice,
            retryDelayMs = 100L,
        )
    }
}

/**
 * Noise-resilient, dialogue-safe voice command matcher.
 *
 * Strips ASR punctuation (`"Okay, action!"`, `"Cut?"`), supports far-field phonetic variants,
 * tolerates crowded-room ambient words before/after cued commands, and strictly blocks
 * grammatical dialogue patterns (`"I had to cut"`, `"Don't cut me off"`, `"take action"`).
 */
internal object VoiceCommandMatcher {

    private val NON_WORD_REGEX = Regex("""[^a-z0-9']+""")
    private val EDGE_APOSTROPHE_REGEX = Regex("""^'+|'+$""")

    private val NEGATION_LEFT = setOf(
        "don't", "dont", "didn't", "didnt", "won't", "wont", "can't", "cant", "cannot",
        "not", "never", "no", "to",
    )

    private val RECORD_DISQUALIFYING_LEFT = NEGATION_LEFT + setOf(
        "take", "taking", "took", "in", "into", "of", "the", "a", "an",
        "my", "your", "our", "his", "her", "their", "this", "that",
        "class", "legal", "military", "price", "live", "world", "track", "new",
        "criminal", "medical", "public", "on", "off", "for",
    )

    private val RECORD_DISQUALIFYING_RIGHT = setOf(
        "movie", "movies", "film", "films", "plan", "plans", "hero", "packed",
        "tomorrow", "figure", "figures", "label", "store", "player", "deal", "breaker",
    )

    private val CUT_DISQUALIFYING_LEFT = NEGATION_LEFT + setOf(
        "will", "would", "should", "could", "must", "might", "may",
        "had", "have", "has", "having", "was", "were", "got", "get", "getting",
        "a", "an", "the", "my", "your", "our", "his", "her", "their", "this", "that",
        "severe", "budget", "deep", "clean", "clear", "short", "final", "rough",
        "director's", "directors", "tax", "pay", "power", "price", "rate", "hair",
        "paper", "fresh", "open", "bubble", "gift", "plastic", "shrink",
    )

    private val CUT_DISQUALIFYING_RIGHT = setOf(
        "me", "him", "her", "us", "them", "you",
        "my", "your", "our", "his", "their",
        "the", "a", "an", "this", "that", "these", "those", "some", "any",
        "off", "out", "into", "through", "down", "back", "up", "away", "loose", "short", "corners",
        "to",
    )

    private val PHOTO_DISQUALIFYING_LEFT = NEGATION_LEFT + setOf(
        "from", "until", "till", "by", "for", "before", "just",
    )

    private val PHOTO_DISQUALIFYING_RIGHT = setOf(
        "on", "that", "and", "we", "you", "i", "they", "he", "she",
    )

    private val RECORD_CUED_PHRASES: List<List<String>> = listOf(
        listOf("and", "action"),
        listOf("okay", "action"),
        listOf("ok", "action"),
        listOf("alright", "action"),
        listOf("ready", "action"),
        listOf("action", "now"),
        listOf("action", "please"),
        listOf("action", "camera"),
        listOf("and", "record"),
        listOf("okay", "record"),
        listOf("ok", "record"),
        listOf("alright", "record"),
        listOf("let's", "record"),
        listOf("lets", "record"),
        listOf("let's", "record", "now"),
        listOf("lets", "record", "now"),
        listOf("record", "now"),
        listOf("record", "please"),
        listOf("start", "recording"),
        listOf("roll", "camera"),
        listOf("camera", "roll"),
        listOf("camera", "rolling"),
        listOf("camera", "speed"),
        listOf("mark", "slate"),
    )

    private val CUT_CUED_PHRASES: List<List<String>> = listOf(
        listOf("and", "cut"),
        listOf("okay", "cut"),
        listOf("ok", "cut"),
        listOf("alright", "cut"),
        listOf("yeah", "cut"),
        listOf("please", "cut"),
        listOf("scene", "cut"),
        listOf("cut", "cut"),
        listOf("cut", "it"),
        listOf("cut", "now"),
        listOf("cut", "please"),
        listOf("cut", "take"),
        listOf("cut", "camera"),
        listOf("cut", "there"),
        listOf("cut", "that"),
        listOf("stop", "recording"),
        listOf("please", "stop", "recording"),
        listOf("okay", "stop", "recording"),
        listOf("stop", "take"),
        listOf("stop", "camera"),
        listOf("that's", "a", "wrap"),
        listOf("thats", "a", "wrap"),
        listOf("wrap", "it"),
        listOf("wrap", "it", "up"),
        listOf("and", "scene"),
        listOf("end", "scene"),
        listOf("all", "done"),
    )

    private val PHOTO_CUED_PHRASES: List<List<String>> = listOf(
        listOf("say", "now"),
        listOf("right", "now"),
        listOf("okay", "now"),
        listOf("ok", "now"),
        listOf("and", "now"),
        listOf("ready", "now"),
        listOf("click", "now"),
        listOf("snap", "now"),
        listOf("shoot", "now"),
        listOf("capture", "now"),
        listOf("shoot", "it"),
        listOf("take", "it"),
        listOf("take", "photo"),
        listOf("take", "a", "photo"),
        listOf("take", "picture"),
        listOf("take", "a", "picture"),
        listOf("say", "cheese"),
    )

    private val READY_MARK_CUED_PHRASES: List<List<String>> = listOf(
        listOf("go", "to", "your", "mark"),
        listOf("go", "to", "my", "mark"),
        listOf("on", "my", "mark"),
        listOf("on", "your", "mark"),
        listOf("at", "my", "mark"),
        listOf("camera", "ready"),
        listOf("i'm", "ready"),
        listOf("im", "ready"),
        listOf("start", "slate"),
    )

    private val NEXT_CUED_PHRASES: List<List<String>> = listOf(
        listOf("next", "scene"),
        listOf("next", "stage"),
        listOf("next", "clip"),
        listOf("move", "on"),
        listOf("keep", "it"),
        listOf("keep", "that"),
    )

    private val AGAIN_CUED_PHRASES: List<List<String>> = listOf(
        listOf("go", "again"),
        listOf("one", "more"),
        listOf("one", "more", "take"),
        listOf("another", "take"),
        listOf("try", "again"),
        listOf("shoot", "again"),
        listOf("record", "again"),
    )

    fun normalizeTokens(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val normalized = raw
            .lowercase(Locale.US)
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('`', '\'')
        return normalized
            .split(NON_WORD_REGEX)
            .map { it.replace(EDGE_APOSTROPHE_REGEX, "") }
            .filter { it.isNotEmpty() }
            .map { canonicalHomophone(it) }
    }

    private fun canonicalHomophone(token: String): String = when (token) {
        "axion", "akshun" -> "action"
        "snapped" -> "snap"
        else -> token
    }

    fun match(raw: String, activeCommands: Set<VoiceCommand>): VoiceCommand? {
        if (activeCommands.isEmpty()) return null
        val tokens = normalizeTokens(raw)
        if (tokens.isEmpty()) return null
        return matchTokens(tokens, activeCommands)
    }

    fun matchTokens(
        tokens: List<String>,
        activeCommands: Set<VoiceCommand>,
        minMatchEndIndex: Int = 0,
    ): VoiceCommand? {
        if (tokens.isEmpty() || activeCommands.isEmpty()) return null
        if (minMatchEndIndex > tokens.lastIndex) return null

        // 1. Check multi-word cued phrases across the window (resistant to trailing ambient chatter in noisy rooms)
        for (command in activeCommands) {
            val phrases = cuedPhrasesFor(command)
            if (containsValidPhrase(tokens, command, phrases, minMatchEndIndex)) {
                return command
            }
        }

        // 2. Check terminal bare command at the end of the utterance / partial delta
        val lastIdx = tokens.lastIndex
        val last = tokens[lastIdx]
        val prev = tokens.getOrNull(lastIdx - 1)

        for (command in activeCommands) {
            if (matchesTerminalBareToken(command, last, prev, tokens.size - minMatchEndIndex)) {
                return command
            }
        }
        return null
    }

    private fun cuedPhrasesFor(command: VoiceCommand): List<List<String>> = when (command) {
        VoiceCommand.Record -> RECORD_CUED_PHRASES
        VoiceCommand.Cut -> CUT_CUED_PHRASES
        VoiceCommand.CapturePhoto -> PHOTO_CUED_PHRASES
        VoiceCommand.ReadyMark -> READY_MARK_CUED_PHRASES
        VoiceCommand.Next -> NEXT_CUED_PHRASES
        VoiceCommand.Again -> AGAIN_CUED_PHRASES
    }

    private fun containsValidPhrase(
        tokens: List<String>,
        command: VoiceCommand,
        phrases: List<List<String>>,
        minMatchEndIndex: Int,
    ): Boolean {
        for (phrase in phrases) {
            val len = phrase.size
            if (tokens.size < len) continue
            for (start in 0..(tokens.size - len)) {
                val endIdx = start + len - 1
                if (endIdx < minMatchEndIndex) continue
                var allMatch = true
                for (k in 0 until len) {
                    if (tokens[start + k] != phrase[k]) {
                        allMatch = false
                        break
                    }
                }
                if (!allMatch) continue

                val left = tokens.getOrNull(start - 1)
                val right = tokens.getOrNull(start + len)
                if (left != null && left in NEGATION_LEFT) continue

                val blockedByRight = when (command) {
                    VoiceCommand.Cut -> right != null && right in CUT_DISQUALIFYING_RIGHT
                    VoiceCommand.Record -> right != null && right in RECORD_DISQUALIFYING_RIGHT
                    VoiceCommand.CapturePhoto -> right != null && right in PHOTO_DISQUALIFYING_RIGHT
                    else -> false
                }
                if (!blockedByRight) return true
            }
        }
        return false
    }

    private fun matchesTerminalBareToken(
        command: VoiceCommand,
        last: String,
        prev: String?,
        totalTokens: Int,
    ): Boolean = when (command) {
        VoiceCommand.Record -> when (last) {
            "action", "record", "rolling", "speed" -> prev == null || prev !in RECORD_DISQUALIFYING_LEFT
            "start", "slate" -> totalTokens == 1
            else -> false
        }

        VoiceCommand.Cut -> when (last) {
            "cut", "wrap" -> prev == null || prev !in CUT_DISQUALIFYING_LEFT
            "stop", "finished" -> totalTokens == 1
            else -> false
        }

        VoiceCommand.CapturePhoto -> when (last) {
            "now", "snap", "capture", "cheese", "click", "shoot" -> prev == null || prev !in PHOTO_DISQUALIFYING_LEFT
            "take", "photo", "picture", "smile", "ready" -> totalTokens <= 2 && (prev == null || prev !in PHOTO_DISQUALIFYING_LEFT)
            else -> false
        }

        VoiceCommand.ReadyMark -> when (last) {
            "ready", "mark" -> prev == null || prev !in NEGATION_LEFT
            else -> false
        }

        VoiceCommand.Next -> when (last) {
            "next", "continue" -> prev == null || prev !in (NEGATION_LEFT + setOf("what's", "whats", "up"))
            else -> false
        }

        VoiceCommand.Again -> when (last) {
            "again", "redo", "retake" -> prev == null || prev !in NEGATION_LEFT
            else -> false
        }
    }
}

/**
 * Tracks incremental token growth within a continuous `SpeechRecognizer` session.
 *
 * In a crowded or noisy environment, Android's VAD stays open indefinitely and only delivers
 * `onPartialResults` with an ever-growing transcript. [VoiceSessionTracker] isolates tokens
 * spoken *after* the latest mode transition so pre-switch words never re-trigger, while retaining
 * 2 tokens of left grammar context so mid-monologue partials like `"I had to cut"` remain blocked.
 */
internal class VoiceSessionTracker {
    private var ignoredPrefixTokenCount: Int = 0
    private var lastObservedPrimaryTokens: List<String> = emptyList()

    fun resetSession() {
        ignoredPrefixTokenCount = 0
        lastObservedPrimaryTokens = emptyList()
    }

    fun resetForModeChange(currentHypotheses: List<String> = emptyList()) {
        val currentPrimary = currentHypotheses.firstOrNull()?.let { VoiceCommandMatcher.normalizeTokens(it) }
            ?: lastObservedPrimaryTokens
        ignoredPrefixTokenCount = currentPrimary.size
        lastObservedPrimaryTokens = currentPrimary
    }

    fun evaluateHypotheses(
        hypotheses: List<String>,
        activeCommands: Set<VoiceCommand>,
    ): VoiceCommand? {
        if (hypotheses.isEmpty() || activeCommands.isEmpty()) return null

        val primaryTokens = VoiceCommandMatcher.normalizeTokens(hypotheses.first())
        // If Android started a fresh utterance buffer (shorter than our ignored prefix), reset prefix
        if (primaryTokens.size < ignoredPrefixTokenCount) {
            ignoredPrefixTokenCount = 0
        }
        lastObservedPrimaryTokens = primaryTokens

        for ((index, raw) in hypotheses.withIndex()) {
            val tokens = if (index == 0) primaryTokens else VoiceCommandMatcher.normalizeTokens(raw)
            if (tokens.size <= ignoredPrefixTokenCount) continue

            // Include up to 2 tokens before the unconsumed delta ONLY as grammatical left-context
            // when checking if a bare command at the end is part of a sentence ("i had to cut").
            // First check strictly within unconsumed tokens, but pass left neighbor from full tokens!
            val unconsumedStart = ignoredPrefixTokenCount
            val contextStart = (unconsumedStart - 2).coerceAtLeast(0)
            val sliceWithContext = tokens.subList(contextStart, tokens.size)
            val contextOffset = unconsumedStart - contextStart

            val matched = matchUnconsumedSlice(sliceWithContext, contextOffset, activeCommands)
            if (matched != null) {
                ignoredPrefixTokenCount = tokens.size
                return matched
            }
        }
        return null
    }

    private fun matchUnconsumedSlice(
        slice: List<String>,
        unconsumedOffset: Int,
        activeCommands: Set<VoiceCommand>,
    ): VoiceCommand? {
        if (unconsumedOffset >= slice.size) return null
        return VoiceCommandMatcher.matchTokens(
            tokens = slice,
            activeCommands = activeCommands,
            minMatchEndIndex = unconsumedOffset,
        )
    }
}

/**
 * Unified, production-grade hands-free voice command engine for Android camera & rehearsal flows.
 */
class VoiceCommandEngine(
    context: Context,
    initialCommands: Set<VoiceCommand> = emptySet(),
    private val onCommand: (VoiceCommand, String) -> Unit,
    private val onRmsChanged: ((Float) -> Unit)? = null,
) {
    private val appContext = context.applicationContext
    val available: Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

    var isListening by mutableStateOf(false)
        private set
    var lastHeard by mutableStateOf<String?>(null)
        private set

    private var activeCommands: Set<VoiceCommand> = initialCommands
    private var recognizer: SpeechRecognizer? = null
    private var usingOnDeviceRecognizer = false
    private var wanted = false
    private var startInFlight = false
    private var consecutiveErrors = 0
    private var lastTriggerElapsedMs = 0L

    private val tracker = VoiceSessionTracker()
    private val handler = Handler(Looper.getMainLooper())

    private val relistenRunnable = Runnable {
        if (wanted) listenInternal()
    }

    // Watchdog in case an OEM SpeechService swallows startListening() without firing onReadyForSpeech or onError
    private val startupWatchdogRunnable = Runnable {
        if (wanted && startInFlight && !isListening) {
            startInFlight = false
            recreateRecognizer()
            handler.postDelayed(relistenRunnable, 80L)
        }
    }

    companion object {
        @Volatile
        private var globalForceStandardRecognizer: Boolean = false
        private const val TRIGGER_COOLDOWN_MS = 1100L
        private const val STARTUP_WATCHDOG_MS = 4500L
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            handler.removeCallbacks(startupWatchdogRunnable)
            startInFlight = false
            isListening = true
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {
            handler.removeCallbacks(startupWatchdogRunnable)
            startInFlight = false
            isListening = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged?.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            // Keep waiting for onResults / onError before restarting
        }

        override fun onError(error: Int) {
            handler.removeCallbacks(startupWatchdogRunnable)
            handler.removeCallbacks(relistenRunnable)
            startInFlight = false
            isListening = false
            tracker.resetSession()

            val isSilenceBoundary = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (isSilenceBoundary) {
                consecutiveErrors = 0
            } else {
                consecutiveErrors += 1
            }

            val decision = VoiceErrorRecoveryPolicy.decide(
                error = error,
                usingOnDevice = usingOnDeviceRecognizer,
                consecutiveErrors = consecutiveErrors,
            )

            if (decision.fallbackToStandardRecognizer) {
                globalForceStandardRecognizer = true
            }
            if (decision.stopPermanently || !wanted) {
                wanted = false
                return
            }
            if (decision.recreateRecognizer) {
                recreateRecognizer()
            }
            // Note: never call recognizer?.cancel() inside onError() — onError is already terminal
            // in Android's SpeechRecognizer state machine, and cancel() races with the next session.
            handler.postDelayed(relistenRunnable, decision.retryDelayMs)
        }

        override fun onResults(results: Bundle?) {
            handler.removeCallbacks(startupWatchdogRunnable)
            handler.removeCallbacks(relistenRunnable)
            startInFlight = false
            isListening = false
            val triggered = evaluateBundle(results)
            tracker.resetSession()
            if (wanted) {
                handler.postDelayed(relistenRunnable, if (triggered) 180L else 25L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            evaluateBundle(partialResults)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun evaluateBundle(bundle: Bundle?): Boolean {
        if (!wanted || activeCommands.isEmpty()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerElapsedMs < TRIGGER_COOLDOWN_MS) return false

        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            .filter { it.isNotBlank() }
        if (matches.isEmpty()) return false

        val command = tracker.evaluateHypotheses(matches, activeCommands) ?: return false
        lastTriggerElapsedMs = now
        val display = matches.first().trim()
        lastHeard = "Heard: \"$display\""
        onCommand(command, display)
        return true
    }

    fun setActiveCommands(commands: Set<VoiceCommand>) {
        if (activeCommands != commands) {
            activeCommands = commands
            lastHeard = null
            tracker.resetForModeChange()
        }
    }

    fun start() {
        if (!available) return
        if (wanted && (isListening || startInFlight)) return
        wanted = true
        consecutiveErrors = 0
        tracker.resetSession()
        handler.removeCallbacks(relistenRunnable)
        listenInternal()
    }

    fun stop() {
        wanted = false
        startInFlight = false
        isListening = false
        tracker.resetSession()
        handler.removeCallbacks(startupWatchdogRunnable)
        handler.removeCallbacks(relistenRunnable)
        runCatching { recognizer?.cancel() }
    }

    fun destroy() {
        stop()
        recreateRecognizer()
    }

    private fun recreateRecognizer() {
        val old = recognizer
        recognizer = null
        usingOnDeviceRecognizer = false
        if (old != null) {
            runCatching { old.destroy() }
        }
    }

    private fun listenInternal() {
        if (!wanted || !available) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            isListening = false
            return
        }

        val rec = recognizer ?: createBestRecognizer()?.also {
            it.setRecognitionListener(listener)
            recognizer = it
        } ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            if (usingOnDeviceRecognizer && !globalForceStandardRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            putExtra("android.speech.extra.DICTATION_MODE", true)
            // Keep session open across quiet-room pauses (eliminates 50% restart deafness window)
            // while onPartialResults triggers commands immediately with ~150ms latency.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
        }

        startInFlight = true
        handler.removeCallbacks(startupWatchdogRunnable)
        handler.postDelayed(startupWatchdogRunnable, STARTUP_WATCHDOG_MS)

        runCatching {
            rec.startListening(intent)
        }.onFailure {
            handler.removeCallbacks(startupWatchdogRunnable)
            startInFlight = false
            isListening = false
            recreateRecognizer()
            if (wanted) {
                handler.postDelayed(relistenRunnable, 200L)
            }
        }
    }

    private fun createBestRecognizer(): SpeechRecognizer? {
        val canTryOnDevice = !globalForceStandardRecognizer &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)

        if (canTryOnDevice) {
            val onDevice = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) }.getOrNull()
            if (onDevice != null) {
                usingOnDeviceRecognizer = true
                return onDevice
            }
            globalForceStandardRecognizer = true
        }

        usingOnDeviceRecognizer = false
        return runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
    }
}

@Composable
fun rememberVoiceCommandEngine(
    activeCommands: Set<VoiceCommand>,
    enabled: Boolean,
    onCommand: (VoiceCommand, String) -> Unit,
): VoiceCommandEngine {
    val context = LocalContext.current
    val latestOnCommand by rememberUpdatedState(onCommand)
    val engine = remember {
        VoiceCommandEngine(
            context = context.applicationContext,
            initialCommands = activeCommands,
            onCommand = { cmd, heard -> latestOnCommand(cmd, heard) },
        )
    }

    LaunchedEffect(activeCommands) {
        engine.setActiveCommands(activeCommands)
    }

    LaunchedEffect(enabled, activeCommands) {
        if (enabled && activeCommands.isNotEmpty()) {
            engine.start()
        } else {
            engine.stop()
        }
    }

    DisposableEffect(engine) {
        onDispose { engine.destroy() }
    }

    return engine
}
