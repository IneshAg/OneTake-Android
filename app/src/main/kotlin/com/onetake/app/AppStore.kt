package com.onetake.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.PieceKind
import com.onetake.app.models.PracticePiece
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import java.time.LocalDateTime

/**
 * App state — Android twin of iOS `AppStore` (@Observable). Compose snapshot state,
 * so any screen reading a property recomposes when it changes. No StateFlow plumbing needed.
 */
class AppStore : ViewModel() {
    var isOnboarded by mutableStateOf(false)
    var slate by mutableStateOf(SlateInfo())

    // Data
    var auditions by mutableStateOf(emptyList<Audition>())
    var takes by mutableStateOf(emptyList<Take>())
    var practice by mutableStateOf(emptyList<PracticePiece>())

    // Settings (iOS @AppStorage equivalents)
    var slateAtStart by mutableStateOf(true)
    var cleanNoise by mutableStateOf(true)
    var readerVolume by mutableStateOf("Quieter")
    var exportSpec by mutableStateOf("1080p, landscape")
    var headshotsTaken by mutableIntStateOf(0)
    var headshots by mutableStateOf<Map<String, String>>(emptyMap())
    var notifyMorning by mutableStateOf(true)
    var notifyTwoHours by mutableStateOf(true)
    var notifyCallbacks by mutableStateOf(true)
    var notifyMessages by mutableStateOf(true)
    var notifyDelivery by mutableStateOf(true)
    var notifyPractice by mutableStateOf(false)
    var liquidGlass by mutableStateOf(true)

    // MARK: Lookup

    fun audition(id: String): Audition? = auditions.firstOrNull { it.id == id }

    fun practicePiece(id: String): PracticePiece? = practice.firstOrNull { it.id == id }

    /** The audition Home pushes the actor toward: soonest due, not sent. */
    val nextPriority: Audition?
        get() = auditions.filter { it.status != AuditionStatus.Sent }.minByOrNull { it.due }

    val active: List<Audition>
        get() = auditions.filter { it.status != AuditionStatus.Sent }.sortedBy { it.due }

    val sent: List<Audition>
        get() = auditions.filter { it.status == AuditionStatus.Sent }

    // MARK: Mutations

    fun add(audition: Audition) {
        auditions = listOf(audition) + auditions
    }

    fun delete(id: String) {
        auditions = auditions.filterNot { it.id == id }
    }

    fun update(id: String, transform: (Audition) -> Audition) {
        auditions = auditions.map { if (it.id == id) transform(it) else it }
    }

    fun markDone(kind: PieceKind, auditionId: String, title: String? = null) = update(auditionId) { a ->
        val j = a.pieces.indexOfFirst { it.kind == kind && !it.isDone && (title == null || it.title == title) }
        val pieces = if (j >= 0) a.pieces.mapIndexed { i, p -> if (i == j) p.copy(isDone = true) else p } else a.pieces
        val updated = a.copy(pieces = pieces)
        if (updated.nextPiece == null) updated.copy(status = AuditionStatus.Ready) else updated
    }

    fun markSent(auditionId: String) = update(auditionId) {
        it.copy(status = AuditionStatus.Sent, sentDate = LocalDateTime.now())
    }

    fun setDestination(auditionId: String, destination: Destination) = update(auditionId) {
        it.copy(destination = destination)
    }

    fun circle(take: Take) {
        takes = takes.map {
            if (take.sceneId != null && it.sceneId != null) {
                if (it.sceneId == take.sceneId) it.copy(isCircled = it.id == take.id) else it
            } else {
                it.copy(isCircled = it.id == take.id)
            }
        }
    }

    fun addTake(take: Take) {
        takes = takes + take
    }

    fun addPractice(piece: PracticePiece) {
        practice = listOf(piece) + practice
    }

    fun saveHeadshot(angle: String, uri: String) {
        val updated = headshots.toMutableMap()
        updated[angle.lowercase()] = uri
        headshots = updated
        headshotsTaken = headshots.size
    }

    fun hasHeadshot(angle: String): Boolean = headshots.containsKey(angle.lowercase())

    fun headshot(angle: String): String? = headshots[angle.lowercase()]

    fun clearHeadshots() {
        headshots = emptyMap()
        headshotsTaken = 0
    }
}

val LocalAppStore = staticCompositionLocalOf<AppStore> { error("AppStore not provided") }
