package com.onetake.app.ui.system

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.io.File

// Android twins of the iOS UIKit bridges (UIKitBridges.swift).

/** Haptics — UIImpact/UINotificationFeedbackGenerator equivalents. */
class Haptics(private val view: View) {
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun impact() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun success() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
    fun warning() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/** "More ways to share" — the Android share sheet (UIActivityViewController). */
fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** "On your clipboard" card in Add audition — returns text only if it looks like a casting notice. */
fun clipboardCastingNotice(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
    val lower = text.lowercase()
    return if ("tape" in lower || "audition" in lower || "sides" in lower) text else null
}

/** Keep the screen on while this composable is visible (filming, prompter). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

// MARK: - Reader: your own recorded voice only (one file per reader line)

object ReaderFiles {
    fun file(context: Context, lineIndex: Int) = File(context.filesDir, "reader-line-$lineIndex.m4a")
}

/** Plays the reader lines the actor recorded in "Record Maya". Falls back to TTS if provided and unrecorded. */
class ReaderTrackPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(lineIndex: Int) {
        val f = ReaderFiles.file(context, lineIndex)
        if (!f.exists()) return
        stop()
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { it.release(); if (player === it) player = null }
            mp.prepare()
            mp.start()
            player = mp
        }.onFailure { mp.release() } // empty/corrupt recording → stay silent instead of crashing
    }

    fun playOrSynthesize(
        lineIndex: Int,
        text: String = "",
        ttsEngine: RehearsalSpeechEngine? = null,
        onDone: () -> Unit = {},
    ) {
        val f = ReaderFiles.file(context, lineIndex)
        if (f.exists()) {
            stop()
            val mp = MediaPlayer()
            runCatching {
                mp.setDataSource(f.absolutePath)
                mp.setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    onDone()
                }
                mp.prepare()
                mp.start()
                player = mp
            }.onFailure {
                mp.release()
                if (text.isNotBlank() && ttsEngine != null) {
                    ttsEngine.speak(text, onDone)
                } else {
                    onDone()
                }
            }
        } else if (text.isNotBlank() && ttsEngine != null) {
            stop()
            ttsEngine.speak(text, onDone)
        } else {
            onDone()
        }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
    }
}

/** Records one reader line to disk (needs RECORD_AUDIO). */
class ReaderLineRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun start(lineIndex: Int) {
        stop()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioChannels(1)
            r.setOutputFile(ReaderFiles.file(context, lineIndex).absolutePath)
            r.prepare()
            r.start()
            recorder = r
        }.onFailure { r.release() }
    }

    fun stop() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
    }
}

@Composable
fun rememberReaderPlayer(): ReaderTrackPlayer {
    val context = LocalContext.current
    val p = remember { ReaderTrackPlayer(context.applicationContext) }
    DisposableEffect(p) { onDispose { p.stop() } }
    return p
}

@Composable
fun rememberReaderRecorder(): ReaderLineRecorder {
    val context = LocalContext.current
    val r = remember { ReaderLineRecorder(context.applicationContext) }
    DisposableEffect(r) { onDispose { r.stop() } }
    return r
}
