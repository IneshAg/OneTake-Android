package com.onetake.app.ui.camera

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.Image
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Pure JVM-safe normalized rectangle in visible [0f..1f] screen coordinates.
 * Avoids android.graphics.RectF stub behavior in JVM unit tests while remaining zero-overhead at runtime.
 */
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
}

/**
 * Real-time analysis engine for live tape checking during shooting.
 * Evaluates camera framing (centering, distance), actor eyeline (rule-of-thirds upper third or full-body headroom),
 * scene lighting balance (Y-plane luma), and microphone sound levels (RMS dBFS).
 */
object LiveTapeCheckEngine {

    enum class FramingTarget {
        MediumCloseUp,
        FullBody,
    }

    enum class FramingStatus {
        Centered,
        ShiftLeft,
        ShiftRight,
        MoveCloser,
        StepBack,
        NoFace,
    }

    enum class EyelineStatus {
        Good,
        TooLow,
        TooHigh,
    }

    enum class LightStatus {
        Balanced,
        TooDark,
        TooBright,
    }

    enum class SoundStatus {
        Clear,
        QuietRoom,
        Clipping,
        MicOff,
    }

    data class LiveTapeCheck(
        val framing: FramingStatus = FramingStatus.NoFace,
        val framingLabel: String = "Step into frame",
        val isFramingPassed: Boolean = false,

        val eyeline: EyelineStatus = EyelineStatus.TooLow,
        val eyelineLabel: String = "Set eye level",
        val isEyelinePassed: Boolean = false,

        val light: LightStatus = LightStatus.Balanced,
        val lightLabel: String = "Light balanced",
        val isLightPassed: Boolean = true,

        val sound: SoundStatus = SoundStatus.QuietRoom,
        val soundLabel: String = "Room quiet",
        val isSoundPassed: Boolean = true,
        val audioLevelDb: Float = -50f,

        val faceBoxNorm: NormalizedBox? = null,
    ) {
        val allPassed: Boolean
            get() = isFramingPassed && isEyelinePassed && isLightPassed && isSoundPassed

        /**
         * Updates sound status immediately when AudioMeter produces new dBFS readings,
         * without waiting for a camera frame callback.
         */
        fun withUpdatedSound(micPermissionGranted: Boolean, audioDb: Float): LiveTapeCheck {
            val (status, label) = evaluateSound(micPermissionGranted, audioDb)
            val passed = status == SoundStatus.Clear || status == SoundStatus.QuietRoom
            return copy(
                sound = status,
                soundLabel = label,
                isSoundPassed = passed,
                audioLevelDb = audioDb,
            )
        }
    }

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Maps an upright ML Kit face bounding box into visible [0..1] PreviewView coordinates,
     * compensating for front-camera horizontal mirroring AND ScaleType.FILL_CENTER center-cropping.
     */
    fun sensorToVisibleScreenBox(
        faceLeft: Float,
        faceTop: Float,
        faceRight: Float,
        faceBottom: Float,
        uprightWidth: Int,
        uprightHeight: Int,
        isFrontCamera: Boolean,
        viewAspect: Float? = null,
    ): NormalizedBox {
        if (uprightWidth <= 0 || uprightHeight <= 0) {
            return NormalizedBox(0f, 0f, 0f, 0f)
        }
        val rawLeft = faceLeft / uprightWidth.toFloat()
        val rawRight = faceRight / uprightWidth.toFloat()
        val rawTop = faceTop / uprightHeight.toFloat()
        val rawBottom = faceBottom / uprightHeight.toFloat()

        val mirroredLeft = if (isFrontCamera) 1f - rawRight else rawLeft
        val mirroredRight = if (isFrontCamera) 1f - rawLeft else rawRight

        val frameAspect = uprightWidth.toFloat() / uprightHeight.toFloat()
        val targetAspect = viewAspect?.takeIf { it > 0.05f } ?: frameAspect

        return if (frameAspect > targetAspect) {
            // Frame is wider than display (e.g. 4:3 sensor on 20:9 portrait display): horizontal sides are cropped.
            val visibleRatioX = (targetAspect / frameAspect).coerceIn(0.1f, 1f)
            val cropOffsetX = (1f - visibleRatioX) * 0.5f
            NormalizedBox(
                left = (mirroredLeft - cropOffsetX) / visibleRatioX,
                top = rawTop,
                right = (mirroredRight - cropOffsetX) / visibleRatioX,
                bottom = rawBottom,
            )
        } else if (frameAspect < targetAspect) {
            // Frame is taller than display: top and bottom are cropped.
            val visibleRatioY = (frameAspect / targetAspect).coerceIn(0.1f, 1f)
            val cropOffsetY = (1f - visibleRatioY) * 0.5f
            NormalizedBox(
                left = mirroredLeft,
                top = (rawTop - cropOffsetY) / visibleRatioY,
                right = mirroredRight,
                bottom = (rawBottom - cropOffsetY) / visibleRatioY,
            )
        } else {
            NormalizedBox(mirroredLeft, rawTop, mirroredRight, rawBottom)
        }
    }

