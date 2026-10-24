package com.onetake.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.onetake.app.data.TapeAnalysis
import java.io.File
import java.io.InputStream

/**
 * Downsampled, orientation-correct image loading with a small shared memory cache.
 *
 * Headshots are full-resolution camera JPEGs (12 MP ≈ 48 MB as a bitmap) shown at avatar size,
 * and video thumbnails were full frames (a 4K frame ≈ 33 MB). Decoding at display size and caching
 * keeps scrolling and recomposition from re-decoding — and from running the app out of memory.
 */
object ImageLoading {
    /** Longest edge decoded, in pixels — plenty for any card or avatar in the app. */
    private const val MAX_EDGE = 1024

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** A photo from a file:// or content:// URI, downsampled and rotated per its EXIF tag
     *  (CameraX writes JPEGs with an orientation tag rather than rotated pixels). Blocking. */
    fun photo(context: Context, uriString: String): Bitmap? {
        val key = cacheKey(uriString)
        cache.get(key)?.let { return it }
        val uri = Uri.parse(uriString)
        fun open(): InputStream? =
            if (uri.scheme == "file" || uri.scheme == null) File(uri.path ?: uriString).takeIf { it.exists() }?.inputStream()
            else context.contentResolver.openInputStream(uri)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Bounds-only decode always returns null; the size lands in `bounds`.
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return null
            val degrees = open()?.use { orientationDegrees(ExifInterface(it)) } ?: 0
            val upright = if (degrees == 0) decoded else
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            upright.also { cache.put(key, it) }
        }.getOrNull()
    }

    /** A frame from a video, scaled to display size. Blocking. */
    fun videoFrame(context: Context, uriString: String): Bitmap? {
        val key = "frame:" + cacheKey(uriString)
        cache.get(key)?.let { return it }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(uriString))
            TapeAnalysis.frameAt(r, 0, maxSide = MAX_EDGE / 2)?.also { cache.put(key, it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** File URIs include the modification time, so a re-recorded clip under the same name (e.g.
     *  slate.mp4) never shows the old frame. */
    private fun cacheKey(uriString: String): String {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file" && uri.scheme != null) return uriString
        return uriString + "@" + File(uri.path ?: uriString).lastModified()
    }

    private fun orientationDegrees(exif: ExifInterface): Int = when (
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
