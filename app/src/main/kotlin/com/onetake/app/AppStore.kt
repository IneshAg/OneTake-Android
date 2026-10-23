package com.onetake.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import com.onetake.app.data.Persistence
import com.onetake.app.data.PersistedState
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.PieceKind
import com.onetake.app.models.PracticePiece
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * App state — Android twin of iOS `AppStore` (@Observable). Compose snapshot state,
 * so any screen reading a property recomposes when it changes. Persisted to disk (debounced)
 * on every mutation so none of this is lost on relaunch — real data, not a reset-on-kill demo.
 */
class AppStore(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()

    /** True while init is still applying a loaded snapshot, so that pass doesn't immediately
     *  re-trigger its own save. */
    private var isLoading = true
    private var saveJob: Job? = null

    /** Self-managed instead of `viewModelScope`: `viewModelScope` resolves to
     *  `Dispatchers.Main.immediate`, which throws `IllegalStateException` in a plain JVM unit
     *  test (no Robolectric/Main-dispatcher rule) the moment any persisted property is set.
     *  Cancelled in `onCleared()` below. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var isOnboarded by mutableStateOf(false)
        set(value) { field = value; persist() }
    var slate by mutableStateOf(SlateInfo())
        set(value) { field = value; persist() }

    // Data
    var auditions by mutableStateOf(emptyList<Audition>())
        set(value) { field = value; persist() }
    var takes by mutableStateOf(emptyList<Take>())
        set(value) { field = value; persist() }
    var practice by mutableStateOf(emptyList<PracticePiece>())
        set(value) { field = value; persist() }

    // Settings (iOS @AppStorage equivalents) — not yet persisted to the state file; local-session only.
    var slateAtStart by mutableStateOf(true)
    var cleanNoise by mutableStateOf(true)
    var readerVolume by mutableStateOf("Quieter")
    var exportSpec by mutableStateOf("1080p, landscape")
    var headshotsTaken by mutableIntStateOf(0)
    var headshots by mutableStateOf<Map<String, String>>(emptyMap())
        set(value) { field = value; persist() }
    var notifyMorning by mutableStateOf(true)
    var notifyTwoHours by mutableStateOf(true)
    var notifyCallbacks by mutableStateOf(true)
    var notifyMessages by mutableStateOf(true)
    var notifyDelivery by mutableStateOf(true)
    var notifyPractice by mutableStateOf(false)
    var liquidGlass by mutableStateOf(true)

    init {
        val saved = Persistence.load(context)
        if (saved != null) {
            auditions = saved.auditions
            takes = saved.takes
            practice = saved.practice
            slate = saved.slate
            isOnboarded = saved.isOnboarded
            headshots = saved.headshots
            headshotsTaken = saved.headshots.size
        }
        isLoading = false
    }

    /** Debounced so rapid edits don't hit disk on every single state change. */
    private fun persist() {
        if (isLoading) return
        saveJob?.cancel()
        val snapshot = PersistedState(
            auditions = auditions, takes = takes, practice = practice,
            slate = slate, isOnboarded = isOnboarded, headshots = headshots,
        )
        saveJob = saveScope.launch {
            delay(300)
            Persistence.save(context, snapshot)
        }
    }

    override fun onCleared() {
        saveScope.cancel()
        super.onCleared()
    }

    // MARK: Lookup

    fun audition(id: String): Audition? = auditions.firstOrNull { it.id == id }

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

    fun setSlateVideo(auditionId: String, uri: String) = update(auditionId) {
        it.copy(slateVideoUri = uri)
    }

    fun setFullLengthVideo(auditionId: String, uri: String) = update(auditionId) {
        it.copy(fullLengthVideoUri = uri)
    }

    fun setExportedFile(auditionId: String, uri: String) = update(auditionId) {
        it.copy(exportedFileUri = uri)
    }

    /** Circles `take` and un-circles every other take in the *same* audition+scene — scoped, so
     *  circling a take in one audition no longer silently un-circles another audition's send pick. */
    fun circle(take: Take) {
        takes = takes.map {
            if (it.auditionId == take.auditionId && it.sceneIndex == take.sceneIndex) it.copy(isCircled = it.id == take.id) else it
        }
    }

    /** Next take number for a scene — real takes only, so numbering reflects what's actually on disk. */
    fun nextTakeNumber(auditionId: String, sceneIndex: Int): Int =
        takes.count { it.auditionId == auditionId && it.sceneIndex == sceneIndex } + 1

    /** Appends a real take, circling it automatically if it's the first for that scene (so there's
     *  always a sensible send pick without the actor having to remember to circle one). */
    fun addTake(take: Take) {
        val isFirstForScene = take.auditionId != null && take.sceneIndex != null &&
            takes.none { it.auditionId == take.auditionId && it.sceneIndex == take.sceneIndex }
        takes = takes + if (isFirstForScene) take.copy(isCircled = true) else take
    }

    fun updateTake(id: String, transform: (Take) -> Take) {
        takes = takes.map { if (it.id == id) transform(it) else it }
    }

    fun addPractice(piece: PracticePiece) {
        practice = listOf(piece) + practice
    }

    fun incrementPracticeTakes(id: String) {
        practice = practice.map { if (it.id == id) it.copy(takesRecorded = it.takesRecorded + 1) else it }
    }

    /** Finds or creates the PracticePiece for a given audition scene, so Quick Drills and the
     *  "pick up where you left off" card have something real to point at. */
    fun practicePiece(auditionId: String, sceneIndex: Int): PracticePiece {
        practice.firstOrNull { it.auditionId == auditionId && it.sceneIndex == sceneIndex }?.let { return it }
        val audition = audition(auditionId)
        val scene = audition?.scenes?.getOrNull(sceneIndex)
        val piece = if (audition == null || scene == null) {
            PracticePiece(title = "Practice", detail = "", linesLearned = 0, linesTotal = 0)
        } else {
            val mine = scene.lines.count { it.isMine }
            PracticePiece(
                title = scene.title, detail = "${audition.project} · ${audition.role}",
                linesLearned = 0, linesTotal = mine, character = audition.role, scene = scene,
                auditionId = auditionId, sceneIndex = sceneIndex,
            )
        }
        practice = listOf(piece) + practice
        return piece
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
