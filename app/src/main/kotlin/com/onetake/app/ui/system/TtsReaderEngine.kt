package com.onetake.app.ui.system

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface RehearsalSpeechEngine {
    fun speak(text: String, onDone: () -> Unit = {})
    fun stop()
    fun isAvailable(): Boolean
}

/**
 * Holds the latest speech utterance requested while Android's [TextToSpeech] service is still
 * binding asynchronously, so Line 0 on screen launch is spoken once ready instead of dropped.
 */
internal class PendingSpeechQueue {
    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null

    @Synchronized
    fun enqueue(text: String, onDone: () -> Unit) {
        pendingText = text
        pendingOnDone = onDone
    }

    @Synchronized
    fun clear() {
        pendingText = null
        pendingOnDone = null
    }

    @Synchronized
    fun drainOnInitialized(): Pair<String, () -> Unit>? {
        val text = pendingText ?: return null
        val cb = pendingOnDone ?: {}
        pendingText = null
        pendingOnDone = null
        return text to cb
    }

    @Synchronized
    fun failAll() {
        val cb = pendingOnDone
        pendingText = null
        pendingOnDone = null
        cb?.invoke()
    }
}

class TtsReaderEngine(context: Context) : RehearsalSpeechEngine {
    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var initFailed = false

    /** 0–1 speech volume (Profile → Reader volume while shooting). */
    var volume: Float = 1f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingQueue = PendingSpeechQueue()

    /** The utterance whose completion [currentOnDone] belongs to. A late callback from an earlier
     *  (stopped or replaced) utterance must not fire the current line's callback. Main thread only. */
    private var currentUtteranceId: String? = null
    private var currentOnDone: (() -> Unit)? = null

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    if (engine != null) {
                        val langRes = engine.setLanguage(Locale.US)
                        if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                            runCatching { engine.setLanguage(Locale.getDefault()) }
                        }
                        engine.setPitch(0.95f)
                        engine.setSpeechRate(0.95f)
                        setupListener(engine)
                        isInitialized = true
                        mainHandler.post {
                            pendingQueue.drainOnInitialized()?.let { (queuedText, queuedDone) ->
                                speakNow(engine, queuedText, queuedDone)
                            }
                        }
                    } else {
                        initFailed = true
                        mainHandler.post { pendingQueue.failAll() }
                    }
                } else {
                    initFailed = true
                    mainHandler.post { pendingQueue.failAll() }
                }
            }
        }.onFailure {
            initFailed = true
            pendingQueue.failAll()
        }
    }

    private fun setupListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        })
    }

    /** TTS reports progress on its own binder thread; hop to main (callers touch Compose state and
     *  advance lines) and only complete the utterance that's still current. */
    private fun finished(utteranceId: String?) {
        mainHandler.post {
            if (utteranceId == null || utteranceId != currentUtteranceId) return@post
            val callback = currentOnDone
            currentOnDone = null
            currentUtteranceId = null
            callback?.invoke()
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        stop()
        val engine = tts
        when {
            engine != null && isInitialized -> speakNow(engine, text, onDone)
            engine != null && !initFailed -> pendingQueue.enqueue(text, onDone)
            else -> onDone()
        }
    }

    private fun speakNow(engine: TextToSpeech, text: String, onDone: () -> Unit) {
        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId
        currentOnDone = onDone
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
        }
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        if (result == TextToSpeech.ERROR) {
            currentUtteranceId = null
            currentOnDone = null
            onDone()
        }
    }

    override fun stop() {
        pendingQueue.clear()
        runCatching { tts?.stop() }
        currentUtteranceId = null
        currentOnDone = null
    }

    override fun isAvailable(): Boolean = isInitialized && !initFailed

    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        isInitialized = false
    }
}

/** Speaks [text] and suspends until it has actually finished (or [timeoutMs] passes, in case the
 *  engine never reports back) — so callers advance after the line, not on a guessed timer that
 *  cuts long lines off mid-sentence. */
suspend fun RehearsalSpeechEngine.speakAndAwait(text: String, timeoutMs: Long) {
    withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont -> speak(text) { if (cont.isActive) cont.resume(Unit) } }
    }
}

@Composable
fun rememberTtsEngine(): TtsReaderEngine {
    val context = LocalContext.current
    val engine = remember { TtsReaderEngine(context.applicationContext) }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    return engine
}
