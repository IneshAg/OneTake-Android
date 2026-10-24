package com.onetake.app.ui.system

import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
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

/** A real file:// URI exposed through the app's FileProvider — API 24+ refuses to hand raw
 *  file:// URIs to other apps, so anything handed off to the share sheet or a viewer has to go
 *  through this. Mirrors iOS's `ShareSheet(items: [url])`. */
fun fileProviderUri(context: Context, file: File): android.net.Uri =
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Hands a real exported file to the Android share sheet — another app, Drive, Bluetooth, mail,
 *  whatever's installed. There's no casting-site upload API this app can call directly. */
fun shareFile(context: Context, file: File, subject: String) {
    val uri = fileProviderUri(context, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Opens a real exported file in the device's default video player. */
fun viewFile(context: Context, file: File) {
    val uri = fileProviderUri(context, file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(view) }
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

// MARK: - Reader: your own recorded voice only (one file per reader line, per scene)

/** Reader recordings live under reader/<scene id>/, numbered by reader line within that scene.
 *  Scoped by scene so one audition never plays (or overwrites) another audition's reader voice.
 *  Older builds wrote flat filesDir/reader-line-N.m4a files that can't be attributed to a scene;
 *  those are only counted and deleted by Privacy & data ([LEGACY_PREFIX]). */
object ReaderFiles {
    const val DIR = "reader"
    const val LEGACY_PREFIX = "reader-line-"
    fun sceneDir(context: Context, sceneId: String) = File(File(context.filesDir, DIR), sceneId)
    fun file(context: Context, sceneId: String, readerIndex: Int) = File(sceneDir(context, sceneId), "line-$readerIndex.m4a")
}

/** Plays the reader lines the actor recorded in "Record Maya". Falls back to TTS if provided and unrecorded. */
class ReaderTrackPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    /** 0–1, applied to every recording played (Profile → Reader volume while shooting). */
    var volume: Float = 1f

    fun play(sceneId: String, readerIndex: Int) {
        val f = ReaderFiles.file(context, sceneId, readerIndex)
        if (!f.exists()) return
        stop()
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { it.release(); if (player === it) player = null }
            mp.prepare()
            mp.setVolume(volume, volume)
            mp.start()
            player = mp
        }.onFailure { mp.release() } // empty/corrupt recording → stay silent instead of crashing
    }

    fun playOrSynthesize(
        sceneId: String,
        readerIndex: Int,
        text: String = "",
        ttsEngine: RehearsalSpeechEngine? = null,
        onDone: () -> Unit = {},
    ) {
        val f = ReaderFiles.file(context, sceneId, readerIndex)
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
                mp.setVolume(volume, volume)
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

/** Plays the recorded reader line (or speaks it) and suspends until it has finished, or until
 *  [timeoutMs] in case neither reports completion. */
suspend fun ReaderTrackPlayer.playAndAwait(
    sceneId: String,
    readerIndex: Int,
    text: String,
    ttsEngine: RehearsalSpeechEngine?,
    timeoutMs: Long,
) {
    withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            playOrSynthesize(sceneId, readerIndex, text, ttsEngine) { if (cont.isActive) cont.resume(Unit) }
        }
    }
}

/** Records one reader line to disk (needs RECORD_AUDIO). */
class ReaderLineRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    /** Starts recording; false if the recorder couldn't start (mic busy, no permission…). */
    fun start(sceneId: String, readerIndex: Int): Boolean {
        stop()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioChannels(1)
            val out = ReaderFiles.file(context, sceneId, readerIndex)
            out.parentFile?.mkdirs()
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        }.onFailure { r.release() }.isSuccess
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
