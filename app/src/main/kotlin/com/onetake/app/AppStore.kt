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
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.SlateInfo
import com.onetake.app.models.Take
import com.onetake.app.ui.system.ReaderFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    /** The snapshot waiting on the debounce, if any — written at once if the app closes first. */
    @Volatile private var pendingSave: PersistedState? = null
    /** One write at a time, in order: a cancelled save already writing finishes before the next. */
    private val writeLock = Mutex()
    /** What reminders and the widget were last built from (guarded by [writeLock]). */
    private var scheduledFrom: Triple<List<Audition>, Boolean, Boolean>? = null

    /** Self-managed instead of `viewModelScope`: `viewModelScope` resolves to
     *  `Dispatchers.Main.immediate`, which throws `IllegalStateException` in a plain JVM unit
     *  test (no Robolectric/Main-dispatcher rule) the moment any persisted property is set.
     *  Not cancelled with the ViewModel, so a last save still lands (see `onCleared()`). */
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
    /** An audition to open, from the home-screen widget. Not persisted. */
    var openAuditionRequest by mutableStateOf<String?>(null)
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
        pendingSave = snapshot
        saveJob = saveScope.launch {
            delay(300)
            write(snapshot)
        }
    }

    private suspend fun write(snapshot: PersistedState) = writeLock.withLock {
        // Once started, a write runs to the end even if a newer edit cancels its job (the newer
        // save waits for the lock), so the widget is never left behind the saved state.
        withContext(NonCancellable) {
            Persistence.save(context, snapshot)
            if (pendingSave === snapshot) pendingSave = null
            // Reminders and the home-screen widget follow the auditions (due dates, sends, progress)
            // and the reminder toggles. Most saves — takes, practice, the slate — change neither, and
            // rescheduling every alarm and redrawing the widget for them was wasted work.
            val inputs = Triple(snapshot.auditions, snapshot.notifyMorning, snapshot.notifyTwoHours)
            if (inputs != scheduledFrom) {
                scheduledFrom = inputs
                runCatching { DeadlineReminders.reschedule(context, snapshot) }
                com.onetake.app.widget.NextAuditionWidget.refresh(context)
            }
        }
    }

    override fun onCleared() {
        // Write whatever is still waiting on the debounce now. Cancelling the scope here dropped
        // the last edit whenever the app was closed within 300 ms of it. Nothing else runs in the
        // scope, so it's done once this write is.
        pendingSave?.let { snapshot ->
            saveJob?.cancel()
            saveJob = saveScope.launch { write(snapshot) }
        }
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

    /** Deletes an audition along with everything recorded for it: its takes, slate,
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
            exportedFileUri = null,
        )
    }

    /** Extended details editing: supports projectType, roleSize, minute-accurate due date, deliverables/asks syncing, and status. */
    fun editDetailsExtended(
        id: String,
        project: String,
        role: String,
        projectType: String,
        roleSize: String,
        castingOffice: String,
        due: LocalDateTime,
        destination: Destination,
        notes: String,
        asks: List<String>,
        status: AuditionStatus,
    ) = update(id) { a ->
        val newRole = role.trim().ifEmpty { a.role }
        val scenes = if (newRole == a.role) a.scenes else a.scenes.map { scene ->
            scene.copy(lines = scene.lines.map { it.copy(isMine = ScriptParser.isMyCue(it.character, newRole)) })
        }
        val wantsFullLength = asks.any { "full" in it.lowercase() }
        var pieces = a.pieces
        if (wantsFullLength && pieces.none { it.kind == PieceKind.FullLength }) {
            val slateIdx = pieces.indexOfLast { it.kind == PieceKind.Slate }
            val insertIdx = if (slateIdx >= 0) slateIdx + 1 else 0
            val fullLengthPiece = Piece(
                kind = PieceKind.FullLength,
                title = "Full-length shot",
                detail = "Head to feet",
                isDone = !a.fullLengthVideoUri.isNullOrBlank(),
            )
            pieces = pieces.toMutableList().apply { add(insertIdx, fullLengthPiece) }
        } else if (!wantsFullLength && pieces.any { it.kind == PieceKind.FullLength }) {
            pieces = pieces.filterNot { it.kind == PieceKind.FullLength }
        }

        val wantsSlate = asks.any { "slate" in it.lowercase() || "ident" in it.lowercase() }
        if (wantsSlate && pieces.none { it.kind == PieceKind.Slate }) {
            val slatePiece = Piece(
                kind = PieceKind.Slate,
                title = "Slate",
                detail = "Name, height, city",
                isDone = !a.slateVideoUri.isNullOrBlank(),
            )
            pieces = listOf(slatePiece) + pieces
        } else if (!wantsSlate && asks.isNotEmpty() && pieces.any { it.kind == PieceKind.Slate }) {
            pieces = pieces.filterNot { it.kind == PieceKind.Slate }
        }

        a.copy(
            project = project.trim().ifEmpty { a.project },
            role = newRole,
            projectType = projectType.trim().ifEmpty { a.projectType },
            roleSize = roleSize.trim().ifEmpty { a.roleSize },
            castingOffice = castingOffice.trim(),
            due = due,
            destination = destination,
            notes = notes.trim(),
            scenes = scenes,
            asks = asks,
            pieces = pieces,
            status = status,
            exportedFileUri = null,
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
        it.copy(slateVideoUri = uri, exportedFileUri = null)
    }

    fun setFullLengthVideo(auditionId: String, uri: String) = update(auditionId) {
        it.copy(fullLengthVideoUri = uri, exportedFileUri = null)
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
        take.auditionId?.let { audId ->
            update(audId) { it.copy(exportedFileUri = null) }
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
        take.auditionId?.let { audId ->
            update(audId) { it.copy(exportedFileUri = null) }
        }
    }

    fun updateTake(id: String, transform: (Take) -> Take) {
        val take = takes.firstOrNull { it.id == id }
        takes = takes.map { if (it.id == id) transform(it) else it }
        take?.auditionId?.let { audId ->
            update(audId) { it.copy(exportedFileUri = null) }
        }
    }

    fun deleteTake(id: String) {
        val take = takes.firstOrNull { it.id == id } ?: return
        val audId = take.auditionId
        val scIdx = take.sceneIndex
        val fileToDelete = take.fileUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }?.let(::File)

        val remaining = takes.filterNot { it.id == id }
        val updatedTakes = if (take.isCircled && audId != null && scIdx != null) {
            val sameSceneTakes = remaining.filter { it.auditionId == audId && it.sceneIndex == scIdx }
            if (sameSceneTakes.isNotEmpty() && sameSceneTakes.none { it.isCircled }) {
                val promoteId = sameSceneTakes.last().id
                remaining.map { if (it.id == promoteId) it.copy(isCircled = true) else it }
            } else {
                remaining
            }
        } else {
            remaining
        }
        takes = updatedTakes

        if (audId != null) {
            update(audId) { it.copy(exportedFileUri = null) }
        }

        if (fileToDelete != null) {
            saveScope.launch(Dispatchers.IO) {
                runCatching { fileToDelete.delete() }
            }
        }
    }

    fun saveTrimmedTake(
        originalTakeId: String,
        trimmedFileUri: String,
        durationSeconds: Int,
        replaceOriginal: Boolean,
    ): Take? {
        val original = takes.firstOrNull { it.id == originalTakeId } ?: return null
        val audId = original.auditionId
        val scIdx = original.sceneIndex

        if (audId != null) {
            update(audId) { it.copy(exportedFileUri = null) }
        }

        return if (replaceOriginal) {
            val oldFile = original.fileUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }?.let(::File)
            val updated = original.copy(
                fileUri = trimmedFileUri,
                durationSeconds = durationSeconds,
            )
            takes = takes.map { if (it.id == originalTakeId) updated else it }
            if (oldFile != null && oldFile.absolutePath != Uri.parse(trimmedFileUri).path) {
                saveScope.launch(Dispatchers.IO) { runCatching { oldFile.delete() } }
            }
            updated
        } else {
            val nextNumber = if (audId != null && scIdx != null) nextTakeNumber(audId, scIdx) else original.number + 1
            val newTake = Take(
                number = nextNumber,
                durationSeconds = durationSeconds,
                recordedAt = LocalDateTime.now(),
                isCircled = false,
                notes = emptyList(),
                fileUri = trimmedFileUri,
                auditionId = audId,
                sceneIndex = scIdx,
            )
            takes = takes + newTake
            newTake
        }
    }

    fun addTakeNote(takeId: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return
        updateTake(takeId) { it.copy(notes = it.notes + trimmed) }
    }

    fun removeTakeNote(takeId: String, index: Int) {
        updateTake(takeId) { t ->
            t.copy(notes = t.notes.filterIndexed { i, _ -> i != index })
        }
    }

    fun updateScriptLine(
        auditionId: String,
        sceneId: String,
        lineId: String,
        newCharacter: String,
        newText: String,
        isMine: Boolean,
    ) = update(auditionId) { a ->
        val scenes = a.scenes.map { s ->
            if (s.id == sceneId) {
                val lines = s.lines.map { l ->
                    if (l.id == lineId) l.copy(character = newCharacter.trim().ifEmpty { l.character }, text = newText.trim(), isMine = isMine) else l
                }
                s.copy(lines = lines)
            } else s
        }
        a.copy(scenes = scenes, exportedFileUri = null)
    }

    fun addScriptLine(
        auditionId: String,
        sceneId: String,
        afterLineId: String?,
        character: String,
        text: String,
        isMine: Boolean,
    ) = update(auditionId) { a ->
        val scenes = a.scenes.map { s ->
            if (s.id == sceneId) {
                val newLine = ScriptLine(character = character.trim().ifEmpty { "Character" }, text = text.trim(), isMine = isMine)
                val lines = if (afterLineId == null) {
                    listOf(newLine) + s.lines
                } else {
                    val idx = s.lines.indexOfFirst { it.id == afterLineId }
                    if (idx >= 0) {
                        s.lines.toMutableList().apply { add(idx + 1, newLine) }
                    } else {
                        s.lines + newLine
                    }
                }
                s.copy(lines = lines)
            } else s
        }
        a.copy(scenes = scenes, exportedFileUri = null)
    }

    fun deleteScriptLine(
        auditionId: String,
        sceneId: String,
        lineId: String,
    ) = update(auditionId) { a ->
        val scenes = a.scenes.map { s ->
            if (s.id == sceneId) {
                s.copy(lines = s.lines.filterNot { it.id == lineId })
            } else s
        }
        a.copy(scenes = scenes, exportedFileUri = null)
    }

    fun addScene(auditionId: String, title: String, heading: String) = update(auditionId) { a ->
        val nextNum = (a.scenes.maxOfOrNull { it.number } ?: 0) + 1
        val newScene = ScriptScene(
            number = nextNum,
            title = title.trim().ifEmpty { "Scene $nextNum" },
            heading = heading.trim().ifEmpty { "SCENE $nextNum" },
            lines = emptyList(),
            estimatedSeconds = 60,
        )
        val updatedScenes = a.scenes + newScene
        val pieces = a.pieces + Piece(kind = PieceKind.Scene, title = newScene.title, detail = "", isDone = false)
        a.copy(scenes = updatedScenes, pieces = pieces, exportedFileUri = null)
    }

    fun renameScene(auditionId: String, sceneId: String, newTitle: String, newHeading: String) = update(auditionId) { a ->
        val oldScene = a.scenes.firstOrNull { it.id == sceneId }
        val oldTitle = oldScene?.title
        val cleanTitle = newTitle.trim().ifEmpty { oldTitle ?: "Scene" }
        val cleanHeading = newHeading.trim().ifEmpty { oldScene?.heading ?: cleanTitle }
        val scenes = a.scenes.map { s ->
            if (s.id == sceneId) s.copy(title = cleanTitle, heading = cleanHeading) else s
        }
        val pieces = a.pieces.map { p ->
            if (p.kind == PieceKind.Scene && oldTitle != null && p.title == oldTitle) p.copy(title = cleanTitle) else p
        }
        a.copy(scenes = scenes, pieces = pieces, exportedFileUri = null)
    }

    fun deleteScene(auditionId: String, sceneId: String) = update(auditionId) { a ->
        val deletedScene = a.scenes.firstOrNull { it.id == sceneId }
        val deletedTitle = deletedScene?.title
        val scenes = a.scenes.filterNot { it.id == sceneId }
        val pieces = if (deletedTitle != null) a.pieces.filterNot { it.kind == PieceKind.Scene && it.title == deletedTitle } else a.pieces
        a.copy(scenes = scenes, pieces = pieces, exportedFileUri = null)
    }

    fun updatePracticeLine(practiceId: String, lineId: String, newCharacter: String, newText: String, isMine: Boolean) {
        practice = practice.map { p ->
            if (p.id == practiceId && p.scene != null) {
                val lines = p.scene.lines.map { l ->
                    if (l.id == lineId) l.copy(character = newCharacter.trim().ifEmpty { l.character }, text = newText.trim(), isMine = isMine) else l
                }
                val mineCount = lines.count { it.isMine }
                p.copy(scene = p.scene.copy(lines = lines), linesTotal = mineCount, linesLearned = minOf(p.linesLearned, mineCount))
            } else p
        }
    }

    fun addPracticeLine(practiceId: String, afterLineId: String?, character: String, text: String, isMine: Boolean) {
        practice = practice.map { p ->
            if (p.id == practiceId && p.scene != null) {
                val newLine = ScriptLine(character = character.trim().ifEmpty { "Character" }, text = text.trim(), isMine = isMine)
                val lines = if (afterLineId == null) {
                    listOf(newLine) + p.scene.lines
                } else {
                    val idx = p.scene.lines.indexOfFirst { it.id == afterLineId }
                    if (idx >= 0) p.scene.lines.toMutableList().apply { add(idx + 1, newLine) } else p.scene.lines + newLine
                }
                val mineCount = lines.count { it.isMine }
                p.copy(scene = p.scene.copy(lines = lines), linesTotal = mineCount, linesLearned = minOf(p.linesLearned, mineCount))
            } else p
        }
    }

    fun deletePracticeLine(practiceId: String, lineId: String) {
        practice = practice.map { p ->
            if (p.id == practiceId && p.scene != null) {
                val lines = p.scene.lines.filterNot { it.id == lineId }
                val mineCount = lines.count { it.isMine }
                p.copy(scene = p.scene.copy(lines = lines), linesTotal = mineCount, linesLearned = minOf(p.linesLearned, mineCount))
            } else p
        }
    }

    fun updatePracticePiece(practiceId: String, title: String, character: String) {
        practice = practice.map { p ->
            if (p.id == practiceId) {
                p.copy(
                    title = title.trim().ifEmpty { p.title },
                    character = character.trim().ifEmpty { p.character },
                    scene = p.scene?.copy(title = title.trim().ifEmpty { p.scene.title }),
                )
            } else p
        }
    }

    fun deletePracticePiece(practiceId: String) {
        val piece = practice.firstOrNull { it.id == practiceId } ?: return
        practice = practice.filterNot { it.id == practiceId }
        val ctx = context
        saveScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = ctx.filesDir ?: return@runCatching
                File(dir, "practice-$practiceId").deleteRecursively()
            }
        }
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

    fun setHeadshot(angle: String, uri: String) = saveHeadshot(angle, uri)

    fun deleteHeadshot(angle: String) {
        val key = angle.lowercase()
        val uri = headshots[key]
        val fileToDelete = uri?.let { runCatching { Uri.parse(it).path }.getOrNull() }?.let(::File)

        val updated = headshots.toMutableMap().apply { remove(key) }
        headshots = updated
        headshotsTaken = headshots.size

        if (key == "front") {
            auditions = auditions.map { a ->
                val pieces = a.pieces.map { p ->
                    if (p.kind == PieceKind.Headshot) p.copy(isDone = false) else p
                }
                a.copy(pieces = pieces, exportedFileUri = null)
            }
        }

        if (fileToDelete != null) {
            saveScope.launch(Dispatchers.IO) {
                runCatching { fileToDelete.delete() }
            }
        }
    }

    fun hasHeadshot(angle: String): Boolean = headshots.containsKey(angle.lowercase())

    fun headshot(angle: String): String? = headshots[angle.lowercase()]

    fun clearHeadshots() {
        headshots = emptyMap()
        headshotsTaken = 0
    }
}

val LocalAppStore = staticCompositionLocalOf<AppStore> { error("AppStore not provided") }
