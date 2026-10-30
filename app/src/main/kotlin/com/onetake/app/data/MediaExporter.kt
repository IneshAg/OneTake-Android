package com.onetake.app.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.onetake.app.models.Audition
import com.onetake.app.models.PieceKind
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

    fun deliverablePrefix(audition: Audition, slate: SlateInfo): String {
        fun token(s: String) = s.uppercase().filter { it.isLetterOrDigit() }
        val parts = slate.name.trim().split(" ").filter { it.isNotBlank() }
        val last = parts.lastOrNull() ?: "ACTOR"
        val first = if (parts.size > 1) parts.first() else ""
        val pieces = listOf(token(last), token(first), token(audition.role), token(audition.project)).filter { it.isNotEmpty() }
        return if (pieces.isEmpty()) "ONETAKE" else pieces.joinToString("_")
    }

    fun deliverableFileName(audition: Audition, slate: SlateInfo): String =
        deliverablePrefix(audition, slate) + ".mp4"

    fun deliverableZipFileName(audition: Audition, slate: SlateInfo): String =
        deliverablePrefix(audition, slate) + ".zip"

    data class PackageEntryInfo(
        val name: String,
        val kind: String,
        val sizeBytes: Long,
        val uri: String?,
        val isReady: Boolean,
    )

    fun uriToFile(uriString: String?): File? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            if (uriString.startsWith("file://")) {
                File(uriString.substring("file://".length))
            } else if (uriString.startsWith("/")) {
                File(uriString)
            } else {
                Uri.parse(uriString).path?.let(::File)
            }
        }.getOrNull()
    }

    fun inspectPackageManifest(
        context: Context,
        audition: Audition,
        takes: List<Take>,
        slate: SlateInfo,
        headshots: Map<String, String>,
    ): List<PackageEntryInfo> {
        val prefix = deliverablePrefix(audition, slate)
        val ownTakes = takes.filter { it.auditionId == audition.id }
        val items = mutableListOf<PackageEntryInfo>()

        // 1. Slate
        val wantsSlate = audition.pieces.any { it.kind == PieceKind.Slate }
        if (wantsSlate || audition.slateVideoUri != null) {
            val file = uriToFile(audition.slateVideoUri)
            val len = file?.takeIf { it.isFile }?.length() ?: 0L
            items += PackageEntryInfo(
                name = "${prefix}_01_SLATE.mp4",
                kind = "Slate Video",
                sizeBytes = len,
                uri = audition.slateVideoUri,
                isReady = len > 0L,
            )
        }

        // 2. Full Length
        val wantsFull = audition.pieces.any { it.kind == PieceKind.FullLength } || "Full length" in audition.asks
        if (wantsFull || audition.fullLengthVideoUri != null) {
            val file = uriToFile(audition.fullLengthVideoUri)
            val len = file?.takeIf { it.isFile }?.length() ?: 0L
            items += PackageEntryInfo(
                name = "${prefix}_02_FULL_LENGTH.mp4",
                kind = "Full Length Video",
                sizeBytes = len,
                uri = audition.fullLengthVideoUri,
                isReady = len > 0L,
            )
        }

        // 3. Scene takes
        val sceneCount = maxOf(1, audition.scenes.size)
        for (i in 0 until sceneCount) {
            val scene = audition.scenes.getOrNull(i)
            val sceneNum = scene?.number ?: (i + 1)
            val sceneTakes = ownTakes.filter { (it.sceneIndex ?: 0) == i && it.fileUri != null }
            val chosen = sceneTakes.firstOrNull { it.isCircled } ?: sceneTakes.firstOrNull()
            val file = uriToFile(chosen?.fileUri)
            val len = file?.takeIf { it.isFile }?.length() ?: 0L
            items += PackageEntryInfo(
                name = "${prefix}_SCENE_${sceneNum}.mp4",
                kind = scene?.title ?: "Scene $sceneNum",
                sizeBytes = len,
                uri = chosen?.fileUri,
                isReady = len > 0L,
            )
        }

        // 4. Headshots
        listOf("front" to "FRONT", "left" to "LEFT", "right" to "RIGHT").forEach { (angle, cap) ->
            val uriStr = headshots[angle]
            if (uriStr != null || angle == "front") {
                val file = uriToFile(uriStr)
                val len = file?.takeIf { it.isFile }?.length() ?: 0L
                items += PackageEntryInfo(
                    name = "${prefix}_HEADSHOT_${cap}.jpg",
                    kind = "$angle Headshot",
                    sizeBytes = len,
                    uri = uriStr,
                    isReady = len > 0L,
                )
            }
        }

        return items
    }

    fun existingFullTapeFile(context: Context, audition: Audition, slate: SlateInfo): File? {
        val fileName = deliverableFileName(audition, slate)
        val outDir = File(MediaStore.directory(context, "exports"), audition.id)
        val outFile = File(outDir, fileName)
        return if (outFile.exists()) outFile else null
    }

    fun existingZipFile(context: Context, audition: Audition, slate: SlateInfo): File? {
        val fileName = deliverableZipFileName(audition, slate)
        val outDir = File(MediaStore.directory(context, "exports"), audition.id)
        val outFile = File(outDir, fileName)
        return if (outFile.exists()) outFile else null
    }

    /** Exports the complete audition package as a .zip file containing cleanly named MP4 clips,
     *  headshot JPEGs, an audition manifest file, and an optional combined full-tape MP4.
     *  Blocking — run on Dispatchers.IO. */
    fun exportZipPackage(
        context: Context,
        audition: Audition,
        takes: List<Take>,
        slate: SlateInfo,
        headshots: Map<String, String>,
        includeCombinedTape: Boolean = true,
        slateAtStart: Boolean = true,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {},
    ): File {
        val manifest = inspectPackageManifest(context, audition, takes, slate, headshots)
        val validItems = manifest.filter { it.isReady && it.uri != null }
        if (validItems.isEmpty()) throw ExportException.NothingToExport()

        val fileName = deliverableZipFileName(audition, slate)
        val outDir = File(MediaStore.directory(context, "exports"), audition.id).apply { mkdirs() }
        val outFile = File(outDir, fileName)
        if (outFile.exists()) outFile.delete()

        val tempCombinedFile = if (includeCombinedTape && validItems.count { it.kind.endsWith("Video") || it.name.endsWith(".mp4") } >= 2) {
            runCatching {
                export(context, audition, takes, slate, isCancelled = isCancelled, includeSlate = slateAtStart)
            }.getOrNull()
        } else null

        val prefix = deliverablePrefix(audition, slate)
        val totalSteps = validItems.size + 1 + (if (tempCombinedFile != null) 1 else 0)
        var step = 0

        try {
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(outFile).buffered()).use { zipOut ->
                // Write MANIFEST.txt
                val manifestEntry = java.util.zip.ZipEntry("MANIFEST.txt")
                zipOut.putNextEntry(manifestEntry)
                val manifestContent = buildString {
                    appendLine("ONETAKE AUDITION PACKAGE")
                    appendLine("========================")
                    appendLine("Project: ${audition.project}")
                    appendLine("Role: ${audition.role}")
                    appendLine("Actor: ${slate.name}")
                    if (slate.representation.isNotBlank()) appendLine("Representation: ${slate.representation}")
                    if (slate.height.isNotBlank()) appendLine("Height: ${slate.height}")
                    if (slate.basedIn.isNotBlank()) appendLine("Location: ${slate.basedIn}")
                    appendLine("Date: ${java.time.LocalDateTime.now()}")
                    appendLine("Destination: ${audition.destination.label}")
                    appendLine()
                    appendLine("PACKAGE CONTENTS:")
                    validItems.forEach { item ->
                        appendLine("- ${item.name} (${item.sizeBytes / 1024} KB) - ${item.kind}")
                    }
                    if (tempCombinedFile != null) {
                        appendLine("- ${prefix}_FULL_TAPE.mp4 (${tempCombinedFile.length() / 1024} KB) - Complete Assembled Tape")
                    }
                }
                zipOut.write(manifestContent.toByteArray())
                zipOut.closeEntry()
                step++
                onProgress(step.toFloat() / totalSteps)

                // Write valid media files
                val buffer = ByteArray(64 * 1024)
                for (item in validItems) {
                    if (isCancelled()) throw CancellationException("Export cancelled")
                    val file = uriToFile(item.uri)
                    if (file != null && file.exists()) {
                        val entry = java.util.zip.ZipEntry(item.name)
                        zipOut.putNextEntry(entry)
                        java.io.FileInputStream(file).use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                zipOut.write(buffer, 0, read)
                            }
                        }
                        zipOut.closeEntry()
                    }
                    step++
                    onProgress(step.toFloat() / totalSteps)
                }

                // If combined tape exists, add it as well
                if (tempCombinedFile != null && tempCombinedFile.exists()) {
                    if (isCancelled()) throw CancellationException("Export cancelled")
                    val entry = java.util.zip.ZipEntry("${prefix}_FULL_TAPE.mp4")
                    zipOut.putNextEntry(entry)
                    java.io.FileInputStream(tempCombinedFile).use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            zipOut.write(buffer, 0, read)
                        }
                    }
                    zipOut.closeEntry()
                    step++
                    onProgress(step.toFloat() / totalSteps)
                }
            }
        } catch (e: Exception) {
            outFile.delete()
            throw e
        } finally {
            tempCombinedFile?.delete()
        }

        return outFile
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
        includeSlate: Boolean = true,
        onProgress: (Float) -> Unit = {},
    ): File {
        // Only this audition's takes, whatever the caller passed.
        val ownTakes = takes.filter { it.auditionId == audition.id }
        val clipUris = buildList {
            if (includeSlate) audition.slateVideoUri?.let { add(it) }
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
