package com.onetake.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Real-time framing, lighting, and angle analysis for headshots, plus face-centered 4:5 auto-cropping.
 * Powers live dynamic guidance in HeadshotFlowScreen and ensures captured headshots match industry standard 4:5.
 */
object HeadshotFramingEngine {

    enum class HeadshotAngle(val label: String) { Front("Front"), Left("Left"), Right("Right") }

    data class FramingGuidance(
        val headline: String,
        val lightLabel: String,
        val isLightGood: Boolean,
        val angleLabel: String,
        val isAngleGood: Boolean,
        val isCenteredAndReady: Boolean,
        val faceBoxNorm: RectF? = null,
    )

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * CameraX ImageAnalysis.Analyzer that inspects live camera frames for face centering, head angle,
     * and scene lighting. Runs on a background executor.
     */
    class LiveAnalyzer(
        var targetAngle: HeadshotAngle,
        var isFrontCamera: Boolean,
        private val onGuidanceUpdate: (FramingGuidance) -> Unit,
    ) : ImageAnalysis.Analyzer {

        private var lastAnalyzedMs = 0L

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val now = System.currentTimeMillis()
            // Throttle to ~12 FPS for smooth UI without exhausting CPU
            if (now - lastAnalyzedMs < 80L) {
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
                // 1. Calculate lighting from Y-plane luminance buffer
                val luma = calculateLuminance(imageProxy.planes[0].buffer)
                val isLightTooDark = luma < 45
                val isLightTooBright = luma > 218
                val isLightGood = !isLightTooDark && !isLightTooBright
                val lightLabel = when {
                    isLightTooDark -> "too dark"
                    isLightTooBright -> "too bright"
                    else -> "soft and even"
                }

                // 2. Detect face and orientation
                val rotation = imageProxy.imageInfo.rotationDegrees
                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                val uprightWidth = if (rotation == 90 || rotation == 270) mediaImage.height else mediaImage.width
                val uprightHeight = if (rotation == 90 || rotation == 270) mediaImage.width else mediaImage.height

                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val guidance = evaluateFraming(
                            faces = faces,
                            uprightWidth = uprightWidth,
                            uprightHeight = uprightHeight,
                            targetAngle = targetAngle,
                            isFrontCamera = isFrontCamera,
                            lightLabel = lightLabel,
                            isLightGood = isLightGood,
                        )
                        onGuidanceUpdate(guidance)
                    }
                    .addOnFailureListener {
                        onGuidanceUpdate(
                            FramingGuidance(
                                headline = "Hold still",
                                lightLabel = lightLabel,
                                isLightGood = isLightGood,
                                angleLabel = "measuring…",
                                isAngleGood = false,
                                isCenteredAndReady = false,
                            )
                        )
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } catch (e: Throwable) {
                android.util.Log.e("HeadshotFramingEngine", "LiveAnalyzer frame analysis failed", e)
                imageProxy.close()
            }
        }

