package com.onetake.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.media.FaceDetector
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object HeadshotFramingEngine {

    val VOICE_TRIGGER_REGEX = Regex("""(?i)\b(now|take|shoot|capture|cheese|ready)\b""")

    data class FaceCenter(val x: Float, val y: Float)

    data class CropRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
    }

    /**
     * Calculates the 4:5 headshot crop rectangle centered on [faceCenter] (if detected),
     * or positioned with upper-third golden ratio framing as a fallback.
     */
    fun calculateCropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        faceCenter: FaceCenter?,
        targetAspect: Float = 4f / 5f,
    ): CropRect {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "Invalid bitmap dimensions" }

        val imageAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val cropWidth: Int
        val cropHeight: Int

        if (imageAspect > targetAspect) {
            // Image is wider than 4:5 -> height-constrained
            cropHeight = bitmapHeight
            cropWidth = (bitmapHeight * targetAspect).toInt().coerceAtMost(bitmapWidth)
        } else {
            // Image is narrower than 4:5 -> width-constrained
            cropWidth = bitmapWidth
            cropHeight = (bitmapWidth / targetAspect).toInt().coerceAtMost(bitmapHeight)
        }

        val left: Int
        val top: Int

        if (faceCenter != null) {
            // Center horizontally on the face
            val targetLeft = (faceCenter.x - cropWidth / 2f).toInt()
            left = targetLeft.coerceIn(0, bitmapWidth - cropWidth)

            // In headshot portraits, eyes/face sit at ~38% of the vertical frame (upper third)
            val targetTop = (faceCenter.y - cropHeight * 0.38f).toInt()
            top = targetTop.coerceIn(0, bitmapHeight - cropHeight)
        } else {
            // Fallback: Horizontal center, upper-third vertical framing
            left = (bitmapWidth - cropWidth) / 2
            val targetTop = ((bitmapHeight - cropHeight) * 0.35f).toInt()
            top = targetTop.coerceIn(0, bitmapHeight - cropHeight)
        }

        return CropRect(
            left = left,
            top = top,
            right = left + cropWidth,
            bottom = top + cropHeight,
        )
    }

    /**
     * Loads the captured photo, corrects Exif orientation, detects face coordinates,
     * crops to 4:5 aspect ratio, and saves the framed image.
     */
    fun frameAndCropImage(
        context: Context,
        inputUri: Uri,
        targetAspect: Float = 4f / 5f,
    ): Uri {
        return runCatching {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(inputUri)
            val exif = inputStream?.use { ExifInterface(it) }

            // Safe in-memory decoding with bounds checking to avoid OOM on large sensors
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(inputUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var inSampleSize = 1
            val maxDimension = 3000
            while (options.outWidth / inSampleSize > maxDimension || options.outHeight / inSampleSize > maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }

            val rawBitmap = contentResolver.openInputStream(inputUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return inputUri

            // Correct orientation from Exif
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val uprightBitmap = if (!matrix.isIdentity) {
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                    if (it != rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            // Detect faces using native FaceDetector
            val faceCenter = detectFaceCenter(uprightBitmap)

            // Calculate 4:5 crop rect
            val cropRect = calculateCropRect(uprightBitmap.width, uprightBitmap.height, faceCenter, targetAspect)

            val croppedBitmap = Bitmap.createBitmap(
                uprightBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width,
                cropRect.height,
            )

            if (croppedBitmap != uprightBitmap) {
                uprightBitmap.recycle()
            }

            // Save cropped image to cache/files dir
            val outputFile = File(context.filesDir, "headshot_${UUID.randomUUID()}.jpg")
            FileOutputStream(outputFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            croppedBitmap.recycle()

            Uri.fromFile(outputFile)
        }.getOrDefault(inputUri)
    }

    private fun detectFaceCenter(bitmap: Bitmap): FaceCenter? {
        return runCatching {
            // FaceDetector requires even width and RGB_565 format
            val evenWidth = if (bitmap.width % 2 == 0) bitmap.width else bitmap.width - 1
            val evenHeight = bitmap.height
            val scale = (evenWidth.toFloat() / 600f).coerceAtLeast(1f)
            val smallWidth = (evenWidth / scale).toInt().let { if (it % 2 == 0) it else it - 1 }
            val smallHeight = (evenHeight / scale).toInt()

            val small565 = Bitmap.createScaledBitmap(bitmap, smallWidth, smallHeight, true).copy(Bitmap.Config.RGB_565, false)
            val detector = FaceDetector(smallWidth, smallHeight, 1)
            val faces = arrayOfNulls<FaceDetector.Face>(1)
            val numFaces = detector.findFaces(small565, faces)
            small565.recycle()

            if (numFaces > 0 && faces[0] != null) {
                val point = PointF()
                faces[0]!!.getMidPoint(point)
                FaceCenter(point.x * scale, point.y * scale)
            } else {
                null
            }
        }.getOrNull()
    }
}
