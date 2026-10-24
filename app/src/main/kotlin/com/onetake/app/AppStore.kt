package com.onetake.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import com.onetake.app.data.DeadlineReminders
import com.onetake.app.data.PersistedState
import com.onetake.app.data.Persistence
import com.onetake.app.data.ScriptParser
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.PracticePiece
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import com.onetake.app.ui.system.ReaderFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

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

    // Persisted properties keep their Compose state in an explicit MutableState so the setter can
    // also schedule a save — a `by mutableStateOf` delegate can't have a custom setter.
    private val isOnboardedState = mutableStateOf(false)
    var isOnboarded: Boolean
        get() = isOnboardedState.value
        set(value) { isOnboardedState.value = value; persist() }
    private val slateState = mutableStateOf(SlateInfo())
    var slate: SlateInfo
        get() = slateState.value
        set(value) { slateState.value = value; persist() }

    // Data
    private val auditionsState = mutableStateOf(emptyList<Audition>())
    var auditions: List<Audition>
        get() = auditionsState.value
        set(value) { auditionsState.value = value; persist() }
    private val takesState = mutableStateOf(emptyList<Take>())
    var takes: List<Take>
        get() = takesState.value
        set(value) { takesState.value = value; persist() }
    private val practiceState = mutableStateOf(emptyList<PracticePiece>())
    var practice: List<PracticePiece>
        get() = practiceState.value
        set(value) { practiceState.value = value; persist() }

    // Settings that change behaviour — persisted with everything else.
    private val slateAtStartState = mutableStateOf(true)
    /** Whether the exported tape opens with the slate clip. */
    var slateAtStart: Boolean
        get() = slateAtStartState.value
        set(value) { slateAtStartState.value = value; persist() }
    private val readerVolumeState = mutableStateOf("Quieter")
    /** "Same as me" / "Quieter" / "Much quieter" — the reader's level under your takes. */
    var readerVolume: String
        get() = readerVolumeState.value
        set(value) { readerVolumeState.value = value; persist() }
    val readerVolumeLevel: Float
        get() = when (readerVolume) {
            "Same as me" -> 1f
            "Much quieter" -> 0.35f
            else -> 0.6f
        }
    var headshotsTaken by mutableIntStateOf(0)
    private val headshotsState = mutableStateOf<Map<String, String>>(emptyMap())
    var headshots: Map<String, String>
        get() = headshotsState.value
        set(value) { headshotsState.value = value; persist() }
    // Local deadline reminders (DeadlineReminders) — persisted, rescheduled on every save.
    private val notifyMorningState = mutableStateOf(true)
    var notifyMorning: Boolean
        get() = notifyMorningState.value
        set(value) { notifyMorningState.value = value; persist() }
    private val notifyTwoHoursState = mutableStateOf(true)
    var notifyTwoHours: Boolean
        get() = notifyTwoHoursState.value
        set(value) { notifyTwoHoursState.value = value; persist() }
    private val liquidGlassState = mutableStateOf(true)
    /** A notice shared from another app, waiting for Add audition to read it. Not persisted. */
    var incomingShare by mutableStateOf<com.onetake.app.data.SharedNotice?>(null)
    var liquidGlass: Boolean
        get() = liquidGlassState.value
        set(value) { liquidGlassState.value = value; persist() }

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
            slateAtStart = saved.slateAtStart
            readerVolume = saved.readerVolume
            liquidGlass = saved.liquidGlass
            notifyMorning = saved.notifyMorning
            notifyTwoHours = saved.notifyTwoHours
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
            slateAtStart = slateAtStart, readerVolume = readerVolume, liquidGlass = liquidGlass,
            notifyMorning = notifyMorning, notifyTwoHours = notifyTwoHours,
        )
        saveJob = saveScope.launch {
            delay(300)
            Persistence.save(context, snapshot)
            // Due dates, sends and the toggles all change which reminders should exist.
            runCatching { DeadlineReminders.reschedule(context, snapshot) }
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

    /** Creates the next round for [original]: same role and project, the chosen scenes, a fresh
     *  set of pieces to record, and casting's notes. With [shareReader] the scenes keep their ids,
     *  so the reader recordings carry over; otherwise they're new scenes with no reader track. */
    fun startCallback(
        original: Audition,
        due: LocalDateTime,
        sceneIds: Set<String>,
        notes: String,
        shareReader: Boolean,
    ): Audition {
        val scenes = original.scenes.filter { it.id in sceneIds }
            .map { if (shareReader) it else it.copy(id = UUID.randomUUID().toString()) }
        val pieces = buildList {
            original.pieces.firstOrNull { it.kind == PieceKind.Headshot }
                ?.let { add(it.copy(id = UUID.randomUUID().toString(), isDone = headshots.isNotEmpty())) }
            if (original.pieces.any { it.kind == PieceKind.Slate }) add(Piece(kind = PieceKind.Slate, title = "Slate", detail = "Name, height, city", isDone = false))
            scenes.forEach { add(Piece(kind = PieceKind.Scene, title = it.title, detail = "", isDone = false)) }
        }
        val callback = original.copy(
            id = UUID.randomUUID().toString(),
            due = due,
            pieces = pieces,
            scenes = scenes,
            status = AuditionStatus.Due,
            sentDate = null,
            slateVideoUri = null,
            fullLengthVideoUri = null,
            exportedFileUri = null,
            notes = notes.trim(),
        )
        add(callback)
        return callback
    }

    /** Deletes an audition along with everything recorded for it on this phone: its takes, slate,
     *  full length, export, reader recordings, and the practice pieces made from its scenes (and
     *  their practice takes). An export file another audition also points at is kept. */
    fun delete(id: String) {
        val audition = audition(id) ?: return
        val ownPractice = practice.filter { it.auditionId == id }
        auditions = auditions.filterNot { it.id == id }
        takes = takes.filterNot { it.auditionId == id }
        practice = practice.filterNot { it.auditionId == id }

        val ctx = context
        // A callback can share scenes (and so reader recordings) with the round it came from.
        val sceneIdsStillUsed = (auditions.flatMap { it.scenes } + practice.mapNotNull { it.scene }).map { it.id }.toSet()
        val exportPath = audition.exportedFileUri
            ?.takeIf { uri -> auditions.none { it.exportedFileUri == uri } }
            ?.let { runCatching { Uri.parse(it).path }.getOrNull() }
        saveScope.launch {
            runCatching {
                val dir = ctx.filesDir ?: return@runCatching
                File(dir, "audition-$id").deleteRecursively()
                File(File(dir, "exports"), id).deleteRecursively()
                exportPath?.let { File(it).delete() }
                ownPractice.forEach { File(dir, "practice-${it.id}").deleteRecursively() }
                audition.scenes.filter { it.id !in sceneIdsStillUsed }
                    .forEach { ReaderFiles.sceneDir(ctx, it.id).deleteRecursively() }
            }
        }
    }

    /** After Privacy's "Delete all local data" has removed the files: drop every take and media
     *  link, and un-tick the pieces whose recordings are gone so no audition still reads as Ready.
     *  Sent auditions keep their Sent status — what was delivered stays delivered. */
    fun resetAfterMediaWipe() {
        takes = emptyList()
        practice = practice.map { it.copy(takesRecorded = 0) }
        auditions = auditions.map { a ->
            val cleared = a.copy(slateVideoUri = null, fullLengthVideoUri = null, exportedFileUri = null)
            if (a.status == AuditionStatus.Sent) cleared
            else cleared.copy(status = AuditionStatus.Due, pieces = a.pieces.map { it.copy(isDone = false) })
        }
        clearHeadshots()
    }

    /** Edit details: the fields casting can change after the fact. A new role re-marks which lines
     *  are yours. */
    fun editDetails(
        id: String,
        project: String,
        role: String,
        castingOffice: String,
        due: LocalDateTime,
        destination: Destination,
        notes: String,
    ) = update(id) { a ->
        val newRole = role.trim().ifEmpty { a.role }
        val scenes = if (newRole == a.role) a.scenes else a.scenes.map { scene ->
            scene.copy(lines = scene.lines.map { it.copy(isMine = ScriptParser.isMyCue(it.character, newRole)) })
        }
        a.copy(
            project = project.trim().ifEmpty { a.project },
            role = newRole,
            castingOffice = castingOffice.trim(),
            due = due,
            destination = destination,
            notes = notes.trim(),
            scenes = scenes,
        )
    }

    /** Revised sides: the new scenes replace the old, and the scene pieces start again (the old
     *  takes stay, but the new pages need shooting). Practice pieces follow the new text. */
    fun replaceSides(id: String, scenes: List<ScriptScene>) {
        update(id) { a ->
            val pieces = a.pieces.filterNot { it.kind == PieceKind.Scene } +
                scenes.map { Piece(kind = PieceKind.Scene, title = it.title, detail = "Revised sides", isDone = false) }
            a.copy(scenes = scenes, pieces = pieces, status = if (a.status == AuditionStatus.Ready) AuditionStatus.Due else a.status)
        }
        practice = practice.map { p ->
            val scene = p.sceneIndex?.let { scenes.getOrNull(it) }
            if (p.auditionId != id || scene == null) p
            else {
                val mine = scene.lines.count { it.isMine }
                p.copy(scene = scene, linesTotal = mine, linesLearned = minOf(p.linesLearned, mine))
            }
        }
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

    /** The scene the camera should open on: the first whose checklist piece isn't done, else the
     *  first with no takes yet, else the first. */
    fun sceneToShoot(auditionId: String): Int {
        val a = audition(auditionId) ?: return 0
        val undone = a.pieces.filter { it.kind == PieceKind.Scene && !it.isDone }.map { it.title }.toSet()
        a.scenes.indexOfFirst { it.title in undone }.takeIf { it >= 0 }?.let { return it }
        a.scenes.indices.firstOrNull { i -> takes.none { it.auditionId == auditionId && it.sceneIndex == i } }?.let { return it }
        return 0
    }

    /** Next take number for a scene: one past the highest real take, so numbers never repeat after a
     *  take is removed (a count-based number would collide with an existing one). */
    fun nextTakeNumber(auditionId: String, sceneIndex: Int): Int =
        (takes.filter { it.auditionId == auditionId && it.sceneIndex == sceneIndex }.maxOfOrNull { it.number } ?: 0) + 1

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

    /** The practice piece Run Lines / Prepare track progress on: [ownerId] is a practice piece id
     *  (opened from Practice) or an audition id (that audition's scene). Null if neither exists. */
    fun practiceProgress(ownerId: String, sceneIndex: Int): PracticePiece? =
        practice.firstOrNull { it.id == ownerId }
            ?: practice.firstOrNull { it.auditionId == ownerId && it.sceneIndex == sceneIndex }

    /** Records a completed run: [learned] of your lines said without asking for help. */
    fun recordRun(ownerId: String, sceneIndex: Int, learned: Int, total: Int) {
        val piece = practiceProgress(ownerId, sceneIndex)
            ?: if (audition(ownerId) != null) practicePiece(ownerId, sceneIndex) else return
        practice = practice.map {
            if (it.id == piece.id) it.copy(linesLearned = learned.coerceIn(0, total), linesTotal = total) else it
        }
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
