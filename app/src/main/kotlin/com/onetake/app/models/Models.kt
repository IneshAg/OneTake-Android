package com.onetake.app.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

// Mirrors iOS Models.swift one-to-one so both apps share the same states and copy.

enum class PieceKind(val icon: ImageVector) {
    Headshot(Icons.Outlined.AccountBox),
    Slate(Icons.Outlined.Badge),
    FullLength(Icons.Outlined.AccessibilityNew),
    Scene(Icons.Outlined.Movie),
}

data class Piece(
    val id: String = UUID.randomUUID().toString(),
    val kind: PieceKind,
    val title: String,
    val detail: String,
    val isDone: Boolean,
)

data class ScriptLine(
    val id: String = UUID.randomUUID().toString(),
    val character: String,
    val text: String,
    val isMine: Boolean,
)

data class ScriptScene(
    val id: String = UUID.randomUUID().toString(),
    val number: Int,
    val title: String,
    val heading: String,
    val lines: List<ScriptLine>,
    val estimatedSeconds: Int,
)

enum class AuditionStatus { Due, Ready, Sent }

enum class Destination(val label: String, val spec: String) {
    ActorsAccess("Actors Access", "H.264 .mp4 · 1080p · under 500 MB"),
    CastingNetworks("Casting Networks", "H.264 .mp4 · 1080p · up to 300 MB"),
    Spotlight("Spotlight", "H.264 .mp4 · 1080p"),
    EmailOrLink("Email or link", "Private link · 30 days"),
}

data class Audition(
    val id: String = UUID.randomUUID().toString(),
    val project: String,
    val projectType: String,
    val role: String,
    val roleSize: String,
    val castingOffice: String,
    val due: LocalDateTime,
    val asks: List<String>,
    val pieces: List<Piece>,
    val scenes: List<ScriptScene>,
    val destination: Destination,
    val status: AuditionStatus,
    val sentDate: LocalDateTime? = null,
    val headshotAsset: String = "headshot",
) {
    val readyCount: Int get() = pieces.count { it.isDone }
    val totalCount: Int get() = pieces.size
    val nextPiece: Piece? get() = pieces.firstOrNull { !it.isDone }

    /** The single "Next:" line used on Home and on audition rows. */
    val nextAction: String
        get() {
            if (status == AuditionStatus.Sent) return "Sent · link active"
            val next = nextPiece ?: return "Watch it once, then send"
            return when (next.kind) {
                PieceKind.Headshot -> "Take your headshot"
                PieceKind.Slate -> "Record the slate"
                PieceKind.FullLength -> "Record the full-length shot"
                PieceKind.Scene -> "Record ${next.title}"
            }
        }

    val hoursLeft: Int get() = maxOf(0, Duration.between(LocalDateTime.now(), due).toHours().toInt())
}

data class Take(
    val id: String = UUID.randomUUID().toString(),
    val number: Int,
    val durationSeconds: Int,
    val recordedAt: LocalDateTime,
    val isCircled: Boolean,
    val notes: List<String>,
    val fileUri: String? = null,
    val sceneId: String? = null,
)

data class TapeFinding(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val title: String,
    val where: String,
    val advice: String,
)

data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val whenLabel: String,
    val time: String,
    val title: String,
    val detail: String,
)

data class PracticePiece(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String,
    val linesLearned: Int,
    val linesTotal: Int,
    val character: String = "",
    val scene: ScriptScene? = null,
)

data class SlateInfo(
    val name: String = "",
    val height: String = "",
    val basedIn: String = "",
    val representation: String = "",
) {
    val spoken: String
        get() = listOf(name, height, basedIn).filter { it.isNotBlank() }.joinToString(". ").let { if (it.isNotBlank()) "$it." else "" }
}

// MARK: - Date formatting helpers (same output as iOS `Date.otDue` / `otDayMonth`)

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/** "Fri, 10:00 AM" */
val LocalDateTime.otDue: String
    get() = "${dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)}, ${format(timeFmt)}"

/** "20 Sep" */
val LocalDateTime.otDayMonth: String get() = format(dayMonthFmt)

/** "FRI" */
val LocalDateTime.otWeekdayCaps: String get() = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase()

/** "6:00 PM" */
val LocalDateTime.otTime: String get() = format(timeFmt)

/** "Tomorrow, 2:30 PM" / "Today, 6:00 PM" / "Fri, 10:00 AM" */
val LocalDateTime.otRelative: String
    get() {
        val days = Duration.between(LocalDateTime.now().toLocalDate().atStartOfDay(), toLocalDate().atStartOfDay()).toDays()
        return when (days) {
            0L -> "Today, ${format(timeFmt)}"
            1L -> "Tomorrow, ${format(timeFmt)}"
            else -> otDue
        }
    }

/** "1:28" */
fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
