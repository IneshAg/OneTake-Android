package com.onetake.app.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Cuts a take down to a time range without re-encoding: the samples in range are copied straight
 * into a new file (MediaExtractor → MediaMuxer), so it's quick and loses no quality.
 *
 * Video can only start on a keyframe, so the cut starts at the last keyframe at or before the
 * requested start (usually well under a second earlier); audio starts at the same point so the two
 * stay in sync. Blocking — run it on Dispatchers.IO.
 */
object TakeTrimmer {
    /** The trimmed file and its real length. */
    data class Trimmed(val file: File, val durationMs: Long)

    fun trim(context: Context, source: Uri, startMs: Long, endMs: Long, dest: File): Trimmed? {
        if (endMs <= startMs) return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, source, null)
            val tracks = (0 until extractor.trackCount).filter { mimeOf(extractor, it).let { m -> m.startsWith("video/") || m.startsWith("audio/") } }
            if (tracks.isEmpty()) return null
            val video = tracks.firstOrNull { mimeOf(extractor, it).startsWith("video/") }
            val endUs = endMs * 1000

            // Where the cut really starts: the keyframe at or before the requested start.
            val cutUs = if (video == null) startMs * 1000 else {
                extractor.selectTrack(video)
                extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                extractor.sampleTime.coerceAtLeast(0).also { extractor.unselectTrack(video) }
            }

            val muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var started = false
            var lengthUs = 0L
            try {
                val outTracks = tracks.associateWith { i ->
                    val format = extractor.getTrackFormat(i)
                    // MediaMuxer drops the rotation unless it's set again, so portrait takes would turn sideways.
                    if (i == video && format.containsKey(MediaFormat.KEY_ROTATION)) {
                        muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                    }
                    muxer.addTrack(format)
                }
                muxer.start()
                started = true
                val info = MediaCodec.BufferInfo()
                for ((track, out) in outTracks) {
                    val format = extractor.getTrackFormat(track)
                    val maxInput = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(0)
                    val buffer = ByteBuffer.allocate(maxOf(maxInput, 1 shl 20))
                    extractor.selectTrack(track)
                    extractor.seekTo(cutUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val pts = extractor.sampleTime
                        // A little past the end before stopping, since frames needn't arrive in time order.
                        if (pts > endUs + 500_000) break
                        if (pts in cutUs..endUs) {
                            info.set(0, size, pts - cutUs, extractor.sampleFlags)
                            muxer.writeSampleData(out, buffer, info)
                            lengthUs = maxOf(lengthUs, pts - cutUs)
                        }
                        if (!extractor.advance()) break
                    }
                    extractor.unselectTrack(track)
                }
            } finally {
                if (started) runCatching { muxer.stop() }
                muxer.release()
            }
            if (lengthUs <= 0) { dest.delete(); null } else Trimmed(dest, lengthUs / 1000)
        } catch (e: Exception) {
            android.util.Log.e("TakeTrimmer", "Trim failed", e)
            dest.delete()
            null
        } finally {
            extractor.release()
        }
    }

    private fun mimeOf(extractor: MediaExtractor, track: Int): String =
        extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME).orEmpty()
}
