package com.onetake.app.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.onetake.app.models.Audition
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Composes the real recorded pieces of an audition — slate, full-length, and the circled take for
 * each scene — into one exported .mp4, the way a real "package for delivery" step has to. There's
 * no casting-site API this app can submit the file to, so "send" is real local export + handing the
 * real file to the system share intent (another app, Drive, Bluetooth…), not a simulated server
 * round trip.
 *
 * Android has no AVFoundation-style composition engine, so this concatenates the already-encoded
 * samples of each clip directly with MediaExtractor/MediaMuxer, offsetting presentation timestamps
 * so the clips play back to back. This assumes the clips share compatible video/audio encoding
 * (true in practice here — every clip comes from the same CameraX `Recorder` configuration) rather
 * than re-encoding, which this sandbox has no way to verify against real device hardware.
 */
object MediaExporter {
    sealed class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class NothingToExport : ExportException("Nothing's been recorded yet for this audition.")
        class MuxerFailed(cause: Throwable) : ExportException(cause.message ?: "Couldn't put the export together.", cause)
    }

    fun deliverableFileName(audition: Audition, slate: SlateInfo): String {
        fun token(s: String) = s.uppercase().filter { it.isLetterOrDigit() }
        val parts = slate.name.trim().split(" ").filter { it.isNotBlank() }
        val last = parts.lastOrNull() ?: "ACTOR"
        val first = if (parts.size > 1) parts.first() else ""
        val pieces = listOf(token(last), token(first), token(audition.role), token(audition.project)).filter { it.isNotEmpty() }
        return (if (pieces.isEmpty()) "ONETAKE" else pieces.joinToString("_")) + ".mp4"
    }

    /** Blocking — callers should run this on Dispatchers.IO. Returns the exported file.
     *  [isCancelled] is polled between samples; when it turns true the partial file is deleted
     *  and a CancellationException is thrown. */
    fun export(
        context: Context,
        audition: Audition,
        takes: List<Take>,
        slate: SlateInfo,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {},
    ): File {
        // Only this audition's takes, whatever the caller passed.
        val ownTakes = takes.filter { it.auditionId == audition.id }
        val clipUris = buildList {
            audition.slateVideoUri?.let { add(it) }
            audition.fullLengthVideoUri?.let { add(it) }
            audition.scenes.indices.forEach { i ->
                val sceneTakes = ownTakes.filter { it.sceneIndex == i && it.fileUri != null }
                val chosen = sceneTakes.firstOrNull { it.isCircled } ?: sceneTakes.firstOrNull()
                chosen?.fileUri?.let { add(it) }
            }
            // Scenes may be empty on a bare-bones audition — fall back to any circled/first take.
            if (audition.scenes.isEmpty()) {
                val withFile = ownTakes.filter { it.fileUri != null }
                (withFile.firstOrNull { it.isCircled } ?: withFile.firstOrNull())?.fileUri?.let { add(it) }
            }
        }
        if (clipUris.isEmpty()) throw ExportException.NothingToExport()

        val fileName = deliverableFileName(audition, slate)
        // Per-audition folder: two auditions with the same role + project share a deliverable
        // name, and must not overwrite each other's tape.
        val outDir = File(MediaStore.directory(context, "exports"), audition.id).apply { mkdirs() }
        val outFile = File(outDir, fileName)
        if (outFile.exists()) outFile.delete()

        try {
            muxConcat(context, clipUris, outFile, isCancelled, onProgress)
        } catch (e: CancellationException) {
            outFile.delete()
            throw e
        } catch (e: ExportException) {
            outFile.delete()
            throw e
        } catch (e: Exception) {
            outFile.delete()
            throw ExportException.MuxerFailed(e)
        }
        return outFile
    }

    private fun muxConcat(
        context: Context,
        clipUris: List<String>,
        outFile: File,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val extractors = clipUris.map { uriString ->
            MediaExtractor().apply { setDataSource(context, Uri.parse(uriString), null) }
        }
        try {
            fun trackIndexOf(extractor: MediaExtractor, mimePrefix: String): Int? =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true
                }

            val videoTrackIndices = extractors.map { trackIndexOf(it, "video/") }
            val audioTrackIndices = extractors.map { trackIndexOf(it, "audio/") }
            val firstVideoClip = videoTrackIndices.indexOfFirst { it != null }
            val firstAudioClip = audioTrackIndices.indexOfFirst { it != null }
            if (firstVideoClip == -1 && firstAudioClip == -1) {
                throw ExportException.MuxerFailed(IllegalStateException("No usable video or audio track in any recorded clip"))
            }

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var started = false
            try {
                val muxerVideoTrack = if (firstVideoClip >= 0) {
                    val format = extractors[firstVideoClip].getTrackFormat(videoTrackIndices[firstVideoClip]!!)
                    // MediaMuxer ignores the source's rotation metadata unless it's set explicitly,
                    // so portrait recordings would otherwise play back sideways.
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                    }
                    muxer.addTrack(format)
                } else -1
                val muxerAudioTrack = if (firstAudioClip >= 0) {
                    muxer.addTrack(extractors[firstAudioClip].getTrackFormat(audioTrackIndices[firstAudioClip]!!))
                } else -1

                muxer.start()
                started = true
                // One shared offset for both tracks, advanced by each clip's full length, so audio
                // and video stay in sync from clip to clip and clips never overlap.
                var clipOffsetUs = 0L
                extractors.forEachIndexed { i, extractor ->
                    var clipEndUs = 0L
                    videoTrackIndices[i]?.let { idx ->
                        if (muxerVideoTrack != -1) {
                            extractor.selectTrack(idx)
                            clipEndUs = maxOf(clipEndUs, copyTrack(extractor, idx, muxer, muxerVideoTrack, clipOffsetUs, isCancelled))
                            extractor.unselectTrack(idx)
                        }
                    }
                    audioTrackIndices[i]?.let { idx ->
                        if (muxerAudioTrack != -1) {
                            extractor.selectTrack(idx)
                            clipEndUs = maxOf(clipEndUs, copyTrack(extractor, idx, muxer, muxerAudioTrack, clipOffsetUs, isCancelled))
                            extractor.unselectTrack(idx)
                        }
                    }
                    clipOffsetUs += clipEndUs
                    onProgress((i + 1).toFloat() / extractors.size)
                }
            } finally {
                if (started) runCatching { muxer.stop() }
                muxer.release()
            }
        } finally {
            extractors.forEach { runCatching { it.release() } }
        }
    }

    /** Copies every sample of one track into the muxer, offsetting presentation time so clips play
     *  back to back. Returns the track's length — its declared duration, or the last sample's time
     *  plus one sample interval — so the next clip starts after this one ends rather than on top
     *  of its last frame. */
    private fun copyTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        muxer: MediaMuxer,
        muxerTrack: Int,
        timeOffsetUs: Long,
        isCancelled: () -> Boolean,
    ): Long {
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val format = extractor.getTrackFormat(trackIndex)
        val maxInputSize = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(0)
        val buffer = ByteBuffer.allocate(maxOf(maxInputSize, 2 shl 20))
        val bufferInfo = MediaCodec.BufferInfo()
        var maxPts = 0L
        var lastPts = -1L
        var lastInterval = 0L
        while (true) {
            if (isCancelled()) throw CancellationException("Export cancelled")
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val pts = extractor.sampleTime
            bufferInfo.set(0, sampleSize, pts + timeOffsetUs, extractor.sampleFlags)
            muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
            if (lastPts >= 0 && pts > lastPts) lastInterval = pts - lastPts
            lastPts = pts
            maxPts = maxOf(maxPts, pts)
            if (!extractor.advance()) break
        }
        val declared = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        return maxOf(declared, maxPts + lastInterval)
    }
}