    fun calculateLuminance(buffer: ByteBuffer): Double {
        val dup = buffer.duplicate()
        dup.rewind()
        val remaining = dup.remaining()
        if (remaining == 0) return 128.0
        val step = (remaining / 500).coerceAtLeast(1)
        var sum = 0L
        var count = 0
        var i = 0
        while (i < remaining) {
            val byteVal = dup.get(i).toInt() and 0xFF
            sum += byteVal
            count++
            i += step
        }
        return if (count > 0) sum.toDouble() / count else 128.0
    }

    fun evaluateFraming(
        faces: List<Face>,
        uprightWidth: Int,
        uprightHeight: Int,
        isFrontCamera: Boolean,
        viewAspect: Float? = null,
        target: FramingTarget = FramingTarget.MediumCloseUp,
    ): Pair<FramingStatus, String> {
        if (faces.isEmpty() || uprightWidth <= 0 || uprightHeight <= 0) {
            return FramingStatus.NoFace to "Step into frame"
        }
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return FramingStatus.NoFace to "Step into frame"
        return evaluateFramingMetrics(
            faceCenterX = primaryFace.boundingBox.exactCenterX(),
            faceHeight = primaryFace.boundingBox.height().toFloat(),
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            isFrontCamera = isFrontCamera,
            viewAspect = viewAspect,
            target = target,
        )
    }

    fun evaluateFramingMetrics(
        faceCenterX: Float,
        faceHeight: Float,
        uprightWidth: Int,
        uprightHeight: Int,
        isFrontCamera: Boolean,
        viewAspect: Float? = null,
        target: FramingTarget = FramingTarget.MediumCloseUp,
    ): Pair<FramingStatus, String> {
        if (uprightWidth <= 0 || uprightHeight <= 0) {
            return FramingStatus.NoFace to "Step into frame"
        }
        val halfW = faceHeight * 0.4f
        val visibleBox = sensorToVisibleScreenBox(
            faceLeft = faceCenterX - halfW,
            faceTop = 0f,
            faceRight = faceCenterX + halfW,
            faceBottom = faceHeight,
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            isFrontCamera = isFrontCamera,
            viewAspect = viewAspect,
        )
        val screenCenterX = visibleBox.centerX
        val normHeight = visibleBox.height

        // When screenCenterX < 0.38, the actor is near the LEFT edge of the visible screen and must shift RIGHT.
        // When screenCenterX > 0.62, the actor is near the RIGHT edge of the visible screen and must shift LEFT.
        if (screenCenterX < 0.38f) return FramingStatus.ShiftRight to "Shift right"
        if (screenCenterX > 0.62f) return FramingStatus.ShiftLeft to "Shift left"

        return when (target) {
            FramingTarget.MediumCloseUp -> when {
                normHeight < 0.14f -> FramingStatus.MoveCloser to "Move closer"
                normHeight > 0.56f -> FramingStatus.StepBack to "Step back"
                else -> FramingStatus.Centered to "Framing centered"
            }
            FramingTarget.FullBody -> when {
                normHeight > 0.22f -> FramingStatus.StepBack to "Step further back"
                normHeight < 0.045f -> FramingStatus.MoveCloser to "Move slightly closer"
                else -> FramingStatus.Centered to "Full body framed"
            }
        }
    }

