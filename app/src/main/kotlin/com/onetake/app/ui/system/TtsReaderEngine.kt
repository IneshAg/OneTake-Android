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

interface RehearsalSpeechEngine {
    fun speak(text: String, onDone: () -> Unit = {})
    fun stop()
    fun isAvailable(): Boolean
}

class TtsReaderEngine(context: Context) : RehearsalSpeechEngine {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The utterance whose completion [currentOnDone] belongs to. A late callback from an earlier
     *  (stopped or replaced) utterance must not fire the current line's callback. Main thread only. */
    private var currentUtteranceId: String? = null
    private var currentOnDone: (() -> Unit)? = null

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setPitch(0.95f)
                    tts?.setSpeechRate(0.95f)
                    isInitialized = true
                    setupListener()
                }
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
        if (engine != null && isInitialized) {
            val utteranceId = UUID.randomUUID().toString()
            currentUtteranceId = utteranceId
            currentOnDone = onDone
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        } else {
            // TTS not available or not ready
            onDone()
        }
    }

    override fun stop() {
        tts?.stop()
        currentUtteranceId = null
        currentOnDone = null
    }

    override fun isAvailable(): Boolean = isInitialized

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
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
