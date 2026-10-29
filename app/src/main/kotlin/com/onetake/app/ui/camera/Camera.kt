package com.onetake.app.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CameraX capture pipeline — powers live framing analysis, photo capture, and H.264 MP4 recording.
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
    var hasMicPermission by mutableStateOf(granted(context, Manifest.permission.RECORD_AUDIO))
        internal set
    var isAnalysisActive by mutableStateOf(false)
        private set
    var isExposureLocked by mutableStateOf(false)
        private set
    var viewAspect by mutableFloatStateOf(0.56f)
        internal set

    private val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var currentAnalyzer: ImageAnalysis.Analyzer? = null
    private var analyzerExecutor: ExecutorService? = null
    private var recording: Recording? = null
    private var recordingStartRealtimeMs: Long = 0L
    private var provider: ProcessCameraProvider? = null
    private var boundCamera: Camera? = null

    private var boundOwner: LifecycleOwner? = null
    private var boundPreviewView: PreviewView? = null
    private var boundSelector: CameraSelector? = null
    private var boundFront: Boolean = true
    private var boundPhotoOnly: Boolean = false
    private var boundNeedsVideo: Boolean = true
    private var pendingRebind: Boolean = false

    /** Who gets the finished file of the recording being stopped. Held here so finalization completes cleanly. */
    private var onFinalized: ((Uri?) -> Unit)? = null

    private fun buildImageAnalysis(analyzer: ImageAnalysis.Analyzer): ImageAnalysis {
        val executor = analyzerExecutor ?: Executors.newSingleThreadExecutor().also { analyzerExecutor = it }
        val selector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(480, 640),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(selector)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(executor, analyzer) }
    }

    fun setAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        if (currentAnalyzer === analyzer && imageAnalysis != null) return
        imageAnalysis?.clearAnalyzer()
        currentAnalyzer = analyzer
        imageAnalysis = buildImageAnalysis(analyzer)
        requestRebind()
    }

    fun clearAnalyzer() {
        if (currentAnalyzer == null && imageAnalysis == null) return
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        currentAnalyzer = null
        isAnalysisActive = false
        requestRebind()
    }

    private fun requestRebind() {
        // Never unbind CameraX while an active recording is flushing/finalizing its MP4 file!
        if (recording != null || state == State.Recording) {
            pendingRebind = true
            return
        }
        val o = boundOwner ?: return
        val pv = boundPreviewView ?: return
        bind(o, pv, boundFront, boundPhotoOnly, boundNeedsVideo)
    }

    private fun createVideoCapture(preferHdFor3StreamCompatibility: Boolean = false): VideoCapture<Recorder> {
        val qualities = if (preferHdFor3StreamCompatibility) {
            listOf(Quality.HD, Quality.SD)
        } else {
            listOf(Quality.FHD, Quality.HD, Quality.SD)
        }
        val primary = qualities.first()
        val qualitySelector = QualitySelector.fromOrderedList(
            qualities,
            FallbackStrategy.lowerQualityOrHigherThan(primary),
        )
        val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
        return VideoCapture.withOutput(recorder)
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setExposureLocked(locked: Boolean, focusCenterNormX: Float = 0.5f, focusCenterNormY: Float = 0.36f) {
        isExposureLocked = locked
        val cam = boundCamera ?: return
        runCatching {
            val c2 = Camera2CameraControl.from(cam.cameraControl)
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
                .build()
            c2.setCaptureRequestOptions(opts)
            if (locked) {
                val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                val point = factory.createPoint(
                    focusCenterNormX.coerceIn(0.1f, 0.9f),
                    focusCenterNormY.coerceIn(0.1f, 0.9f),
                )
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .disableAutoCancel()
                    .build()
                cam.cameraControl.startFocusAndMetering(action)
            } else {
                cam.cameraControl.cancelFocusAndMetering()
            }
        }
    }

    internal fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        front: Boolean,
        photoOnly: Boolean = false,
        needsVideo: Boolean = !photoOnly,
    ) {
        boundOwner = owner
        boundPreviewView = previewView
        boundFront = front
        boundPhotoOnly = photoOnly
        boundNeedsVideo = needsVideo

        // If a recording is actively flushing, defer rebinding until VideoRecordEvent.Finalize arrives.
        if (recording != null || state == State.Recording) {
            pendingRebind = true
            return
        }
        pendingRebind = false

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (recording != null || state == State.Recording) {
                pendingRebind = true
                return@addListener
            }
            runCatching {
                val p = future.get()
                provider = p
                fun newPreview() = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
                val selector = when {
                    front && p.hasCamera(frontSelector) -> frontSelector
                    !front && p.hasCamera(backSelector) -> backSelector
                    p.hasCamera(frontSelector) -> frontSelector
                    p.hasCamera(backSelector) -> backSelector
                    else -> if (front) frontSelector else backSelector
                }
                boundSelector = selector

                // Rebuild ImageAnalysis cleanly if an analyzer is registered
                val activeAnalyzer = currentAnalyzer
                val analysis = if (activeAnalyzer != null) {
                    imageAnalysis ?: buildImageAnalysis(activeAnalyzer).also { imageAnalysis = it }
                } else {
                    null
                }

                var analysisBound = false
                var cameraInstance: Camera? = null

                if (photoOnly) {
                    videoCapture = null
                    if (analysis != null) {
                        cameraInstance = runCatching {
                            p.unbindAll()
                            p.bindToLifecycle(owner, selector, newPreview(), imageCapture, analysis).also { analysisBound = true }
                        }.recoverCatching {
                            // Guaranteed 2-stream PRIV + YUV fallback; capturePhoto temporarily swaps to ImageCapture on shutter!
                            p.unbindAll()
                            p.bindToLifecycle(owner, selector, newPreview(), analysis).also { analysisBound = true }
                        }.recoverCatching {
                            p.unbindAll()
                            analysisBound = false
                            p.bindToLifecycle(owner, selector, newPreview(), imageCapture)
                        }.getOrThrow()
                    } else {
                        p.unbindAll()
                        cameraInstance = p.bindToLifecycle(owner, selector, newPreview(), imageCapture)
                    }
                } else if (!needsVideo) {
                    // Check-only stage (e.g. ShootStage.Check): pure 2-stream Preview + ImageAnalysis.
                    // Never binds ImageCapture or VideoCapture, guaranteeing 100% compatibility on LIMITED/LEGACY front cameras.
                    videoCapture = null
                    if (analysis != null) {
                        cameraInstance = runCatching {
                            p.unbindAll()
                            p.bindToLifecycle(owner, selector, newPreview(), analysis).also { analysisBound = true }
                        }.recoverCatching {
                            p.unbindAll()
                            analysisBound = false
                            p.bindToLifecycle(owner, selector, newPreview())
                        }.getOrThrow()
                    } else {
                        p.unbindAll()
                        cameraInstance = p.bindToLifecycle(owner, selector, newPreview())
                    }
                } else {
                    // Video recording stages: NEVER bind ImageCapture (saves an entire hardware JPEG stream!).
                    if (analysis != null) {
                        cameraInstance = runCatching {
                            p.unbindAll()
                            val vcFhd = createVideoCapture(preferHdFor3StreamCompatibility = false)
                            videoCapture = vcFhd
                            p.bindToLifecycle(owner, selector, newPreview(), vcFhd, analysis).also { analysisBound = true }
                        }.recoverCatching {
                            // Many LIMITED front cameras support PRIV(Preview) + PRIV(720p HD) + YUV(Analysis) even when 1080p fails.
                            p.unbindAll()
                            val vcHd = createVideoCapture(preferHdFor3StreamCompatibility = true)
                            videoCapture = vcHd
                            p.bindToLifecycle(owner, selector, newPreview(), vcHd, analysis).also { analysisBound = true }
                        }.recoverCatching {
                            // Final video fallback if hardware strictly forbids 3 streams even with StreamSharing.
                            p.unbindAll()
                            analysisBound = false
                            val vcFhd = createVideoCapture(preferHdFor3StreamCompatibility = false)
                            videoCapture = vcFhd
                            p.bindToLifecycle(owner, selector, newPreview(), vcFhd)
                        }.recoverCatching {
                            p.unbindAll()
                            analysisBound = false
                            videoCapture = null
                            p.bindToLifecycle(owner, selector, newPreview())
                        }.getOrThrow()
                    } else {
                        cameraInstance = runCatching {
                            p.unbindAll()
                            val vcFhd = createVideoCapture(preferHdFor3StreamCompatibility = false)
                            videoCapture = vcFhd
                            p.bindToLifecycle(owner, selector, newPreview(), vcFhd)
                        }.recoverCatching {
                            p.unbindAll()
                            videoCapture = null
                            p.bindToLifecycle(owner, selector, newPreview())
                        }.getOrThrow()
                    }
                }

                boundCamera = cameraInstance
                isAnalysisActive = analysisBound
                state = State.Ready

                // Re-apply exposure lock if actor already locked exposure before stage transition
                if (isExposureLocked) {
                    setExposureLocked(true)
                }
            }.onFailure { e ->
                android.util.Log.e("CameraController", "Camera bind failed", e)
                isAnalysisActive = false
                state = State.Failed
            }
        }, ContextCompat.getMainExecutor(context))
    }

    var startError by mutableStateOf<String?>(null)
        private set

    fun clearStartError() { startError = null }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        val vc = videoCapture
        if (state != State.Ready || vc == null) {
            startError = when {
                state == State.Failed -> "The camera couldn't start on this device."
                state == State.Ready -> "The camera is finishing setup. Give it a second and try again."
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
                    val uri = if (event.hasError()) {
                        file.delete()
                        null
                    } else {
                        event.outputResults.outputUri
                    }
                    recording = null
                    lastRecording = uri
                    state = State.Ready
                    val callback = onFinalized
                    onFinalized = null
                    callback?.invoke(uri)
                    if (pendingRebind) {
                        pendingRebind = false
                        requestRebind()
                    }
                }
            }
        }
        recording = started.getOrElse {
            android.util.Log.e("CameraController", "Recording failed to start", it)
            startError = "The recording couldn't start. Try again, or close and reopen the camera."
            return false
        }
        recordingStartRealtimeMs = SystemClock.elapsedRealtime()
        startError = null
        state = State.Recording
        elapsed = 0f
        return true
    }

    fun stopRecording(onFinalized: ((Uri?) -> Unit)? = null) {
        val r = recording
        if (r != null) {
            this.onFinalized = onFinalized
            // Keep `recording` non-null until VideoRecordEvent.Finalize fires so rebind() never calls unbindAll() mid-flush!
            runCatching { r.stop() }.onFailure {
                recording = null
                state = if (provider != null) State.Ready else State.Idle
                this.onFinalized = null
                onFinalized?.invoke(null)
            }
        } else {
            state = if (provider != null) State.Ready else State.Idle
            onFinalized?.invoke(null)
        }
    }

    fun capturePhoto(onResult: (Uri?) -> Unit) {
        val dir = File(context.filesDir, "headshots").apply { mkdirs() }
        val file = File(dir, "headshot-${System.currentTimeMillis()}.jpg")
        val p = provider
        val owner = boundOwner
        val selector = boundSelector
        val pv = boundPreviewView
        if (p == null || state != State.Ready) {
            onResult(null)
            return
        }

        val takeAction = { onComplete: () -> Unit ->
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        onComplete()
                        onResult(output.savedUri ?: Uri.fromFile(file))
                    }
                    override fun onError(exception: ImageCaptureException) {
                        android.util.Log.e("CameraController", "capturePhoto failed", exception)
                        onComplete()
                        onResult(null)
                    }
                },
            )
        }

        if (p.isBound(imageCapture)) {
            takeAction {}
        } else if (owner != null && selector != null && pv != null) {
            // Clean full-session swap for constrained devices where Preview + Analysis was bound without ImageCapture
            runCatching {
                p.unbindAll()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                p.bindToLifecycle(owner, selector, preview, imageCapture)
                takeAction {
                    requestRebind()
                }
            }.onFailure { e ->
                android.util.Log.e("CameraController", "Failed to bind imageCapture for photo", e)
                requestRebind()
                onResult(null)
            }
        } else {
            onResult(null)
        }
    }

    internal suspend fun tick() {
        while (true) {
            delay(100)
            if (state == State.Recording && recordingStartRealtimeMs > 0L) {
                elapsed = ((SystemClock.elapsedRealtime() - recordingStartRealtimeMs).coerceAtLeast(0L)) / 1000f
            }
        }
    }

    fun release() {
        runCatching { recording?.stop() }
        recording = null
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        currentAnalyzer = null
        isAnalysisActive = false
        boundCamera = null
        runCatching { analyzerExecutor?.shutdown() }
        analyzerExecutor = null
        runCatching { provider?.unbindAll() }
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
 * Full-bleed camera feed. Requests camera + audio permissions in a single unified system dialog.
 */
@Composable
fun CameraBackground(
    controller: CameraController,
    modifier: Modifier = Modifier,
    front: Boolean = true,
    photoOnly: Boolean = false,
    needsVideo: Boolean = !photoOnly,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        controller.hasPermission = result[Manifest.permission.CAMERA] == true ||
            CameraController.granted(context, Manifest.permission.CAMERA)
        controller.hasMicPermission = result[Manifest.permission.RECORD_AUDIO] == true ||
            CameraController.granted(context, Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) {
        controller.hasPermission = CameraController.granted(context, Manifest.permission.CAMERA)
        controller.hasMicPermission = CameraController.granted(context, Manifest.permission.RECORD_AUDIO)
        if (!controller.hasPermission || !controller.hasMicPermission) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(OT.camera.bg)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    controller.viewAspect = size.width.toFloat() / size.height.toFloat()
                }
            },
    ) {
        if (controller.hasPermission && controller.state != CameraController.State.Failed) {
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }
            }
            LaunchedEffect(front, photoOnly, needsVideo) {
                controller.bind(owner, previewView, front, photoOnly, needsVideo)
            }
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
                        "OneTake needs camera and mic access to frame your shot and record takes.",
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

