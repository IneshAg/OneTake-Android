package com.onetake.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.onetake.app.models.Audition
import com.onetake.app.models.PieceKind
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import java.io.File

/**
 * Tape check built from recorded files and their metadata —
 * instead of a fixed "11 checks passed" list. Each check is something measured: file metadata here,
 * and inside each scene's take the sound levels and on-device face framing ([TapeAnalysis]).
 * Blocking (reads file metadata); run it on Dispatchers.IO.
 */
object TapeChecker {
    data class Check(
        val title: String,
        val passed: Boolean,
        val detail: String,
        /** A clip to show when the actor opens this check, if one applies. */
        val fileUri: String? = null,
    )

    private class ClipInfo(val label: String, val uri: String, val durationMs: Long, val landscape: Boolean?, val hasAudio: Boolean)

    fun run(
        context: Context,
        audition: Audition,
        takes: List<Take>,
        headshots: Map<String, String>,
        slate: SlateInfo,
        slateAtStart: Boolean = true,
    ): List<Check> {
        val checks = mutableListOf<Check>()
        val ownTakes = takes.filter { it.auditionId == audition.id }
        val wantsSlate = audition.pieces.any { it.kind == PieceKind.Slate }
        val wantsFullLength = audition.pieces.any { it.kind == PieceKind.FullLength } || "Full length" in audition.asks

        if (audition.pieces.any { it.kind == PieceKind.Headshot }) {
            val ok = headshots.values.any { exists(it) }
            checks += Check("Headshot", ok, if (ok) "${headshots.size} of 3 angles saved" else "No headshot saved yet")
            if ("Both profiles" in audition.asks) {
                val hasProfiles = headshots.containsKey("left") && headshots.containsKey("right")
                checks += Check("Both profiles", hasProfiles, if (hasProfiles) "Left and right profiles saved" else "Casting asked for both profiles — add left & right in Profile")
            }
        }
        if (wantsSlate) {
            val ok = exists(audition.slateVideoUri)
            val slatePos = if (slateAtStart) "Opens the tape" else "Plays at end of tape"
            checks += Check("Slate recorded", ok, if (ok) slatePos else "Not recorded yet", audition.slateVideoUri)
            val details = slate.name.isNotBlank()
            checks += Check("Slate details", details, if (details) slate.spoken else "Add your name in Profile so the slate prompt has it")
            if (ok) {
                checks += takeChecks(context, "Slate", audition.slateVideoUri!!)
            }
        }
        if (wantsFullLength) {
            val ok = exists(audition.fullLengthVideoUri)
            checks += Check("Full length", ok, if (ok) "Recorded" else "Casting asked for one — not recorded yet", audition.fullLengthVideoUri)
            if (ok) {
                checks += takeChecks(context, "Full length", audition.fullLengthVideoUri!!)
            }
        }

        val clips = mutableListOf<ClipInfo>()
        if (exists(audition.slateVideoUri)) clipInfo(context, "Slate", audition.slateVideoUri!!)?.let(clips::add)
        if (exists(audition.fullLengthVideoUri)) clipInfo(context, "Full length", audition.fullLengthVideoUri!!)?.let(clips::add)

        val sceneCount = maxOf(1, audition.scenes.size)
        for (i in 0 until sceneCount) {
            val title = audition.scenes.getOrNull(i)?.title ?: "Scene ${i + 1}"
            val withFile = ownTakes.filter { (it.sceneIndex ?: 0) == i && exists(it.fileUri) }
            val chosen = withFile.firstOrNull { it.isCircled } ?: withFile.firstOrNull()
            checks += when {
                chosen == null -> Check(title, false, "No take with a video yet")
                chosen.isCircled -> Check(title, true, "Take ${chosen.number} circled", chosen.fileUri)
                else -> Check(title, true, "Take ${chosen.number} will be sent — none circled", chosen.fileUri)
            }
            if (chosen?.fileUri != null) {
                clipInfo(context, "Take ${chosen.number}", chosen.fileUri!!)?.let(clips::add)
                checks += takeChecks(context, "$title · take ${chosen.number}", chosen.fileUri!!)
            }
        }

        if (clips.isNotEmpty()) {
            val silent = clips.filterNot { it.hasAudio }
            checks += Check(
                "Sound",
                silent.isEmpty(),
                if (silent.isEmpty()) "Every clip has an audio track" else "No audio in: ${silent.joinToString { it.label }}",
                silent.firstOrNull()?.uri,
            )
            if ("Landscape" in audition.asks) {
                val portrait = clips.filter { it.landscape == false }
                checks += Check(
                    "Landscape",
                    portrait.isEmpty(),
                    if (portrait.isEmpty()) "Every clip is landscape" else "Shot upright: ${portrait.joinToString { it.label }}",
                    portrait.firstOrNull()?.uri,
                )
            }
            val totalMs = clips.sumOf { it.durationMs }
            if ("Under 3 min" in audition.asks) {
                checks += Check("Under 3 minutes", totalMs <= 180_000, "Tape runs ${formatMs(totalMs)}")
            }
        }
        return checks
    }

    /** Sound and framing inside a video clip ([TapeAnalysis]). */
    private fun takeChecks(context: Context, label: String, uri: String): List<Check> {
        val checks = mutableListOf<Check>()
        TapeAnalysis.sound(context, uri)?.let { sound ->
            checks += when {
                sound.loudestDb < -50 -> Check("Sound · $label", false, "Nothing audible — check the mic wasn't covered or muted", uri)
                sound.loudestDb < -32 -> Check("Sound · $label", false, "Your voice is very quiet — move closer to the phone or speak up", uri)
                else -> Check("Sound · $label", true, "Clear level", uri)
            }
            sound.clippedAtMs?.let { at ->
                checks += Check("Distortion · $label", false, "The sound distorts at ${formatMs(at)} — a little further from the mic", uri)
            }
        }
        TapeAnalysis.framing(context, uri)?.let { f ->
            val inFrame = f.withFace.toDouble() / maxOf(1, f.sampled)
            checks += if (inFrame >= 0.8) {
                Check("In frame · $label", true, "Your face is in shot throughout", uri)
            } else {
                Check("In frame · $label", false, "Out of shot from about ${formatMs(f.missingAtMs ?: 0)} — check your mark", uri)
            }
            if (f.withFace > 0) {
                checks += Check(
                    "Centred · $label",
                    f.centredShare >= 0.7,
                    if (f.centredShare >= 0.7) "Centred left to right" else "Off to one side for much of the take",
                    uri,
                )
                f.faceHeight?.let { h ->
                    checks += when {
                        h < 0.12 -> Check("Distance · $label", false, "You're far from the camera — casting wants a medium close-up", uri)
                        h > 0.6 -> Check("Distance · $label", false, "Very close — step back so your shoulders are in shot", uri)
                        else -> Check("Distance · $label", true, "Medium close-up", uri)
                    }
                }
            }
        }
        return checks
    }

    private fun exists(uri: String?): Boolean =
        uri != null && runCatching { Uri.parse(uri).path?.let { File(it).isFile } == true }.getOrDefault(false)

    private fun clipInfo(context: Context, label: String, uri: String): ClipInfo? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, Uri.parse(uri))
            fun meta(key: Int) = r.extractMetadata(key)
            val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            // Displayed orientation: a 90°/270° rotation swaps the stored width and height.
            val landscape = if (w == null || h == null) null else if (rotation % 180 == 0) w > h else h > w
            ClipInfo(
                label = label,
                uri = uri,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                landscape = landscape,
                hasAudio = meta(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
            )
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    private fun formatMs(ms: Long): String = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }
}