    fun evaluateEyeline(
        faces: List<Face>,
        uprightHeight: Int,
        target: FramingTarget = FramingTarget.MediumCloseUp,
    ): Pair<EyelineStatus, String> {
        if (faces.isEmpty() || uprightHeight <= 0) {
            return EyelineStatus.TooLow to if (target == FramingTarget.FullBody) "Head to toe" else "Set eye level"
        }
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return EyelineStatus.TooLow to "Set eye level"
        return evaluateEyelineMetrics(
            faceCenterY = primaryFace.boundingBox.exactCenterY(),
            uprightHeight = uprightHeight,
            target = target,
        )
    }

    fun evaluateEyelineMetrics(
        faceCenterY: Float,
        uprightHeight: Int,
        target: FramingTarget = FramingTarget.MediumCloseUp,
    ): Pair<EyelineStatus, String> {
        if (uprightHeight <= 0) {
            return EyelineStatus.TooLow to "Set eye level"
        }
        val normCenterY = faceCenterY / uprightHeight.toFloat()
        return when (target) {
            FramingTarget.MediumCloseUp -> when {
                normCenterY > 0.52f -> EyelineStatus.TooLow to "Raise camera"
                normCenterY < 0.20f -> EyelineStatus.TooHigh to "Tilt down"
                else -> EyelineStatus.Good to "Eyeline level"
            }
            FramingTarget.FullBody -> when {
                normCenterY > 0.34f -> EyelineStatus.TooLow to "Tilt down for feet"
                normCenterY < 0.05f -> EyelineStatus.TooHigh to "Leave headroom"
                else -> EyelineStatus.Good to "Headroom good"
            }
        }
    }

    fun evaluateLighting(luma: Double): Pair<LightStatus, String> {
        return when {
            luma < 48.0 -> LightStatus.TooDark to "Too dark"
            luma > 216.0 -> LightStatus.TooBright to "Too bright"
            else -> LightStatus.Balanced to "Light balanced"
        }
    }

    fun evaluateSound(
        micPermissionGranted: Boolean,
        audioDb: Float,
    ): Pair<SoundStatus, String> {
        if (!micPermissionGranted) {
            return SoundStatus.MicOff to "Mic needed"
        }
        return when {
            audioDb > -8f -> SoundStatus.Clipping to "Audio peaking"
            audioDb > -34f -> SoundStatus.Clear to "Clear level"
            else -> SoundStatus.QuietRoom to "Room quiet"
        }
    }

