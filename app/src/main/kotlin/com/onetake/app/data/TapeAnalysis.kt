package com.onetake.app.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * What Tape check measures inside a take, on the phone: the sound (decoded to PCM, in half-second
 * windows) and the framing (a frame about every second through ML Kit face detection). Both are
 * blocking — run on Dispatchers.IO — and cached per file, since Tape check re-runs whenever takes
 * change and a take's analysis doesn't.
 */
object TapeAnalysis {
    data class Sound(
        /** The loudest half second, in dBFS. Speech near the phone sits well above -32. */
        val loudestDb: Double,
        /** Where the audio first distorts (samples at full scale), if it does. */
        val clippedAtMs: Long?,
    )

    data class Framing(
        val sampled: Int,
        val withFace: Int,
        /** First sampled moment with no face, if any. */
        val missingAtMs: Long?,
        /** Share of face frames where the face is roughly centred left to right. */
        val centredShare: Double,
        /** Median face height as a share of the frame height (≈0.2–0.45 for a medium close-up). */
        val faceHeight: Double?,
    )

    private val soundCache = ConcurrentHashMap<String, Sound>()
    private val framingCache = ConcurrentHashMap<String, Framing>()

    /** Same file, same size, same modified time → same result. A trimmed take is a new file. */
    private fun cacheKey(uri: String): String? = Uri.parse(uri).path?.let(::File)?.takeIf { it.isFile }
        ?.let { "${it.path}|${it.length()}|${it.lastModified()}" }

    fun sound(context: Context, uri: String): Sound? {
        val key = cacheKey(uri) ?: return null
        soundCache[key]?.let { return it }
        return analyseSound(context, Uri.parse(uri))?.also { soundCache[key] = it }
    }

    fun framing(context: Context, uri: String): Framing? {
        val key = cacheKey(uri) ?: return null
        framingCache[key]?.let { return it }
        return analyseFraming(context, Uri.parse(uri))?.also { framingCache[key] = it }
    }

    private fun analyseSound(context: Context, uri: Uri): Sound? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var loudest = -120.0
            var clippedAtMs: Long? = null
            var windowSum = 0.0
            var windowCount = 0
            var windowClipped = 0
            var windowIndex = 0L
            fun closeWindow() {
                if (windowCount == 0) return
                val rms = sqrt(windowSum / windowCount)
                loudest = maxOf(loudest, if (rms > 0) 20 * log10(rms) else -120.0)
                if (clippedAtMs == null && windowClipped.toDouble() / windowCount > 0.001) clippedAtMs = windowIndex * 500
                windowIndex++
                windowSum = 0.0; windowCount = 0; windowClipped = 0
            }
            fun add(sample: Double) {
                windowSum += sample * sample
                if (kotlin.math.abs(sample) >= 0.99) windowClipped++
                windowCount++
                if (windowCount >= maxOf(1, sampleRate * channels / 2)) closeWindow()
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            // Ten minutes is far longer than any take; stop there rather than decode forever.
            val limitUs = 10L * 60 * 1_000_000
            while (true) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > limitUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoder.outputFormat
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outIndex >= 0) {
                    val out = decoder.getOutputBuffer(outIndex)!!.apply {
                        position(info.offset)
                        limit(info.offset + info.size)
                        order(ByteOrder.nativeOrder())
                    }
                    if (floatPcm) {
                        val floats = out.asFloatBuffer()
                        while (floats.hasRemaining()) add(floats.get().toDouble())
                    } else {
                        val shorts = out.asShortBuffer()
                        while (shorts.hasRemaining()) add(shorts.get() / 32768.0)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            closeWindow()
            Sound(loudest, clippedAtMs)
        } catch (e: Exception) {
            android.util.Log.w("TapeAnalysis", "Couldn't analyse sound", e)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** A frame at [timeUs], no larger than [maxSide] on its longest edge. getScaledFrameAtTime is API
     *  27+, and the app supports 26. */
    fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long, maxSide: Int): android.graphics.Bitmap? {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            return retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
        }
        val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val scale = maxSide.toFloat() / maxOf(full.width, full.height)
        if (scale >= 1f) return full
        return android.graphics.Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
            .also { if (it !== full) full.recycle() }
    }

    private fun analyseFraming(context: Context, uri: Uri): Framing? {
        val retriever = MediaMetadataRetriever()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build(),
        )
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            // About a frame a second, at most 30, skipping the first half second (walking in).
            val step = maxOf(1000L, durationMs / 30)
            var sampled = 0
            var withFace = 0
            var centred = 0
            var missingAt: Long? = null
            val heights = mutableListOf<Double>()
            var t = 500L
            while (t < durationMs) {
                val frame = frameAt(retriever, t * 1000, maxSide = 640)
                if (frame != null) {
                    // Frames usually come back upright already; only rotate one that is still in the
                    // sensor's orientation.
                    val needsRotation = rotation % 180 != 0 && frame.width > frame.height
                    val image = InputImage.fromBitmap(frame, if (needsRotation) rotation else 0)
                    val uprightWidth = if (needsRotation) frame.height else frame.width
                    val uprightHeight = if (needsRotation) frame.width else frame.height
                    val faces = runCatching { Tasks.await(detector.process(image)) }.getOrNull()
                    sampled++
                    val face = faces?.maxByOrNull { it.boundingBox.height() }
                    if (face == null) {
                        if (missingAt == null) missingAt = t
                    } else {
                        withFace++
                        val centreX = face.boundingBox.exactCenterX().toDouble() / uprightWidth
                        if (centreX in 0.3..0.7) centred++
                        heights += face.boundingBox.height().toDouble() / uprightHeight
                    }
                    frame.recycle()
                }
                t += step
            }
            if (sampled == 0) return null
            Framing(
                sampled = sampled,
                withFace = withFace,
                missingAtMs = missingAt,
                centredShare = if (withFace == 0) 0.0 else centred.toDouble() / withFace,
                faceHeight = heights.sorted().getOrNull(heights.size / 2),
            )
        } catch (e: Exception) {
            android.util.Log.w("TapeAnalysis", "Couldn't analyse framing", e)
            null
        } finally {
            runCatching { retriever.release() }
            detector.close()
        }
    }
}
