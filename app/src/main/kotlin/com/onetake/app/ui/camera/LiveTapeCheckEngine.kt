package com.onetake.app.ui.camera

import android.content.Context
import android.graphics.RectF
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.Image
import android.media.MediaRecorder
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Real-time analysis engine for live tape checking during shooting.
 * Evaluates camera framing (centering, distance), actor eyeline (rule-of-thirds upper third),
 * scene lighting balance (Y-plane luma), and microphone sound levels (RMS dBFS).
 */
object LiveTapeCheckEngine {

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
        val eyelineLabel: String = "Eye level",
        val isEyelinePassed: Boolean = false,

        val light: LightStatus = LightStatus.Balanced,
        val lightLabel: String = "Balanced",
        val isLightPassed: Boolean = true,

        val sound: SoundStatus = SoundStatus.QuietRoom,
        val soundLabel: String = "Room quiet",
        val isSoundPassed: Boolean = true,
        val audioLevelDb: Float = -50f,

        val faceBoxNorm: RectF? = null,
    ) {
        val allPassed: Boolean
            get() = isFramingPassed && isEyelinePassed && isLightPassed && isSoundPassed
    }

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    fun calculateLuminance(buffer: ByteBuffer): Double {
        val remaining = buffer.remaining()
        if (remaining == 0) return 128.0
        val step = (remaining / 500).coerceAtLeast(1)
        var sum = 0L
        var count = 0
        var i = 0
        while (i < remaining) {
            val byteVal = buffer.get(i).toInt() and 0xFF
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
    ): Pair<FramingStatus, String> {
        if (faces.isEmpty() || uprightWidth <= 0 || uprightHeight <= 0) {
            return FramingStatus.NoFace to "Step into frame"
        }
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return FramingStatus.NoFace to "Step into frame"
        return evaluateFramingMetrics(
            faceCenterX = primaryFace.boundingBox.centerX().toFloat(),
            faceHeight = primaryFace.boundingBox.height().toFloat(),
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            isFrontCamera = isFrontCamera,
        )
    }

    fun evaluateFramingMetrics(
        faceCenterX: Float,
        faceHeight: Float,
        uprightWidth: Int,
        uprightHeight: Int,
        isFrontCamera: Boolean,
    ): Pair<FramingStatus, String> {
        if (uprightWidth <= 0 || uprightHeight <= 0) {
            return FramingStatus.NoFace to "Step into frame"
        }
        val normCenterX = faceCenterX / uprightWidth
        val normHeight = faceHeight / uprightHeight

        val isOffLeft = if (isFrontCamera) normCenterX > 0.62f else normCenterX < 0.38f
        val isOffRight = if (isFrontCamera) normCenterX < 0.38f else normCenterX > 0.62f

        return when {
            isOffLeft -> FramingStatus.ShiftLeft to "Shift left"
            isOffRight -> FramingStatus.ShiftRight to "Shift right"
            normHeight < 0.13f -> FramingStatus.MoveCloser to "Move closer"
            normHeight > 0.56f -> FramingStatus.StepBack to "Step back"
            else -> FramingStatus.Centered to "Framing centered"
        }
    }

    fun evaluateEyeline(
        faces: List<Face>,
        uprightHeight: Int,
    ): Pair<EyelineStatus, String> {
        if (faces.isEmpty() || uprightHeight <= 0) {
            return EyelineStatus.TooLow to "Set eye level"
        }
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return EyelineStatus.TooLow to "Set eye level"
        return evaluateEyelineMetrics(
            faceCenterY = primaryFace.boundingBox.centerY().toFloat(),
            uprightHeight = uprightHeight,
        )
    }

    fun evaluateEyelineMetrics(
        faceCenterY: Float,
        uprightHeight: Int,
    ): Pair<EyelineStatus, String> {
        if (uprightHeight <= 0) {
            return EyelineStatus.TooLow to "Set eye level"
        }
        val normCenterY = faceCenterY / uprightHeight
        return when {
            normCenterY > 0.52f -> EyelineStatus.TooLow to "Raise camera"
            normCenterY < 0.22f -> EyelineStatus.TooHigh to "Tilt down"
            else -> EyelineStatus.Good to "Eyeline level"
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
        var isFrontCamera: Boolean,
        private val onCheckUpdated: (LiveTapeCheck) -> Unit,
    ) : ImageAnalysis.Analyzer {

        private var lastAnalyzedMs = 0L
        var currentAudioDb: Float = -50f
        var micPermissionGranted: Boolean = true

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val now = System.currentTimeMillis()
            // Throttle to ~10 FPS for fluid updates without exhausting CPU
            if (now - lastAnalyzedMs < 95L) {
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
                // 1. Calculate lighting from Y-plane buffer
                val luma = calculateLuminance(imageProxy.planes[0].buffer)
                val (lightStatus, lightLabel) = evaluateLighting(luma)
                val isLightGood = lightStatus == LightStatus.Balanced

                // 2. Sound status
                val (soundStatus, soundLabel) = evaluateSound(micPermissionGranted, currentAudioDb)
                val isSoundGood = soundStatus == SoundStatus.Clear || soundStatus == SoundStatus.QuietRoom

                // 3. Face detection
                val rotation = imageProxy.imageInfo.rotationDegrees
                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                val uprightWidth = if (rotation == 90 || rotation == 270) mediaImage.height else mediaImage.width
                val uprightHeight = if (rotation == 90 || rotation == 270) mediaImage.width else mediaImage.height

                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val (framingStatus, framingLabel) = evaluateFraming(faces, uprightWidth, uprightHeight, isFrontCamera)
                        val (eyelineStatus, eyelineLabel) = evaluateEyeline(faces, uprightHeight)

                        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        val faceBoxNorm = primaryFace?.let { f ->
                            RectF(
                                f.boundingBox.left.toFloat() / uprightWidth,
                                f.boundingBox.top.toFloat() / uprightHeight,
                                f.boundingBox.right.toFloat() / uprightWidth,
                                f.boundingBox.bottom.toFloat() / uprightHeight,
                            )
                        }

                        val isFramingGood = framingStatus == FramingStatus.Centered
                        val isEyelineGood = eyelineStatus == EyelineStatus.Good

                        val check = LiveTapeCheck(
                            framing = framingStatus,
                            framingLabel = framingLabel,
                            isFramingPassed = isFramingGood,
                            eyeline = eyelineStatus,
                            eyelineLabel = eyelineLabel,
                            isEyelinePassed = isEyelineGood,
                            light = lightStatus,
                            lightLabel = lightLabel,
                            isLightPassed = isLightGood,
                            sound = soundStatus,
                            soundLabel = soundLabel,
                            isSoundPassed = isSoundGood,
                            audioLevelDb = currentAudioDb,
                            faceBoxNorm = faceBoxNorm,
                        )
                        onCheckUpdated(check)
                    }
                    .addOnFailureListener {
                        onCheckUpdated(
                            LiveTapeCheck(
                                framing = FramingStatus.NoFace,
                                framingLabel = "Hold still",
                                isFramingPassed = false,
                                eyeline = EyelineStatus.Good,
                                eyelineLabel = "Eyeline level",
                                isEyelinePassed = true,
                                light = lightStatus,
                                lightLabel = lightLabel,
                                isLightPassed = isLightGood,
                                sound = soundStatus,
                                soundLabel = soundLabel,
                                isSoundPassed = isSoundGood,
                                audioLevelDb = currentAudioDb,
                            )
                        )
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } catch (e: Exception) {
                imageProxy.close()
            }
        }
    }

    /**
     * Dedicated background microphone RMS meter. Reads audio levels in real time
     * when recording is not active, cleanly yielding the microphone when video capture rolls.
     */
    class AudioMeter(
        private val context: Context,
        private val scope: CoroutineScope,
        private val onLevelUpdate: (Float) -> Unit,
    ) {
        private var job: Job? = null
        private var record: AudioRecord? = null

        fun start() {
            if (job != null) return
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
                    audioRecord?.release()
                    return@launch
                }

                record = audioRecord
                try {
                    audioRecord.startRecording()
                    val buffer = ShortArray(512)
                    while (isActive) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                val s = buffer[i]
                                sum += s * s
                            }
                            val rms = sqrt(sum / read)
                            val db = if (rms > 0) (20.0 * log10(rms / 32767.0)).toFloat().coerceIn(-100f, 0f) else -100f
                            onLevelUpdate(db)
                        }
                    }
                } catch (e: Exception) {
                    // Safe exit
                } finally {
                    runCatching {
                        audioRecord.stop()
                        audioRecord.release()
                    }
                    record = null
                }
            }
        }

        fun stop() {
            job?.cancel()
            job = null
            runCatching {
                record?.stop()
                record?.release()
            }
            record = null
        }
    }
}