/**
 * Live Cinema Framing Assist Viewfinder Overlay:
 * Draws subtle rule-of-thirds composition lines, upper-third self-tape eyeline mark (or full-body headroom/footroom guides),
 * and smooth animated corner tracking brackets around the detected actor's face.
 */
@Composable
fun FramingAssistOverlay(
    check: LiveTapeCheckEngine.LiveTapeCheck,
    modifier: Modifier = Modifier,
    target: LiveTapeCheckEngine.FramingTarget = LiveTapeCheckEngine.FramingTarget.MediumCloseUp,
    showGrid: Boolean = true,
    showFaceReticle: Boolean = true,
) {
    val isGood = check.isFramingPassed && check.isEyelinePassed
    val reticleColor by animateColorAsState(
        targetValue = when {
            check.faceBoxNorm == null -> Color.White.copy(alpha = 0.35f)
            isGood -> Color(0xFF34C759)
            else -> Color(0xFFFFB340)
        },
        animationSpec = tween(180),
        label = "framingReticleColor",
    )

    val box = check.faceBoxNorm
    val animLeft by animateFloatAsState(box?.left ?: 0.32f, tween(110), label = "faceL")
    val animTop by animateFloatAsState(box?.top ?: 0.22f, tween(110), label = "faceT")
    val animRight by animateFloatAsState(box?.right ?: 0.68f, tween(110), label = "faceR")
    val animBottom by animateFloatAsState(box?.bottom ?: 0.50f, tween(110), label = "faceB")

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.14f)
            val stroke = 1.dp.toPx()
            // Rule of thirds vertical lines
            drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = stroke)
            drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = stroke)
            // Horizontal thirds
            drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = stroke)
            drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = stroke)

            // Target eyeline / headroom dashed guide
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            val eyelineY = if (target == LiveTapeCheckEngine.FramingTarget.FullBody) h * 0.14f else h * 0.333f
            val eyelineColor = if (check.isEyelinePassed) {
                Color(0xFF34C759).copy(alpha = 0.45f)
            } else {
                Color(0xFFFFB340).copy(alpha = 0.45f)
            }
            drawLine(
                color = eyelineColor,
                start = Offset(w * 0.18f, eyelineY),
                end = Offset(w * 0.82f, eyelineY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect,
            )
            if (target == LiveTapeCheckEngine.FramingTarget.FullBody) {
                // Footroom baseline mark for full-length head-to-toe shot
                val footY = h * 0.88f
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(w * 0.25f, footY),
                    end = Offset(w * 0.75f, footY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect,
                )
            }
        }

        if (showFaceReticle && box != null) {
            val padX = (w * 0.025f)
            val padY = (h * 0.018f)
            val l = (animLeft * w - padX).coerceIn(8f, w - 24f)
            val t = (animTop * h - padY).coerceIn(8f, h - 24f)
            val r = (animRight * w + padX).coerceIn(l + 24f, w - 8f)
            val b = (animBottom * h + padY).coerceIn(t + 24f, h - 8f)

            val corner = minOf(r - l, b - t) * 0.24f
            val sw = 2.5.dp.toPx()

            // Top-left bracket
            drawLine(reticleColor, Offset(l, t), Offset(l + corner, t), sw, StrokeCap.Round)
            drawLine(reticleColor, Offset(l, t), Offset(l, t + corner), sw, StrokeCap.Round)
            // Top-right bracket
            drawLine(reticleColor, Offset(r, t), Offset(r - corner, t), sw, StrokeCap.Round)
            drawLine(reticleColor, Offset(r, t), Offset(r, t + corner), sw, StrokeCap.Round)
            // Bottom-left bracket
            drawLine(reticleColor, Offset(l, b), Offset(l + corner, b), sw, StrokeCap.Round)
            drawLine(reticleColor, Offset(l, b), Offset(l, b - corner), sw, StrokeCap.Round)
            // Bottom-right bracket
            drawLine(reticleColor, Offset(r, b), Offset(r - corner, b), sw, StrokeCap.Round)
            drawLine(reticleColor, Offset(r, b), Offset(r, b - corner), sw, StrokeCap.Round)
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
