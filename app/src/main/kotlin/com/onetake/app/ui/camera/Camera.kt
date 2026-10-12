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
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.onetake.app.ui.components.HeadshotImage
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
    private val videoCapture = VideoCapture.withOutput(
        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD)).build()
    )
    private var recording: Recording? = null
    private var provider: ProcessCameraProvider? = null

    internal fun bind(owner: LifecycleOwner, previewView: PreviewView, front: Boolean) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                p.bindToLifecycle(owner, selector, preview, imageCapture, videoCapture)
                state = State.Ready
            }.onFailure { state = State.Failed }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (state != State.Ready) {
            // No camera (emulator / permission denied): still run the timer so the flow can be tested.
            state = State.Recording
            elapsed = 0f
            return
        }
        val file = File(context.cacheDir, "take-${System.currentTimeMillis()}.mp4")
        val pending = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        val withAudio = if (granted(context, Manifest.permission.RECORD_AUDIO)) pending.withAudioEnabled() else pending
        recording = withAudio.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                lastRecording = if (event.hasError()) null else event.outputResults.outputUri
                state = State.Ready
            }
        }
        state = State.Recording
        elapsed = 0f
    }

    fun stopRecording() {
        val r = recording
        recording = null
        if (r != null) r.stop() else state = if (provider != null) State.Ready else State.Idle
    }

    fun capturePhoto(onResult: (Uri?) -> Unit) {
        if (provider == null) { onResult(null); return }
        val file = File(context.cacheDir, "headshot-${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onResult(output.savedUri ?: Uri.fromFile(file))
                override fun onError(exception: ImageCaptureException) = onResult(null)
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
 * Full-bleed camera feed. Asks for camera + mic the first time. Without a camera (emulator,
 * permission denied) it shows the sample headshot, like the iOS Simulator fallback.
 * The feed is clipped to its bounds so it can never push the camera controls off-screen.
 */
@Composable
fun CameraBackground(controller: CameraController, modifier: Modifier = Modifier, front: Boolean = true) {
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
            LaunchedEffect(front) { controller.bind(owner, previewView, front) }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            HeadshotImage(Modifier.fillMaxSize().alpha(0.85f))
        }
    }
}