    class Analyzer(
        @Volatile var isFrontCamera: Boolean,
        @Volatile var viewAspect: Float? = null,
        @Volatile var framingTarget: FramingTarget = FramingTarget.MediumCloseUp,
        private val onCheckUpdated: (LiveTapeCheck) -> Unit,
    ) : ImageAnalysis.Analyzer {

        private val mainHandler = Handler(Looper.getMainLooper())
        private var lastAnalyzedMs = 0L
        @Volatile var currentAudioDb: Float = -50f
        @Volatile var micPermissionGranted: Boolean = true

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val now = System.currentTimeMillis()
            if (now - lastAnalyzedMs < 90L) {
                imageProxy.close()
                return
            }
            lastAnalyzedMs = now

            val mediaImage: Image? = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            try {
                val luma = calculateLuminance(imageProxy.planes[0].buffer)
                val (lightStatus, lightLabel) = evaluateLighting(luma)
                val isLightGood = lightStatus == LightStatus.Balanced

                val audioDbSnapshot = currentAudioDb
                val micGrantedSnapshot = micPermissionGranted
                val (soundStatus, soundLabel) = evaluateSound(micGrantedSnapshot, audioDbSnapshot)
                val isSoundGood = soundStatus == SoundStatus.Clear || soundStatus == SoundStatus.QuietRoom

                val rotation = imageProxy.imageInfo.rotationDegrees
                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                val uprightWidth = if (rotation == 90 || rotation == 270) mediaImage.height else mediaImage.width
                val uprightHeight = if (rotation == 90 || rotation == 270) mediaImage.width else mediaImage.height
                val frontSnapshot = isFrontCamera
                val aspectSnapshot = viewAspect
                val targetSnapshot = framingTarget

                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val (framingStatus, framingLabel) = evaluateFraming(
                            faces = faces,
                            uprightWidth = uprightWidth,
                            uprightHeight = uprightHeight,
                            isFrontCamera = frontSnapshot,
                            viewAspect = aspectSnapshot,
                            target = targetSnapshot,
                        )
                        val (eyelineStatus, eyelineLabel) = evaluateEyeline(
                            faces = faces,
                            uprightHeight = uprightHeight,
                            target = targetSnapshot,
                        )

                        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        val faceBoxNorm = primaryFace?.let { f ->
                            sensorToVisibleScreenBox(
                                faceLeft = f.boundingBox.left.toFloat(),
                                faceTop = f.boundingBox.top.toFloat(),
                                faceRight = f.boundingBox.right.toFloat(),
                                faceBottom = f.boundingBox.bottom.toFloat(),
                                uprightWidth = uprightWidth,
                                uprightHeight = uprightHeight,
                                isFrontCamera = frontSnapshot,
                                viewAspect = aspectSnapshot,
                            )
                        }

                        val check = LiveTapeCheck(
                            framing = framingStatus,
                            framingLabel = framingLabel,
                            isFramingPassed = framingStatus == FramingStatus.Centered,
                            eyeline = eyelineStatus,
                            eyelineLabel = eyelineLabel,
                            isEyelinePassed = eyelineStatus == EyelineStatus.Good,
                            light = lightStatus,
                            lightLabel = lightLabel,
                            isLightPassed = isLightGood,
                            sound = soundStatus,
                            soundLabel = soundLabel,
                            isSoundPassed = isSoundGood,
                            audioLevelDb = currentAudioDb,
                            faceBoxNorm = faceBoxNorm,
                        )
                        mainHandler.post { onCheckUpdated(check) }
                    }
                    .addOnFailureListener {
                        val fallback = LiveTapeCheck(
                            framing = FramingStatus.NoFace,
                            framingLabel = "Hold still",
                            isFramingPassed = false,
                            eyeline = EyelineStatus.TooLow,
                            eyelineLabel = "Set eye level",
                            isEyelinePassed = false,
                            light = lightStatus,
                            lightLabel = lightLabel,
                            isLightPassed = isLightGood,
                            sound = soundStatus,
                            soundLabel = soundLabel,
                            isSoundPassed = isSoundGood,
                            audioLevelDb = currentAudioDb,
                        )
                        mainHandler.post { onCheckUpdated(fallback) }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } catch (t: Throwable) {
                imageProxy.close()
            }
        }
    }

    /**
     * Thread-safe background microphone dBFS RMS meter.
     * Ownership of AudioRecord stop/release belongs strictly to the IO coroutine's finally block
     * so stopping never double-releases or races with audioRecord.read().
     */
    class AudioMeter(
        @Suppress("UNUSED_PARAMETER") context: Context,
        private val scope: CoroutineScope,
        private val onLevelUpdate: (Float) -> Unit,
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var job: Job? = null

        @android.annotation.SuppressLint("MissingPermission")
        fun start() {
            if (job?.isActive == true) return
            job = scope.launch(Dispatchers.IO) {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = minBufferSize.coerceAtLeast(1024)

                val audioRecord = runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize,
                    )
                }.getOrNull()

                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    runCatching { audioRecord?.release() }
                    return@launch
                }

                try {
                    audioRecord.startRecording()
                    val buffer = ShortArray(512)
                    while (isActive) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0 && isActive) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                val s = buffer[i].toDouble()
                                sum += s * s
                            }
                            val rms = sqrt(sum / read)
                            val db = if (rms > 0) (20.0 * log10(rms / 32767.0)).toFloat().coerceIn(-100f, 0f) else -100f
                            mainHandler.post { onLevelUpdate(db) }
                        } else if (read < 0) {
                            break
                        }
                    }
                } catch (_: CancellationException) {
                    // Normal cancellation
                } catch (_: Throwable) {
                    // Safe exit
                } finally {
                    runCatching {
                        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            audioRecord.stop()
                        }
                    }
                    runCatching { audioRecord.release() }
                }
            }
        }

        fun stop() {
            val active = job
            job = null
            active?.cancel()
        }

        suspend fun stopAndJoin() {
            val active = job
            job = null
            if (active != null) {
                active.cancel()
                withContext(Dispatchers.IO) {
                    runCatching { active.join() }
                }
            }
        }
    }
}