        private fun calculateLuminance(buffer: ByteBuffer): Int {
            buffer.rewind()
            val remaining = buffer.remaining()
            if (remaining == 0) return 128
            var sum = 0L
            var count = 0
            val step = maxOf(1, remaining / 300) // Sample ~300 pixels across the frame
            var i = 0
            while (i < remaining) {
                sum += (buffer.get(i).toInt() and 0xFF)
                count++
                i += step
            }
            return if (count > 0) (sum / count).toInt() else 128
        }
    }

    /**
     * Evaluates face bounding box, head angles (yaw/roll), and distance against headshot requirements.
     */
    fun evaluateFraming(
        faces: List<Face>,
        uprightWidth: Int,
        uprightHeight: Int,
        targetAngle: HeadshotAngle,
        isFrontCamera: Boolean,
        lightLabel: String,
        isLightGood: Boolean,
    ): FramingGuidance {
        if (faces.isEmpty() || uprightWidth <= 0 || uprightHeight <= 0) {
            return FramingGuidance(
                headline = "Step into the frame",
                lightLabel = lightLabel,
                isLightGood = isLightGood,
                angleLabel = if (targetAngle == HeadshotAngle.Front) "square" else "side on",
                isAngleGood = false,
                isCenteredAndReady = false,
                faceBoxNorm = null,
            )
        }

        val primaryFace = faces.maxByOrNull { (it.boundingBox.bottom - it.boundingBox.top) } ?: faces.first()
        val box = primaryFace.boundingBox

        return evaluateFramingMetrics(
            faceLeft = box.left,
            faceTop = box.top,
            faceRight = box.right,
            faceBottom = box.bottom,
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            yaw = primaryFace.headEulerAngleY,
            roll = primaryFace.headEulerAngleZ,
            targetAngle = targetAngle,
            isFrontCamera = isFrontCamera,
            lightLabel = lightLabel,
            isLightGood = isLightGood,
        )
    }

    fun evaluateFramingMetrics(
        faceLeft: Int,
        faceTop: Int,
        faceRight: Int,
        faceBottom: Int,
        uprightWidth: Int,
        uprightHeight: Int,
        yaw: Float,
        roll: Float,
        targetAngle: HeadshotAngle,
        isFrontCamera: Boolean,
        lightLabel: String,
        isLightGood: Boolean,
    ): FramingGuidance {
        val faceCenterX = (faceLeft + faceRight).toFloat() / 2f
        val faceCenterY = (faceTop + faceBottom).toFloat() / 2f
        val faceHeight = (faceBottom - faceTop).toFloat()

        val rawNormX = faceCenterX / uprightWidth
        val screenX = if (isFrontCamera) 1f - rawNormX else rawNormX
        val screenY = faceCenterY / uprightHeight
        val normHeight = faceHeight / uprightHeight

        val normRect = RectF(
            faceLeft.toFloat() / uprightWidth,
            faceTop.toFloat() / uprightHeight,
            faceRight.toFloat() / uprightWidth,
            faceBottom.toFloat() / uprightHeight,
        )

        val isAngleGood: Boolean
        val angleLabel: String
        var angleInstruction: String? = null

        when (targetAngle) {
            HeadshotAngle.Front -> {
                val yawOff = abs(yaw) > 16f
                val rollOff = abs(roll) > 15f
                if (yawOff) angleInstruction = "Face camera directly"
                else if (rollOff) angleInstruction = "Keep head level"
                isAngleGood = !yawOff && !rollOff
                angleLabel = if (isAngleGood) "square" else "turned away"
            }
            HeadshotAngle.Left -> {
                // Front camera: looking left from user's perspective
                val inRange = if (isFrontCamera) yaw in 16f..55f else yaw in -55f..-16f
                if (!inRange) {
                    angleInstruction = if ((isFrontCamera && yaw < 16f) || (!isFrontCamera && yaw > -16f)) "Turn head left" else "Turn slightly forward"
                }
                isAngleGood = inRange
                angleLabel = if (isAngleGood) "profile left" else "turn left"
            }
            HeadshotAngle.Right -> {
                val inRange = if (isFrontCamera) yaw in -55f..-16f else yaw in 16f..55f
                if (!inRange) {
                    angleInstruction = if ((isFrontCamera && yaw > -16f) || (!isFrontCamera && yaw < 16f)) "Turn head right" else "Turn slightly forward"
                }
                isAngleGood = inRange
                angleLabel = if (isAngleGood) "profile right" else "turn right"
            }
        }

        // Evaluate Positioning & Distance
        val headline: String
        val isFramingCentered: Boolean

        if (normHeight < 0.26f) {
            headline = "Move closer"
            isFramingCentered = false
        } else if (normHeight > 0.68f) {
            headline = "Step back"
            isFramingCentered = false
        } else if (screenX < 0.38f) {
            headline = "Move right"
            isFramingCentered = false
        } else if (screenX > 0.62f) {
            headline = "Move left"
            isFramingCentered = false
        } else if (screenY < 0.26f) {
            headline = "Raise camera"
            isFramingCentered = false
        } else if (screenY > 0.60f) {
            headline = "Lower camera"
            isFramingCentered = false
        } else if (angleInstruction != null) {
            headline = angleInstruction
            isFramingCentered = false
        } else {
            isFramingCentered = true
            headline = if (targetAngle == HeadshotAngle.Front) "Hold still — you’re centred" else "Hold still — that’s your profile"
        }

        val isReady = isFramingCentered && isAngleGood && isLightGood

        return FramingGuidance(
            headline = headline,
            lightLabel = lightLabel,
            isLightGood = isLightGood,
            angleLabel = angleLabel,
            isAngleGood = isAngleGood,
            isCenteredAndReady = isReady,
            faceBoxNorm = normRect,
        )
    }

    /**
     * Rotates captured JPEG upright according to Exif orientation, locates the face, and crops
     * to casting-standard 4:5 portrait ratio with natural headroom.
     */
    suspend fun processAndCropHeadshot(context: Context, rawUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val rawPath = rawUri.path ?: return@withContext null
        val rawFile = File(rawPath)
        if (!rawFile.exists()) return@withContext null

        return@withContext runCatching {
            // 1. Decode bitmap with orientation
            val exif = ExifInterface(rawFile.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            val originalBitmap = BitmapFactory.decodeFile(rawFile.absolutePath) ?: return@withContext null
            val uprightBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true).also {
                    if (it !== originalBitmap) originalBitmap.recycle()
                }
            } else {
                originalBitmap
            }

            // 2. Detect face in upright bitmap for optimal 4:5 centering
            val inputImage = InputImage.fromBitmap(uprightBitmap, 0)
            val faces = runCatching { Tasks.await(detector.process(inputImage)) }.getOrNull().orEmpty()
            val face = faces.maxByOrNull { it.boundingBox.height() }

            // 3. Compute 4:5 crop bounds
            val cropRect = compute4by5CropRect(
                bitmapWidth = uprightBitmap.width,
                bitmapHeight = uprightBitmap.height,
                faceBox = face?.boundingBox,
            )

            val croppedBitmap = Bitmap.createBitmap(
                uprightBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width,
                cropRect.height,
            )
            if (croppedBitmap !== uprightBitmap) uprightBitmap.recycle()

            // 4. Save cropped headshot to permanent storage
            val headshotsDir = File(context.filesDir, "headshots").apply { mkdirs() }
            val outputFile = File(headshotsDir, "headshot-${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            croppedBitmap.recycle()

            // Remove temporary uncropped capture
            if (rawFile.absolutePath != outputFile.absolutePath) {
                rawFile.delete()
            }

            outputFile.toUri()
        }.onFailure {
            android.util.Log.e("HeadshotFramingEngine", "Failed to auto-frame and crop headshot", it)
        }.getOrNull()
    }

    data class HeadshotCropRect(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
        fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
    }

    /**
     * Computes a 4:5 aspect ratio crop centered on the face with golden ratio headroom.
     */
    fun compute4by5CropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        faceBox: Rect? = null,
    ): HeadshotCropRect {
        if (faceBox == null || (faceBox.right <= faceBox.left) || (faceBox.bottom <= faceBox.top)) {
            val targetAspect = 4f / 5f
            val cropWidth: Int
            val cropHeight: Int
            if (bitmapWidth.toFloat() / bitmapHeight > targetAspect) {
                cropHeight = bitmapHeight
                cropWidth = (cropHeight * targetAspect).toInt()
            } else {
                cropWidth = bitmapWidth
                cropHeight = (cropWidth / targetAspect).toInt()
            }
            val left = (bitmapWidth - cropWidth) / 2
            val top = (bitmapHeight - cropHeight) / 2
            return HeadshotCropRect(left, top, cropWidth, cropHeight)
        }

        return compute4by5CropRect(
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            faceLeft = faceBox.left,
            faceTop = faceBox.top,
            faceRight = faceBox.right,
            faceBottom = faceBox.bottom,
        )
    }

    fun compute4by5CropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        faceLeft: Int,
        faceTop: Int,
        faceRight: Int,
        faceBottom: Int,
    ): HeadshotCropRect {
        val targetAspect = 4f / 5f
        val faceWidth = faceRight - faceLeft
        val faceHeight = faceBottom - faceTop
        val faceCenterX = (faceLeft + faceRight) / 2

        // For a professional headshot, face height is typically ~38% of total height
        var cropHeight = (faceHeight * 2.4f).toInt()
        var cropWidth = (cropHeight * targetAspect).toInt()

        // Clamp to bitmap boundaries
        if (cropHeight > bitmapHeight) {
            cropHeight = bitmapHeight
            cropWidth = (cropHeight * targetAspect).toInt()
        }
        if (cropWidth > bitmapWidth) {
            cropWidth = bitmapWidth
            cropHeight = (cropWidth / targetAspect).toInt()
        }

        // Center horizontally on the face
        val idealLeft = faceCenterX - (cropWidth / 2)
        val left = idealLeft.coerceIn(0, maxOf(0, bitmapWidth - cropWidth))

        // Position face in upper third with natural headroom (~40% of face height above top of head)
        val headroom = (faceHeight * 0.40f).toInt()
        val idealTop = faceTop - headroom
        val top = idealTop.coerceIn(0, maxOf(0, bitmapHeight - cropHeight))

        return HeadshotCropRect(left, top, cropWidth, cropHeight)
    }
}
