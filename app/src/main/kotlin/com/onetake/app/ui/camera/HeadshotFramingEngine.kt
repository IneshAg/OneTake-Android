package com.onetake.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
 * Real-time framing, lighting, and angle analysis for headshots, plus face-centered or manual 4:5 portrait cropping.
 * Powers live dynamic guidance in HeadshotFlowScreen and ensures captured or imported headshots match industry 4:5.
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
        val faceBoxNorm: NormalizedBox? = null,
    )

    data class ExifTransform(
        val rotationDegrees: Float,
        val flipHorizontal: Boolean,
    )

    private val detector by lazy {
        // ACCURATE + tracking keeps 30-55 degree Left/Right profile head turns locked without dropouts,
        // while CLASSIFICATION_MODE_NONE avoids wasted smile/blink neural inference.
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Resolves all 8 EXIF orientation constants (including Samsung front-camera TRANSPOSE/TRANSVERSE)
     * plus optional selfie horizontal mirroring.
     */
    fun resolveExifTransform(
        exifOrientation: Int,
        extraHorizontalMirror: Boolean = false,
    ): ExifTransform {
        val (deg, exifFlipH) = when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to true
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f to false
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to true
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f to true
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f to false
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f to true
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f to false
            else -> 0f to false
        }
        return ExifTransform(
            rotationDegrees = deg,
            flipHorizontal = exifFlipH xor extraHorizontalMirror,
        )
    }

    /**
     * CameraX ImageAnalysis.Analyzer that inspects live camera frames for face centering, head angle,
     * and scene lighting. Runs on a background executor and dispatches UI updates on the main thread.
     */
    class LiveAnalyzer(
        @Volatile var targetAngle: HeadshotAngle,
        @Volatile var isFrontCamera: Boolean,
        @Volatile var viewAspect: Float? = null,
        private val onGuidanceUpdate: (FramingGuidance) -> Unit,
    ) : ImageAnalysis.Analyzer {

        private val mainHandler = Handler(Looper.getMainLooper())
        private var lastAnalyzedMs = 0L

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val now = System.currentTimeMillis()
            if (now - lastAnalyzedMs < 85L) {
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
                val luma = calculateLuminance(imageProxy.planes[0].buffer).toInt()
                val isLightTooDark = luma < 45
                val isLightTooBright = luma > 218
                val isLightGood = !isLightTooDark && !isLightTooBright
                val lightLabel = when {
                    isLightTooDark -> "too dark"
                    isLightTooBright -> "too bright"
                    else -> "soft and even"
                }

                val rotation = imageProxy.imageInfo.rotationDegrees
                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                val uprightWidth = if (rotation == 90 || rotation == 270) mediaImage.height else mediaImage.width
                val uprightHeight = if (rotation == 90 || rotation == 270) mediaImage.width else mediaImage.height
                val angleSnapshot = targetAngle
                val frontSnapshot = isFrontCamera
                val aspectSnapshot = viewAspect

                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val guidance = evaluateFraming(
                            faces = faces,
                            uprightWidth = uprightWidth,
                            uprightHeight = uprightHeight,
                            targetAngle = angleSnapshot,
                            isFrontCamera = frontSnapshot,
                            lightLabel = lightLabel,
                            isLightGood = isLightGood,
                            viewAspect = aspectSnapshot,
                        )
                        mainHandler.post { onGuidanceUpdate(guidance) }
                    }
                    .addOnFailureListener {
                        val fallback = FramingGuidance(
                            headline = "Hold still",
                            lightLabel = lightLabel,
                            isLightGood = isLightGood,
                            angleLabel = "measuring…",
                            isAngleGood = false,
                            isCenteredAndReady = false,
                        )
                        mainHandler.post { onGuidanceUpdate(fallback) }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } catch (t: Throwable) {
                android.util.Log.e("HeadshotFramingEngine", "LiveAnalyzer frame analysis failed", t)
                imageProxy.close()
            }
        }

        private fun calculateLuminance(buffer: ByteBuffer): Double =
            LiveTapeCheckEngine.calculateLuminance(buffer)
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
        viewAspect: Float? = null,
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

        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: faces.first()
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
            viewAspect = viewAspect,
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
        viewAspect: Float? = null,
    ): FramingGuidance {
        if (uprightWidth <= 0 || uprightHeight <= 0) {
            return FramingGuidance(
                headline = "Step into the frame",
                lightLabel = lightLabel,
                isLightGood = isLightGood,
                angleLabel = "square",
                isAngleGood = false,
                isCenteredAndReady = false,
                faceBoxNorm = null,
            )
        }

        val visibleBox = LiveTapeCheckEngine.sensorToVisibleScreenBox(
            faceLeft = faceLeft.toFloat(),
            faceTop = faceTop.toFloat(),
            faceRight = faceRight.toFloat(),
            faceBottom = faceBottom.toFloat(),
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            isFrontCamera = isFrontCamera,
            viewAspect = viewAspect,
        )

        val screenX = visibleBox.centerX
        val screenY = visibleBox.centerY
        val normHeight = visibleBox.height

        val isAngleGood: Boolean
        val angleLabel: String
        var angleInstruction: String? = null

        when (targetAngle) {
            HeadshotAngle.Front -> {
                val yawOff = abs(yaw) > 15f
                val rollOff = abs(roll) > 14f
                if (yawOff) angleInstruction = "Face camera directly"
                else if (rollOff) angleInstruction = "Keep head level"
                isAngleGood = !yawOff && !rollOff
                angleLabel = if (isAngleGood) "square" else "turned away"
            }
            HeadshotAngle.Left -> {
                val inRange = if (isFrontCamera) yaw in 15f..58f else yaw in -58f..-15f
                if (!inRange) {
                    angleInstruction = if ((isFrontCamera && yaw < 15f) || (!isFrontCamera && yaw > -15f)) {
                        "Turn head left"
                    } else {
                        "Turn slightly forward"
                    }
                }
                isAngleGood = inRange
                angleLabel = if (isAngleGood) "profile left" else "turn left"
            }
            HeadshotAngle.Right -> {
                val inRange = if (isFrontCamera) yaw in -58f..-15f else yaw in 15f..58f
                if (!inRange) {
                    angleInstruction = if ((isFrontCamera && yaw > -15f) || (!isFrontCamera && yaw < 15f)) {
                        "Turn head right"
                    } else {
                        "Turn slightly forward"
                    }
                }
                isAngleGood = inRange
                angleLabel = if (isAngleGood) "profile right" else "turn right"
            }
        }

        val headline: String
        val isFramingCentered: Boolean

        if (normHeight < 0.25f) {
            headline = "Move closer"
            isFramingCentered = false
        } else if (normHeight > 0.65f) {
            headline = "Step back"
            isFramingCentered = false
        } else if (screenX < 0.39f) {
            headline = "Move right"
            isFramingCentered = false
        } else if (screenX > 0.61f) {
            headline = "Move left"
            isFramingCentered = false
        } else if (screenY < 0.27f) {
            headline = "Raise camera"
            isFramingCentered = false
        } else if (screenY > 0.59f) {
            headline = "Lower camera"
            isFramingCentered = false
        } else if (angleInstruction != null) {
            headline = angleInstruction
            isFramingCentered = false
        } else {
            isFramingCentered = true
            headline = if (targetAngle == HeadshotAngle.Front) {
                "Hold still — you’re centred"
            } else {
                "Hold still — that’s your profile"
            }
        }

        val isReady = isFramingCentered && isAngleGood && isLightGood

        return FramingGuidance(
            headline = headline,
            lightLabel = lightLabel,
            isLightGood = isLightGood,
            angleLabel = angleLabel,
            isAngleGood = isAngleGood,
            isCenteredAndReady = isReady,
            faceBoxNorm = visibleBox,
        )
    }

    /**
     * Decodes an upright Bitmap from either a local file:// URI or a content:// gallery URI,
     * applying EXIF orientation, optional selfie mirroring, and extra user 90-degree rotation.
     */
    suspend fun loadUprightHeadshotBitmap(
        context: Context,
        sourceUri: Uri,
        mirrorHorizontally: Boolean = false,
        extraRotationDegrees: Int = 0,
        maxDimension: Int = 2400,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val exifOrientation = if (sourceUri.scheme == "file" || sourceUri.scheme == null) {
                val path = sourceUri.path ?: return@withContext null
                ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            }

            // Bounds-only pass to prevent OOM on 48MP+ camera roll photos
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (sourceUri.scheme == "file" || sourceUri.scheme == null) {
                val path = sourceUri.path ?: return@withContext null
                BitmapFactory.decodeFile(path, boundsOpts)
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
            }
            var sampleSize = 1
            val longest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = if (sourceUri.scheme == "file" || sourceUri.scheme == null) {
                val path = sourceUri.path ?: return@withContext null
                BitmapFactory.decodeFile(path, decodeOpts)
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
            } ?: return@withContext null

            val transform = resolveExifTransform(exifOrientation, mirrorHorizontally)
            val totalRotation = ((transform.rotationDegrees + extraRotationDegrees) % 360f + 360f) % 360f
            if (totalRotation == 0f && !transform.flipHorizontal) {
                rawBitmap
            } else {
                val matrix = Matrix().apply {
                    if (totalRotation != 0f) postRotate(totalRotation)
                    if (transform.flipHorizontal) postScale(-1f, 1f)
                }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                    if (it !== rawBitmap) rawBitmap.recycle()
                }
            }
        }.onFailure {
            android.util.Log.e("HeadshotFramingEngine", "Failed to decode upright headshot bitmap", it)
        }.getOrNull()
    }

    /**
     * Rotates captured or imported photo upright, applies auto face-centered or manual pan/zoom 4:5 portrait crop,
     * and saves to permanent headshots storage.
     */
    suspend fun processAndCropHeadshot(
        context: Context,
        rawUri: Uri,
        mirrorHorizontally: Boolean = false,
        extraRotationDegrees: Int = 0,
        manualZoom: Float? = null,
        manualPanXNorm: Float = 0f,
        manualPanYNorm: Float = 0f,
        deleteRawIfTempFile: Boolean = true,
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val uprightBitmap = loadUprightHeadshotBitmap(
                context = context,
                sourceUri = rawUri,
                mirrorHorizontally = mirrorHorizontally,
                extraRotationDegrees = extraRotationDegrees,
            ) ?: return@withContext null

            val cropRect = if (manualZoom != null) {
                computeManual4by5CropRect(
                    bitmapWidth = uprightBitmap.width,
                    bitmapHeight = uprightBitmap.height,
                    zoom = manualZoom,
                    panXNorm = manualPanXNorm,
                    panYNorm = manualPanYNorm,
                )
            } else {
                val inputImage = InputImage.fromBitmap(uprightBitmap, 0)
                val faces = runCatching { Tasks.await(detector.process(inputImage)) }.getOrNull().orEmpty()
                val face = faces.maxByOrNull { it.boundingBox.height() }
                compute4by5CropRect(
                    bitmapWidth = uprightBitmap.width,
                    bitmapHeight = uprightBitmap.height,
                    faceBox = face?.boundingBox,
                )
            }

            val croppedBitmap = Bitmap.createBitmap(
                uprightBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width,
                cropRect.height,
            )
            if (croppedBitmap !== uprightBitmap) uprightBitmap.recycle()

            val headshotsDir = File(context.filesDir, "headshots").apply { mkdirs() }
            val outputFile = File(headshotsDir, "headshot-${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            croppedBitmap.recycle()

            if (deleteRawIfTempFile && (rawUri.scheme == "file" || rawUri.scheme == null)) {
                val rawFile = rawUri.path?.let(::File)
                if (rawFile != null && rawFile.exists() && rawFile.absolutePath != outputFile.absolutePath) {
                    rawFile.delete()
                }
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
     * Computes a manual 4:5 crop rect given zoom (>= 1.0f) and normalized pan offsets [-1f..+1f].
     */
    fun computeManual4by5CropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        zoom: Float = 1f,
        panXNorm: Float = 0f,
        panYNorm: Float = 0f,
    ): HeadshotCropRect {
        val targetAspect = 4f / 5f
        val safeW = bitmapWidth.coerceAtLeast(4)
        val safeH = bitmapHeight.coerceAtLeast(5)
        val baseWidth: Int
        val baseHeight: Int
        if (safeW.toFloat() / safeH > targetAspect) {
            baseHeight = safeH
            baseWidth = (baseHeight * targetAspect).toInt().coerceAtLeast(4)
        } else {
            baseWidth = safeW
            baseHeight = (baseWidth / targetAspect).toInt().coerceAtLeast(5)
        }
        val clampedZoom = zoom.coerceIn(1f, 3.5f)
        val cropWidth = (baseWidth / clampedZoom).toInt().coerceIn(4, safeW)
        val cropHeight = (baseHeight / clampedZoom).toInt().coerceIn(5, safeH)

        val maxLeft = (safeW - cropWidth).coerceAtLeast(0)
        val maxTop = (safeH - cropHeight).coerceAtLeast(0)
        val centerLeft = maxLeft * 0.5f
        val centerTop = maxTop * 0.5f

        val left = (centerLeft + panXNorm.coerceIn(-1f, 1f) * centerLeft).toInt().coerceIn(0, maxLeft)
        val top = (centerTop + panYNorm.coerceIn(-1f, 1f) * centerTop).toInt().coerceIn(0, maxTop)
        return HeadshotCropRect(left, top, cropWidth, cropHeight)
    }

    /**
     * Computes a 4:5 aspect ratio crop centered on the face with natural headroom.
     */
    fun compute4by5CropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        faceBox: Rect? = null,
    ): HeadshotCropRect {
        if (faceBox == null || (faceBox.right <= faceBox.left) || (faceBox.bottom <= faceBox.top)) {
            return computeManual4by5CropRect(bitmapWidth, bitmapHeight, zoom = 1f, panXNorm = 0f, panYNorm = 0f)
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
        val faceHeight = faceBottom - faceTop
        val faceCenterX = (faceLeft + faceRight) / 2

        var cropHeight = (faceHeight * 2.4f).toInt()
        var cropWidth = (cropHeight * targetAspect).toInt()

        if (cropHeight > bitmapHeight) {
            cropHeight = bitmapHeight
            cropWidth = (cropHeight * targetAspect).toInt()
        }
        if (cropWidth > bitmapWidth) {
            cropWidth = bitmapWidth
            cropHeight = (cropWidth / targetAspect).toInt()
        }
        cropWidth = cropWidth.coerceIn(1, bitmapWidth.coerceAtLeast(1))
        cropHeight = cropHeight.coerceIn(1, bitmapHeight.coerceAtLeast(1))

        val idealLeft = faceCenterX - (cropWidth / 2)
        val left = idealLeft.coerceIn(0, maxOf(0, bitmapWidth - cropWidth))

        val headroom = (faceHeight * 0.40f).toInt()
        val idealTop = faceTop - headroom
        val top = idealTop.coerceIn(0, maxOf(0, bitmapHeight - cropHeight))

        return HeadshotCropRect(left, top, cropWidth, cropHeight)
    }
}
