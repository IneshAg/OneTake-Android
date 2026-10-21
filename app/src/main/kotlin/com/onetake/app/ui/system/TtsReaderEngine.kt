package com.onetake.app.ui.system

import android.content.Context
import android.os.Bundle
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

            override fun onDone(utteranceId: String?) {
                val callback = currentOnDone
                currentOnDone = null
                callback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val callback = currentOnDone
                currentOnDone = null
                callback?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val callback = currentOnDone
                currentOnDone = null
                callback?.invoke()
            }
        })
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        stop()
        currentOnDone = onDone
        val engine = tts
        if (engine != null && isInitialized) {
            val utteranceId = UUID.randomUUID().toString()
            val params = Bundle()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            // TTS not available or not ready, invoke callback
            val callback = currentOnDone
            currentOnDone = null
            callback?.invoke()
        }
    }

    override fun stop() {
        tts?.stop()
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
