package com.onetake.app.models

import java.util.UUID

enum class PieceKind(val displayName: String) {
    HEADSHOT("Headshot"),
    SLATE("Slate"),
    FULL_LENGTH("Full length"),
    SCENE("Scene")
}

data class Piece(
    val id: String = UUID.randomUUID().toString(),
    val kind: PieceKind,
    val title: String,
    val detail: String,
    val isDone: Boolean = false
)

data class ScriptLine(
    val id: String = UUID.randomUUID().toString(),
    val character: String,
    val text: String,
    val isMine: Boolean
)

data class ScriptScene(
    val id: String = UUID.randomUUID().toString(),
    val number: Int,
    val title: String,
    val heading: String,
    val lines: List<ScriptLine>,
    val estimatedSeconds: Int
)

enum class AuditionStatus {
    DUE, READY, SENT
}

enum class Destination(val displayName: String, val spec: String) {
    ACTORS_ACCESS("Actors Access", "H.264 .mp4 · 1080p · under 500 MB"),
    CASTING_NETWORKS("Casting Networks", "H.264 .mp4 · 1080p · up to 300 MB"),
    SPOTLIGHT("Spotlight", "H.264 .mp4 · 1080p"),
    EMAIL_OR_LINK("Email or link", "Private link · 30 days")
}

data class Audition(
    val id: String = UUID.randomUUID().toString(),
    val project: String,
    val projectType: String,
    val role: String,
    val roleSize: String,
    val castingOffice: String,
    val dueEpochMs: Long,
    val asks: List<String>,
    val pieces: List<Piece>,
    val scenes: List<ScriptScene> = emptyList(),
    val destination: Destination = Destination.ACTORS_ACCESS,
    val status: AuditionStatus = AuditionStatus.DUE,
    val sentDateEpochMs: Long? = null,
    val headshotAsset: String = "Headshot"
) {
    val readyCount: Int get() = pieces.count { it.isDone }
    val totalCount: Int get() = pieces.size
    val nextPiece: Piece? get() = pieces.firstOrNull { !it.isDone }

    val nextAction: String
        get() {
            if (status == AuditionStatus.SENT) return "Sent · link active"
            val next = nextPiece ?: return "Watch it once, then send"
            return when (next.kind) {
                PieceKind.HEADSHOT -> "Take your headshot"
                PieceKind.SLATE -> "Record the slate"
                PieceKind.FULL_LENGTH -> "Record the full-length shot"
                PieceKind.SCENE -> "Record ${next.title}"
            }
        }

    val hoursLeft: Int
        get() {
            val diffMs = dueEpochMs - System.currentTimeMillis()
            return (diffMs / (1000 * 60 * 60)).coerceAtLeast(0).toInt()
        }
}

data class Take(
    val id: String = UUID.randomUUID().toString(),
    val number: Int,
    val durationSeconds: Int,
    val recordedAtEpochMs: Long,
    val isCircled: Boolean,
    val notes: List<String>,
    val videoUri: String? = null
)

data class TapeFinding(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val title: String,
    val where: String,
    val advice: String
)

data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val whenDay: String,
    val time: String,
    val title: String,
    val detail: String
)

data class PracticePiece(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String,
    val linesLearned: Int,
    val linesTotal: Int
)

data class SlateInfo(
    val name: String = "Kabir Sharma",
    val height: String = "5′ 11″",
    val basedIn: String = "Mumbai",
    val representation: String = ""
) {
    val spoken: String get() = "$name. $height. $basedIn."
}
