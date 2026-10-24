package com.onetake.app.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.delay
import java.io.File

/**
 * CameraX capture pipeline — Android twin of iOS `CameraController` (AVFoundation).
 * Compose has no camera view, so this is where OneTake drops to the Android View system (PreviewView).
 */
@Stable
class CameraController(private val context: Context) {
    enum class State { Idle, Ready, Recording, Failed }

    var state by mutableStateOf(State.Idle)
        private set
    var elapsed by mutableFloatStateOf(0f)
        private set
    var lastRecording by mutableStateOf<Uri?>(null)
        private set
    var hasPermission by mutableStateOf(granted(context, Manifest.permission.CAMERA))
        internal set

    private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var provider: ProcessCameraProvider? = null
    /** Who gets the finished file of the recording being stopped. Held here, not in a composable
     *  effect, so the file still lands if the screen closes before CameraX finalizes it. */
    private var onFinalized: ((Uri?) -> Unit)? = null

    private fun createVideoCapture(): VideoCapture<Recorder> {
        // 1080p is what self-tape sites ask for (and what the destinations' specs say). 4K first
        // made files ~4x larger, slowed export, and could break limits like Actors Access's 500 MB.
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD),
        )
        val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
        return VideoCapture.withOutput(recorder)
    }

    internal fun bind(owner: LifecycleOwner, previewView: PreviewView, front: Boolean, photoOnly: Boolean = false) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
                val selector = when {
                    front && p.hasCamera(frontSelector) -> frontSelector
                    !front && p.hasCamera(backSelector) -> backSelector
                    p.hasCamera(frontSelector) -> frontSelector
                    p.hasCamera(backSelector) -> backSelector
                    else -> if (front) frontSelector else backSelector
                }
                p.unbindAll()
                if (photoOnly) {
                    videoCapture = null
                    p.bindToLifecycle(owner, selector, preview, imageCapture)
                } else {
                    val vc = runCatching { createVideoCapture() }.getOrNull()
                    videoCapture = vc
                    if (vc != null) {
                        runCatching {
                            p.bindToLifecycle(owner, selector, preview, imageCapture, vc)
                        }.recoverCatching {
                            p.bindToLifecycle(owner, selector, preview, vc)
                        }.recoverCatching {
                            // VideoCapture is not supported on this camera/device stream combination.
                            // Fall back to ImageCapture + Preview so camera remains fully usable.
                            videoCapture = null
                            p.bindToLifecycle(owner, selector, preview, imageCapture)
                        }.recoverCatching {
                            videoCapture = null
                            p.bindToLifecycle(owner, selector, preview)
                        }.getOrThrow()
                    } else {
                        runCatching {
                            p.bindToLifecycle(owner, selector, preview, imageCapture)
                        }.recoverCatching {
                            p.bindToLifecycle(owner, selector, preview)
                        }.getOrThrow()
                    }
                }
                state = State.Ready
            }.onFailure { e ->
                android.util.Log.e("CameraController", "Camera bind failed", e)
                state = State.Failed
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Why the last [startRecording] didn't start, for the screen to show ([RecordStartErrorDialog]);
     *  null once it's been seen or a recording did start. */
    var startError by mutableStateOf<String?>(null)
        private set

    fun clearStartError() { startError = null }

    /** Starts a real recording and returns true. With nothing to record with (camera not bound yet,
     *  or this device can't bind video) it returns false and sets [startError] — it used to run the
     *  timer anyway, so the actor performed a whole take that was never saved. */
    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        val vc = videoCapture
        if (state != State.Ready || vc == null) {
            startError = when {
                state == State.Failed -> "The camera couldn't start on this device."
                state == State.Ready -> "This camera can't record video on this device, only photos."
                else -> "The camera isn't ready yet. Give it a second and try again."
            }
            return false
        }
        val file = File(context.cacheDir, "take-${System.currentTimeMillis()}.mp4")
        val started = runCatching {
            val pending = vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            val withAudio = if (granted(context, Manifest.permission.RECORD_AUDIO)) pending.withAudioEnabled() else pending
            withAudio.start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val uri = if (event.hasError()) null else event.outputResults.outputUri
                    lastRecording = uri
                    state = State.Ready
                    val callback = onFinalized
                    onFinalized = null
                    callback?.invoke(uri)
                }
            }
        }
        recording = started.getOrElse {
            android.util.Log.e("CameraController", "Recording failed to start", it)
            startError = "The recording couldn't start. Try again, or close and reopen the camera."
            return false
        }
        startError = null
        state = State.Recording
        elapsed = 0f
        return true
    }

    /** Stops the current recording. [onFinalized] runs once the file is fully written (null if
     *  nothing real was recorded), even if the calling screen has gone by then. */
    fun stopRecording(onFinalized: ((Uri?) -> Unit)? = null) {
        val r = recording
        recording = null
        if (r != null) {
            this.onFinalized = onFinalized
            r.stop()
        } else {
            state = if (provider != null) State.Ready else State.Idle
            onFinalized?.invoke(null)
        }
    }

    fun capturePhoto(onResult: (Uri?) -> Unit) {
        val dir = File(context.filesDir, "headshots").apply { mkdirs() }
        val file = File(dir, "headshot-${System.currentTimeMillis()}.jpg")
        val p = provider
        if (p == null || state != State.Ready) {
            // Camera not ready or unavailable: report failure rather than saving a placeholder
            // image as if it were the actor's real headshot.
            onResult(null)
            return
        }
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onResult(output.savedUri ?: Uri.fromFile(file))
                override fun onError(exception: ImageCaptureException) {
                    android.util.Log.e("CameraController", "capturePhoto failed", exception)
                    onResult(null)
                }
            },
        )
    }

    internal suspend fun tick() {
        while (true) {
            delay(100)
            if (state == State.Recording) elapsed += 0.1f
        }
    }

    fun release() {
        recording?.stop()
        provider?.unbindAll()
    }

    companion object {
        fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberCameraController(): CameraController {
    val context = LocalContext.current
    val controller = remember { CameraController(context.applicationContext) }
    LaunchedEffect(controller) { controller.tick() }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/**
 * Full-bleed camera feed. Requests camera + audio permissions cleanly.
 * When permissions are missing or camera hardware is unavailable, renders a sleek studio viewfinder
 * with clear status instead of any bundled stock photos.
 */
@Composable
fun CameraBackground(
    controller: CameraController,
    modifier: Modifier = Modifier,
    front: Boolean = true,
    photoOnly: Boolean = false,
) {
    val owner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        controller.hasPermission = result[Manifest.permission.CAMERA] == true
    }
    LaunchedEffect(Unit) {
        if (!controller.hasPermission) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }
    Box(modifier.fillMaxSize().clipToBounds().background(OT.camera.bg)) {
        if (controller.hasPermission && controller.state != CameraController.State.Failed) {
            val context = LocalContext.current
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }
            LaunchedEffect(front, photoOnly) { controller.bind(owner, previewView, front, photoOnly) }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else if (!controller.hasPermission) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF24242A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Text("Camera Access Required", style = OT.type.title, color = Color.White, textAlign = TextAlign.Center)
                    Text(
                        "OneTake needs camera access to capture your headshots and record takes.",
                        style = OT.type.callout,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    PrimaryButton("Grant Access", onClick = {
                        launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    })
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF24242A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.VideocamOff, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
                    }
                    Text("Camera Unavailable", style = OT.type.title, color = Color.White, textAlign = TextAlign.Center)
                    Text(
                        "Camera feed could not be initialized on this device.",
                        style = OT.type.callout,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Tells the actor when a take didn't start, instead of a silent timer over nothing. */
@Composable
fun RecordStartErrorDialog(controller: CameraController) {
    val message = controller.startError ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { controller.clearStartError() },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { controller.clearStartError() }) { Text("OK") }
        },
        title = { Text("Not recording") },
        text = { Text(message) },
    )
}
