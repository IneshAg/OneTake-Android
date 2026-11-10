package com.onetake.app.ui.auditions

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppTab
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.data.ScriptParser
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.otTime
import com.onetake.app.ocr.OcrManager
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.IconRow
import com.onetake.app.ui.components.LargeTitle
import com.onetake.app.ui.components.ListRow
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.PrimaryStageCta
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.SheetHeader
import com.onetake.app.ui.components.SpotlightStageScaffold
import com.onetake.app.ui.system.clipboardText
import com.onetake.app.ui.system.fileProviderUri
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.theme.OT
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AddAuditionSource {
    Paste,
    Scan,
    Photo,
    PdfOrFile,
    Share,
    Type,
}

internal fun addReviewTitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "Audition details"
    else -> "Here's what they want"
}

internal fun addReviewSubtitle(source: AddAuditionSource, asksCount: Int): String {
    val count = asksCount + 1
    val items = if (count == 1) "1 item" else "$count items"
    return when (source) {
        AddAuditionSource.Type -> "Set up your role, deadline, and deliverables, then save."
        AddAuditionSource.Scan -> "$items found from your scan. Tap any line to edit, then save."
        AddAuditionSource.PdfOrFile -> "$items found in the PDF. Tap any line to edit, then save."
        AddAuditionSource.Photo -> "$items found in the image. Tap any line to edit, then save."
        AddAuditionSource.Paste, AddAuditionSource.Share -> "$items found in the notice. Tap any line to edit, then save."
    }
}

internal fun addAsksSectionTitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "What to prepare"
    else -> "They asked for"
}

internal fun addEditHeaderTitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "What to prepare"
    else -> "What they want"
}

internal fun addEditSubtitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "Customize each deliverable and step. Only what's listed here becomes a step in your audition."
    AddAuditionSource.Scan -> "OneTake read this from your scan. It can miss things — add, remove or change any step. Only what's here becomes a step."
    AddAuditionSource.PdfOrFile -> "OneTake read this from the PDF. It can miss things — add, remove or change any step. Only what's here becomes a step."
    AddAuditionSource.Photo -> "OneTake read this from the image. It can miss things — add, remove or change any step. Only what's here becomes a step."
    AddAuditionSource.Paste, AddAuditionSource.Share -> "OneTake read this from the notice. It can miss things — add, remove or change any step. Only what's here becomes a step."
}

internal fun addEditAddButtonLabel(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "Add a deliverable"
    else -> "Add something they asked for"
}

/** Rule-based and ScriptParser-backed casting notice & sides parser. */
internal data class AddDraft(
    val project: String = "",
    val role: String = "",
    val projectType: String = "Feature film",
    val roleSize: String = "Lead",
    val castingOffice: String = "",
    val notes: String = "",
    val due: LocalDateTime? = null,
    val scenes: Int = 1,
    val asks: List<String> = emptyList(),
    val destination: Destination = Destination.ActorsAccess,
    val rawScriptText: String = "",
    val source: AddAuditionSource = AddAuditionSource.Type,
    val kind: DocumentKind = DocumentKind.CastingNotice,
    val detectedCharacters: List<String> = emptyList(),
    val allAvailableRoles: List<String> = emptyList(),
) {
    fun makeAudition(hasHeadshot: Boolean = false, hasProfiles: Boolean = false): Audition {
        val effectiveRole = role
            .ifBlank { if (detectedCharacters.size == 1) detectedCharacters.first() else "" }
            .ifBlank { "Role" }

        val finalScenes: List<ScriptScene> = if (scenes <= 0) {
            emptyList()
        } else {
            val extractedChars = if (rawScriptText.isNotBlank()) ScriptParser.extractCharacters(rawScriptText) else emptyList()
            val canParseScript = rawScriptText.isNotBlank() && (
                extractedChars.isNotEmpty() ||
                    kind == DocumentKind.SidesOnly ||
                    source == AddAuditionSource.Type
                )
            val parsedScenes = if (!canParseScript) {
                emptyList()
            } else {
                ScriptParser.parseScenes(rawScriptText, myCharacter = effectiveRole)
                    .filter { it.lines.isNotEmpty() }
            }
            parsedScenes.ifEmpty {
                (1..scenes).map { ScriptScene.placeholder(it, effectiveRole) }
            }
        }

        val needsProfiles = "Both profiles" in asks
        val pieces = mutableListOf<Piece>()
        val hasPhotoAsk = asks.isEmpty() || asks.any {
            it.equals("Headshot", ignoreCase = true) ||
            Regex("""(?i)\b(?:recent\s+pictures?|recent\s+photos?|photos?|photographs?|headshots?|pictures?)\b""").containsMatchIn(it)
        }
        val hasSlateAsk = asks.isEmpty() || asks.any {
            it.equals("Slate", ignoreCase = true) || it.equals("Slate first", ignoreCase = true) ||
            Regex("""(?i)\b(?:detailed\s+introduction|intro\s+video|introduction|audition\s+profile|slate|ident|idents)\b""").containsMatchIn(it)
        }
        if (hasPhotoAsk) {
            pieces.add(
                Piece(
                    kind = PieceKind.Headshot,
                    title = "Headshot",
                    detail = if (needsProfiles) "Front + profiles" else "Front",
                    isDone = if (needsProfiles) (hasHeadshot && hasProfiles) else hasHeadshot,
                )
            )
        }
        if (hasSlateAsk) {
            pieces.add(Piece(kind = PieceKind.Slate, title = "Slate", detail = "Name, height, city", isDone = false))
        }
        if ("Full length" in asks) {
            pieces.add(Piece(kind = PieceKind.FullLength, title = "Full length", detail = "Head to feet", isDone = false))
        }
        for (scene in finalScenes) {
            pieces.add(Piece(kind = PieceKind.Scene, title = scene.title, detail = "", isDone = false))
        }

        return Audition(
            project = project.ifBlank { "Untitled audition" },
            projectType = projectType.ifBlank { "Feature film" },
            role = effectiveRole,
            roleSize = roleSize.ifBlank { "Lead" },
            castingOffice = castingOffice.trim(),
            notes = notes.trim(),
            due = due ?: LocalDateTime.now().plusDays(3),
            asks = asks,
            pieces = pieces,
            scenes = finalScenes,
            destination = destination,
            status = AuditionStatus.Due,
        )
    }
}

/** Notice → draft. The rules live in [NoticeParser] (pure, unit-tested). */
internal fun addParseNotice(text: String): AddDraft {
    val f = NoticeParser.parse(text)
    val autoRole = f.role.ifEmpty {
        if (f.detectedCharacters.size == 1) f.detectedCharacters.first() else ""
    }
    return AddDraft(
        project = f.project,
        role = autoRole,
        projectType = f.projectType.ifBlank { "Feature film" },
        roleSize = f.roleSize.ifBlank { "Lead" },
        castingOffice = f.castingOffice,
        notes = f.notes,
        due = f.due,
        scenes = if (f.kind == DocumentKind.CastingNotice && f.scriptText.isBlank()) 0 else maxOf(0, f.sceneCount),
        asks = f.asks,
        destination = f.destination,
        rawScriptText = f.scriptText,
        kind = f.kind,
        detectedCharacters = f.detectedCharacters,
        allAvailableRoles = f.allAvailableRoles,
    )
}

internal fun addDraftFromNoticeFields(f: NoticeFields, source: AddAuditionSource = AddAuditionSource.Paste): AddDraft {
    val autoRole = f.role.ifEmpty {
        if (f.allAvailableRoles.size == 1) f.allAvailableRoles.first()
        else if (f.detectedCharacters.size == 1) f.detectedCharacters.first() else ""
    }
    return AddDraft(
        project = f.project,
        role = autoRole,
        projectType = f.projectType.ifBlank { "Feature film" },
        roleSize = f.roleSize.ifBlank { "Lead" },
        castingOffice = f.castingOffice,
        notes = f.notes,
        due = f.due,
        scenes = if (f.kind == DocumentKind.CastingNotice && f.scriptText.isBlank()) 0 else maxOf(0, f.sceneCount),
        asks = f.asks,
        destination = f.destination,
        rawScriptText = f.scriptText,
        source = source,
        kind = f.kind,
        detectedCharacters = f.detectedCharacters,
        allAvailableRoles = f.allAvailableRoles,
    )
}

private enum class AddStep { Reading, MissingDate, Review, EditAsks, CouldNotRead }

private val addDueLong = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.US)
private val addDueDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

@Composable
fun NewAuditionScreen(onClose: () -> Unit) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OT.colors

    var draft by remember { mutableStateOf(store.newAuditionDraft ?: AddDraft()) }
    var notice by remember { mutableStateOf(store.newAuditionNotice) }
    var pendingScanFile by remember { mutableStateOf<File?>(null) }
    var appendNextCapture by remember { mutableStateOf(false) }
    var isAppendingPage by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val initialStep = when {
        store.incomingShare != null -> AddStep.Reading
        store.pendingNewAuditionUris.isNotEmpty() -> AddStep.Reading
        notice.isNotBlank() -> AddStep.Reading
        else -> AddStep.Review
    }
    var path by remember { mutableStateOf(listOf(initialStep)) }

    fun mergeAdditionalText(additionalText: String, source: AddAuditionSource) {
        val combined = listOf(notice, additionalText).filter { it.isNotBlank() }.joinToString("\n\n")
        notice = combined
        val updatedScriptText = listOf(draft.rawScriptText, additionalText).filter { it.isNotBlank() }.joinToString("\n\n")
        val parsedScenes = ScriptParser.parseScenes(updatedScriptText, myCharacter = draft.role.ifBlank { "Role" })
        draft = draft.copy(
            rawScriptText = updatedScriptText,
            scenes = maxOf(draft.scenes, parsedScenes.size),
            detectedCharacters = (draft.detectedCharacters + ScriptParser.extractCharacters(updatedScriptText)).distinct(),
            source = source,
        )
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        val shouldAppend = appendNextCapture
        appendNextCapture = false
        if (uris.isNotEmpty()) {
            if (shouldAppend) {
                isAppendingPage = true
                scope.launch {
                    val result = OcrManager.recognizeTextFromUris(context, uris)
                    val text = result.getOrNull().orEmpty().trim()
                    isAppendingPage = false
                    if (text.isNotBlank()) {
                        mergeAdditionalText(text, draft.source)
                    }
                }
            } else {
                notice = ""
                draft = AddDraft(source = AddAuditionSource.Photo)
                path = listOf(AddStep.Reading)
                scope.launch {
                    val result = OcrManager.recognizeTextFromUris(context, uris)
                    val text = result.getOrNull().orEmpty().trim()
                    if (text.isBlank()) {
                        path = listOf(AddStep.CouldNotRead)
                    } else {
                        notice = text
                        draft = AddDraft(source = AddAuditionSource.Photo)
                    }
                }
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = pendingScanFile
        pendingScanFile = null
        val shouldAppend = appendNextCapture
        appendNextCapture = false
        if (captured && file != null && file.exists()) {
            val scanUri = fileProviderUri(context, file)
            if (shouldAppend) {
                isAppendingPage = true
                scope.launch {
                    val result = OcrManager.recognizeTextFromUri(context, scanUri)
                    runCatching { file.delete() }
                    val text = result.getOrNull().orEmpty().trim()
                    isAppendingPage = false
                    if (text.isNotBlank()) {
                        mergeAdditionalText(text, draft.source)
                    }
                }
            } else {
                notice = ""
                draft = AddDraft(source = AddAuditionSource.Scan)
                path = listOf(AddStep.Reading)
                scope.launch {
                    val result = OcrManager.recognizeTextFromUri(context, scanUri)
                    runCatching { file.delete() }
                    val text = result.getOrNull().orEmpty().trim()
                    if (text.isBlank()) {
                        path = listOf(AddStep.CouldNotRead)
                    } else {
                        notice = text
                        draft = AddDraft(source = AddAuditionSource.Scan)
                    }
                }
            }
        } else {
            runCatching { file?.delete() }
        }
    }

    fun launchCameraScan(append: Boolean) {
        appendNextCapture = append
        try {
            val tempFile = File(context.cacheDir, "ocr-scan-${System.currentTimeMillis()}.jpg")
            pendingScanFile = tempFile
            scanLauncher.launch(fileProviderUri(context, tempFile))
        } catch (e: Exception) {
            pendingScanFile = null
            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val scanAnotherPage: () -> Unit = { launchCameraScan(append = true) }
    val addAnotherPhoto: () -> Unit = {
        appendNextCapture = true
        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Process pending URIs from initial selection (e.g. Photo / File chosen from AddAuditionSheet)
    LaunchedEffect(Unit) {
        val initialUris = store.pendingNewAuditionUris
        if (initialUris.isNotEmpty()) {
            store.pendingNewAuditionUris = emptyList()
            notice = ""
            draft = AddDraft(source = draft.source)
            path = listOf(AddStep.Reading)
            scope.launch {
                val result = if (initialUris.size == 1) {
                    OcrManager.recognizeTextFromUri(context, initialUris.first())
                } else {
                    OcrManager.recognizeTextFromUris(context, initialUris)
                }
                val text = result.getOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    path = listOf(AddStep.CouldNotRead)
                } else {
                    notice = text
                    draft = AddDraft(source = draft.source)
                }
            }
        }
    }

    // Notice shared from another app
    LaunchedEffect(store.incomingShare) {
        val share = store.incomingShare ?: return@LaunchedEffect
        store.incomingShare = null
        notice = ""
        draft = AddDraft(source = AddAuditionSource.Share)
        path = listOf(AddStep.Reading)
        val parts = buildList {
            share.text?.let { add(it) }
            if (share.uris.isNotEmpty()) {
                OcrManager.recognizeTextFromUris(context, share.uris).getOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
        val text = parts.joinToString("\n\n").trim()
        if (text.isBlank()) {
            path = listOf(AddStep.CouldNotRead)
        } else {
            notice = text
            draft = AddDraft(source = AddAuditionSource.Share)
        }
    }

    fun save() {
        val hasHeadshot = store.hasHeadshot("front") || store.headshotsTaken > 0
        val hasProfiles = store.hasHeadshot("left") && store.hasHeadshot("right")
        val audition = draft.makeAudition(hasHeadshot = hasHeadshot, hasProfiles = hasProfiles)
        store.add(audition)
        store.clearNewAudition()
        nav.resetTo(AppTab.Auditions)
        nav.push(Route.AuditionDetail(audition.id))
    }

    fun requestBackOrExit() {
        if (path.size > 1) {
            path = path.dropLast(1)
        } else {
            val hasData = draft.project.isNotBlank() || draft.role.isNotBlank() || notice.isNotBlank() || draft.asks.isNotEmpty()
            if (hasData && path.lastOrNull() != AddStep.CouldNotRead) {
                showDiscardConfirm = true
            } else {
                store.clearNewAudition()
                onClose()
            }
        }
    }

    BackHandler(enabled = true) {
        requestBackOrExit()
    }

    val step = path.lastOrNull() ?: AddStep.Review

    Box(
        Modifier
            .fillMaxSize()
            .background(OT.colors.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (step) {
            AddStep.Reading -> AddReadingStep(
                notice = notice,
                source = draft.source,
                onCancel = {
                    store.clearNewAudition()
                    onClose()
                },
                onDone = { result ->
                    val foundNothing = result.project.isEmpty() &&
                        result.role.isEmpty() &&
                        result.allAvailableRoles.isEmpty() &&
                        result.rawScriptText.isBlank() &&
                        result.due == null &&
                        result.asks.isEmpty()
                    if (foundNothing) {
                        path = listOf(AddStep.CouldNotRead)
                    } else {
                        draft = result.copy(source = draft.source)
                        path = path + (if (result.due == null) AddStep.MissingDate else AddStep.Review)
                    }
                },
            )
            AddStep.MissingDate -> AddMissingDateStep(
                draft = draft,
                onCancel = { requestBackOrExit() },
                onContinue = { due ->
                    draft = draft.copy(due = due)
                    path = path + AddStep.Review
                },
            )
            AddStep.Review -> AddReviewStep(
                draft = draft,
                slateName = store.slate.name,
                isAppendingPage = isAppendingPage,
                onChange = { draft = it },
                onScanAnotherPage = scanAnotherPage,
                onAddAnotherPhoto = addAnotherPhoto,
                onPaste = { pasted ->
                    notice = pasted
                    path = listOf(AddStep.Reading)
                },
                onBack = { requestBackOrExit() },
                onEdit = { path = path + AddStep.EditAsks },
                onSave = { save() },
            )
            AddStep.EditAsks -> AddEditAsksStep(
                draft = draft,
                slateName = store.slate.name,
                onCancel = { path = path.dropLast(1) },
                onDone = {
                    draft = it
                    path = path.dropLast(1)
                },
            )
            AddStep.CouldNotRead -> AddErrorStep(
                onCancel = {
                    store.clearNewAudition()
                    onClose()
                },
                onRetry = {
                    if (notice.isNotBlank()) {
                        path = listOf(AddStep.Reading)
                    } else {
                        addAnotherPhoto()
                    }
                },
                onScan = { launchCameraScan(append = false) },
                onType = {
                    draft = AddDraft(source = AddAuditionSource.Type)
                    path = listOf(AddStep.Review)
                },
            )
        }

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text("Discard audition?", style = OT.type.headline, color = c.labelPrimary) },
                text = { Text("Any details you've entered will be lost.", style = OT.type.body, color = c.labelSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        store.clearNewAudition()
                        onClose()
                    }) {
                        Text("Discard", color = c.danger, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text("Keep editing", color = c.accent)
                    }
                },
                containerColor = c.surfaceCard,
            )
        }
    }
}

// MARK: - Workflow Steps

@Composable
internal fun ModelDownloadStatusPill(
    status: ModelStatus,
    modifier: Modifier = Modifier,
) {
    val downloading = status as? ModelStatus.Downloading ?: return
    val c = OT.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.accentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            progress = { (downloading.progressPercent / 100f).coerceIn(0f, 1f) },
            strokeWidth = 2.5.dp,
            color = c.accent,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "Downloading On-Device AI Engine (${downloading.progressPercent}%) — using fast parser for now",
                style = OT.type.headline.copy(fontSize = 13.sp),
                color = c.labelPrimary,
            )
            Text(
                text = "${downloading.downloadedMb} MB / ${downloading.totalMb} MB",
                style = OT.type.caption,
                color = c.labelSecondary,
            )
        }
    }
}

@Composable
private fun AddReadingStep(
    notice: String,
    source: AddAuditionSource,
    onCancel: () -> Unit,
    onDone: (AddDraft) -> Unit,
) {
    val c = OT.colors
    val context = LocalContext.current
    val downloadStatus by ModelDownloadManager.getInstance(context).status.collectAsState()
    var activeDraft by remember(notice) { mutableStateOf<AddDraft?>(null) }
    var aiStatusDescription by remember { mutableStateOf("Analyzing notice with on-device AI…") }
    var isReading by remember { mutableStateOf(true) }
    var currentItemIndex by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val done by rememberUpdatedState(onDone)

    val draft = activeDraft
    val roleSummary = when {
        draft == null -> "analyzing…"
        draft.role.isNotEmpty() -> draft.role
        draft.allAvailableRoles.isNotEmpty() -> draft.allAvailableRoles.joinToString(", ")
        draft.detectedCharacters.isNotEmpty() -> "${draft.detectedCharacters.size} characters detected"
        else -> "not in the notice"
    }
    val sidesSummary = when {
        draft == null -> "analyzing…"
        draft.kind == DocumentKind.CastingNotice && draft.rawScriptText.isBlank() -> "Notice only · no sides attached"
        draft.rawScriptText.isNotBlank() && draft.scenes == 1 -> "1 scene · dialogue extracted"
        draft.rawScriptText.isNotBlank() -> "${draft.scenes} scenes · dialogue extracted"
        draft.scenes == 1 -> "1 scene"
        draft.scenes > 1 -> "${draft.scenes} scenes"
        else -> "Notice only"
    }
    val rows = listOf(
        "Project" to (draft?.project?.ifEmpty { if (draft.kind == DocumentKind.SidesOnly) "sides page" else "not in the notice" } ?: "analyzing…"),
        "Your role" to roleSummary,
        "Due" to (draft?.due?.format(addDueLong) ?: if (draft == null) "analyzing…" else "no date given"),
        "Sides" to sidesSummary,
        "What they asked for" to (if (draft == null) "analyzing…" else when (draft.asks.size) {
            0 -> "nothing specific"
            1 -> "1 thing"
            else -> "${draft.asks.size} things"
        }),
    )

    LaunchedEffect(notice) {
        if (notice.isBlank()) return@LaunchedEffect

        Log.i("NewAuditionScreen", "AddReadingStep: launched parseSmart async for ${notice.length} chars (LLM primary)")
        val smartDeferred = async(Dispatchers.IO) {
            val extractor = HybridNoticeExtractor.getInstance(context)
            NoticeParser.parseSmart(notice, extractor)
        }

        // Cycle through items to give a lively scanning feel while LLM processes
        val scanJob = launch {
            var idx = 0
            while (isActive && isReading) {
                currentItemIndex = idx % rows.size
                delay(300)
                idx++
            }
        }

        val smartFields = runCatching { smartDeferred.await() }.getOrNull()
        isReading = false
        scanJob.cancel()

        if (smartFields != null) {
            val smartDraft = addDraftFromNoticeFields(smartFields, source)
            activeDraft = smartDraft
            val isLlmFilled = smartFields.project.isNotBlank() || smartFields.role.isNotBlank() || smartFields.allAvailableRoles.isNotEmpty()
            aiStatusDescription = if (isLlmFilled) "Filled by on-device AI" else "Filled by baseline fallback"
            haptics.success()
            delay(650)
            done(smartDraft)
        } else {
            // Ultimate fallback to deterministic regex only if the extractor failed completely
            val fallbackFields = NoticeParser.parse(notice)
            val fallbackDraft = addDraftFromNoticeFields(fallbackFields, source)
            activeDraft = fallbackDraft
            aiStatusDescription = "Filled by baseline fallback"
            haptics.success()
            delay(650)
            done(fallbackDraft)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", style = OT.type.body, color = c.accent)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (downloadStatus is ModelStatus.Downloading) {
                ModelDownloadStatusPill(status = downloadStatus)
            }

            val subtitle = when (source) {
                AddAuditionSource.Scan -> "Extracting the project, deadline, and sides with on-device AI…"
                AddAuditionSource.PdfOrFile -> "Extracting the project, deadline, and sides with on-device AI…"
                AddAuditionSource.Photo -> "Extracting the project, deadline, and sides with on-device AI…"
                else -> "Extracting the project, deadline, and sides with on-device AI…"
            }
            LargeTitle("Reading the notice", subtitle = subtitle)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                rows.forEachIndexed { i, (label, value) ->
                    val isScanning = isReading && i == currentItemIndex
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
                        when {
                            !isReading -> Text(
                                value,
                                style = OT.type.headline,
                                color = if ("not in" in value || "no date" in value) c.labelTertiary else c.labelPrimary,
                            )
                            isScanning -> CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = c.accent,
                                modifier = Modifier.size(16.dp),
                            )
                            else -> Text("analyzing…", style = OT.type.footnote, color = c.labelTertiary)
                        }
                    }
                    if (i < rows.lastIndex) OTDivider()
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isReading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = c.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(aiStatusDescription, style = OT.type.footnote, color = c.accent)
            }
        }
    }
}

@Composable
private fun AddMissingDateStep(draft: AddDraft, onCancel: () -> Unit, onContinue: (LocalDateTime?) -> Unit) {
    val c = OT.colors
    val today = remember { LocalDate.now() }
    val tomorrow = remember { today.plusDays(1) }
    val friday = remember { today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)) }
    var choice by remember { mutableStateOf<Int?>(null) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    var pickedTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", style = OT.type.body, color = c.accent)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val missingSubtitle = when (draft.source) {
                AddAuditionSource.Scan -> "Everything else is filled in from your scan."
                AddAuditionSource.PdfOrFile -> "Everything else is filled in from the PDF."
                AddAuditionSource.Photo -> "Everything else is filled in from the photo."
                else -> "Everything else is filled in from the notice."
            }
            LargeTitle("One thing missing", subtitle = missingSubtitle)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .border(1.dp, c.due, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.dueTrack),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Timer, null, tint = c.due, modifier = Modifier.size(22.dp)) }
                    Column {
                        Text("When's it due?", style = OT.type.headline, color = c.labelPrimary)
                        Text("No date was mentioned in the notice", style = OT.type.footnote, color = c.labelTertiary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddChip("Tomorrow", selected = choice == 0) { choice = 0 }
                    AddChip("Friday", selected = choice == 1) { choice = 1 }
                    AddChip(picked?.format(addDueDay) ?: "Pick a date", selected = choice == 2) {
                        choice = 2
                        showDatePicker = true
                    }
                }
                if (choice in listOf(0, 1, 2)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.fillField)
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Schedule, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Due at", style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
                        Text(
                            pickedTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)),
                            style = OT.type.headline,
                            color = c.accent,
                        )
                    }
                }
                AddChip("I don't know yet", selected = choice == 3) { choice = 3 }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OT.dims.gutter, vertical = 16.dp),
        ) {
            PrimaryButton(
                "Continue",
                enabled = choice != null,
                onClick = {
                    val date = when (choice) {
                        0 -> tomorrow
                        1 -> friday
                        2 -> picked ?: tomorrow
                        else -> null
                    }
                    val due = date?.atTime(pickedTime)
                    onContinue(due)
                },
            )
        }
    }

    if (showDatePicker) {
        AddDatePickerDialog(
            initial = picked ?: tomorrow,
            onPick = { picked = it; choice = 2; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        AddTimePickerDialog(
            initial = pickedTime,
            onPick = { pickedTime = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun AddReviewStep(
    draft: AddDraft,
    slateName: String,
    isAppendingPage: Boolean,
    onChange: (AddDraft) -> Unit,
    onScanAnotherPage: () -> Unit,
    onAddAnotherPhoto: () -> Unit,
    onPaste: (String) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
) {
    val c = OT.colors
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val parsedScenes = remember(draft.rawScriptText, draft.role) {
        if (draft.rawScriptText.isBlank()) emptyList()
        else ScriptParser.parseScenes(draft.rawScriptText, myCharacter = draft.role.ifBlank { "Role" })
    }
    val parsedLineCount = parsedScenes.sumOf { it.lines.size }
    val myLineCount = parsedScenes.sumOf { s -> s.lines.count { it.isMine } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF2A2723), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = c.labelSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text("NEW AUDITION", style = OT.type.navCaption, color = c.labelSecondary)
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(c.fillField)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Edit", style = OT.type.actionPill, color = c.accentGold) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LargeTitle(
                addReviewTitle(draft.source),
                subtitle = addReviewSubtitle(draft.source, draft.asks.size),
            )

            // Primary metadata card
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AddTextField(
                    label = "Project",
                    value = draft.project,
                    placeholder = "Untitled project",
                    onValueChange = { onChange(draft.copy(project = it)) },
                )
                OTDivider()

                AddTextField(
                    label = "Role",
                    value = draft.role,
                    placeholder = "Your role",
                    onValueChange = { newRole ->
                        val reparsedScenes = if (draft.rawScriptText.isNotBlank()) {
                            ScriptParser.parseScenes(draft.rawScriptText, myCharacter = newRole.ifBlank { "Role" })
                        } else emptyList()
                        val newScenes = if (draft.rawScriptText.isNotBlank()) {
                            maxOf(draft.scenes, reparsedScenes.size.coerceAtLeast(1))
                        } else {
                            draft.scenes
                        }
                        onChange(
                            draft.copy(
                                role = newRole,
                                scenes = newScenes,
                            )
                        )
                    },
                )

                val isCastingNoticeOnly = draft.kind == DocumentKind.CastingNotice && draft.rawScriptText.isBlank()
                val candidateRoles = if (draft.allAvailableRoles.size > 1) {
                    draft.allAvailableRoles
                } else if (!isCastingNoticeOnly && draft.detectedCharacters.size > 1) {
                    draft.detectedCharacters
                } else emptyList()

                val rolesToDisplay = with(NoticeParser) {
                    candidateRoles
                        .mapNotNull { it.cleanLlmField() }
                        .filterNot { NoticeParser.GENERIC_ROLE_FILTER_REGEX.matches(it) || ScriptParser.isNonCharacterCue(it) }
                        .distinct()
                }

                if (rolesToDisplay.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Available roles (tap to switch)", style = OT.type.caption, color = c.labelSecondary)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rolesToDisplay.forEach { charName ->
                                val isSelected = draft.role.equals(charName, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val newRole = charName
                                        val reparsedScenes = if (draft.rawScriptText.isNotBlank()) {
                                            ScriptParser.parseScenes(draft.rawScriptText, myCharacter = newRole)
                                        } else emptyList()
                                        val newScenesForChip = if (draft.rawScriptText.isNotBlank()) {
                                            maxOf(draft.scenes, reparsedScenes.size.coerceAtLeast(1))
                                        } else {
                                            draft.scenes
                                        }
                                        onChange(
                                            draft.copy(
                                                role = newRole,
                                                scenes = newScenesForChip,
                                            )
                                        )
                                    },
                                    label = { Text(charName, style = OT.type.subhead) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = c.accent,
                                        selectedLabelColor = Color.White,
                                        containerColor = c.fillField,
                                        labelColor = c.labelPrimary,
                                    ),
                                    border = null,
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                }
                OTDivider()

                AddDueTimeSection(
                    due = draft.due,
                    onChange = { onChange(draft.copy(due = it)) },
                    onOpenDatePicker = { showDatePicker = true },
                    onOpenTimePicker = { showTimePicker = true },
                )
            }

            // Dialogue & Sides extraction status
            val isPureCastingNotice = draft.kind == DocumentKind.CastingNotice && draft.rawScriptText.isBlank()
            if (!isPureCastingNotice && (draft.rawScriptText.isNotBlank() || draft.scenes > 0)) {
                SectionLabel("Script & sides")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surfaceCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(c.accentSoft),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Movie, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
                        Column(Modifier.weight(1f)) {
                            val sceneWord = if (draft.scenes == 1) "1 scene" else "${draft.scenes} scenes"
                            Text(sceneWord, style = OT.type.headline, color = c.labelPrimary)
                            val lineStatus = when {
                                myLineCount > 0 && myLineCount < parsedLineCount ->
                                    "$myLineCount of $parsedLineCount lines assigned to ${draft.role.ifBlank { "your character" }}"
                                parsedLineCount > 0 -> "$parsedLineCount lines extracted for ${draft.role.ifBlank { "your character" }}"
                                draft.rawScriptText.isNotBlank() -> "Text extracted · tap Edit to adjust character/dialogue"
                                else -> "Standard scene placeholders ready"
                            }
                            Text(lineStatus, style = OT.type.footnote, color = c.labelSecondary)
                        }
                    }
                    if (isAppendingPage) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(14.dp))
                            Text("Reading next page…", style = OT.type.footnote, color = c.accent)
                        }
                    } else if (draft.source in listOf(AddAuditionSource.Scan, AddAuditionSource.Photo, AddAuditionSource.PdfOrFile)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = onScanAnotherPage,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(c.fillField),
                            ) {
                                Icon(Icons.Outlined.DocumentScanner, null, tint = c.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan next page", style = OT.type.footnote, color = c.accent)
                            }
                            TextButton(
                                onClick = onAddAnotherPhoto,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(c.fillField),
                            ) {
                                Icon(Icons.Outlined.Image, null, tint = c.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add photo", style = OT.type.footnote, color = c.accent)
                            }
                        }
                    }
                }
            }

            // Deliverables / Asks
            SectionLabel(addAsksSectionTitle(draft.source))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                val deliverables = buildList {
                    val hasExplicitAsks = draft.asks.isNotEmpty()

                    val asksLower = draft.asks.map { it.lowercase() }
                    val hasPhotoAsk = !hasExplicitAsks || asksLower.any {
                        it == "headshot" || Regex("""(?i)\b(?:recent\s+pictures?|recent\s+photos?|photos?|photographs?|headshots?|pictures?)\b""").containsMatchIn(it)
                    }
                    val hasSlateAsk = !hasExplicitAsks || asksLower.any {
                        it == "slate" || it == "slate first" || Regex("""(?i)\b(?:detailed\s+introduction|intro\s+video|introduction|audition\s+profile|slate|ident|idents)\b""").containsMatchIn(it)
                    }
                    val hasFullLength = draft.asks.any {
                        it.equals("Full length", ignoreCase = true) || Regex("""(?i)\b(?:full-length|full\s*length|full\s*body|head\s*to\s*toe|head\s*to\s*feet)\b""").containsMatchIn(it)
                    }

                    if (hasPhotoAsk) {
                        val title = if (draft.asks.any { it.contains("picture", ignoreCase = true) || it.contains("photo", ignoreCase = true) }) {
                            "Headshot / Recent pictures"
                        } else {
                            "Headshot"
                        }
                        val detail = if ("Both profiles" in draft.asks) "Front + profiles" else "Recent photos / headshot"
                        add(title to detail)
                    }

                    if (hasSlateAsk) {
                        val title = if (draft.asks.any { it.contains("intro", ignoreCase = true) || it.contains("introduction", ignoreCase = true) }) {
                            "Slate / Detailed introduction"
                        } else {
                            "Slate"
                        }
                        val detail = "Name, height, city, and introduction"
                        add(title to detail)
                    }

                    if (hasFullLength) {
                        add("Full length" to "Head to feet")
                    }

                    val sceneCount = if (isPureCastingNotice) 0 else draft.scenes
                    if (sceneCount > 0) {
                        for (i in 1..sceneCount) {
                            val detail = if (parsedLineCount > 0) "Lines extracted" else "Scene $i"
                            add("Scene $i" to detail)
                        }
                    }

                    draft.asks.filter { ask ->
                        val l = ask.lowercase()
                        !l.contains("headshot") && !l.contains("picture") && !l.contains("photo") &&
                        !l.contains("slate") && !l.contains("intro") && !l.contains("ident") &&
                        !l.contains("full length") && !l.contains("full body") && !l.contains("both profiles")
                    }.forEach { customAsk ->
                        val formatted = NoticeParser.formatAskPhrase(customAsk)
                        add(formatted to addAskDetail(formatted, slateName, draft.role))
                    }
                }
                deliverables.forEachIndexed { i, (title, detail) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AddAskIconView(title, tint = c.accent, size = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = OT.type.body, color = c.labelPrimary)
                            if (detail.isNotEmpty()) Text(detail, style = OT.type.footnote, color = c.labelTertiary)
                        }
                        Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    }
                    if (i < deliverables.lastIndex) OTDivider()
                }
            }

            // Destination
            SectionLabel("Where's it going?")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                var showDestMenu by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDestMenu = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Destination", style = OT.type.caption, color = c.labelSecondary)
                        Text(draft.destination.label, style = OT.type.body, color = c.labelPrimary)
                    }
                    Text("Change", style = OT.type.footnote, color = c.accent)
                }
                DropdownMenu(
                    expanded = showDestMenu,
                    onDismissRequest = { showDestMenu = false },
                ) {
                    Destination.entries.forEach { dest ->
                        DropdownMenuItem(
                            text = { Text(dest.label) },
                            onClick = {
                                onChange(draft.copy(destination = dest))
                                showDestMenu = false
                            },
                        )
                    }
                }
            }

            // Notes
            SectionLabel("Casting notes & instructions")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .padding(16.dp),
            ) {
                BasicTextField(
                    value = draft.notes,
                    onValueChange = { onChange(draft.copy(notes = it)) },
                    textStyle = OT.type.body.copy(color = c.labelPrimary),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    decorationBox = { inner ->
                        if (draft.notes.isBlank()) {
                            Text("Add wardrobe notes, director notes, accents, etc.", style = OT.type.body, color = c.labelTertiary)
                        }
                        inner()
                    },
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OT.dims.gutter, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryStageCta(
                icon = Icons.Filled.Check,
                caption = "LOOKS RIGHT",
                onClick = onSave,
            )

            // Bottom Ingestion Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "OR BRING IT IN ANOTHER WAY",
                    style = OT.type.stageOverline,
                    color = c.labelTertiary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(c.fillField)
                            .clickable {
                                val text = runCatching { clipboardText(context) }.getOrNull().orEmpty()
                                if (text.isNotBlank()) onPaste(text)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            Text("Paste", style = OT.type.actionPill, color = c.labelPrimary)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(c.fillField)
                            .clickable { onScanAnotherPage() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            Text("Scan", style = OT.type.actionPill, color = c.labelPrimary)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(c.fillField)
                            .clickable { onAddAnotherPhoto() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            Text("File", style = OT.type.actionPill, color = c.labelPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initial = draft.due?.toLocalDate() ?: LocalDate.now().plusDays(2)
        AddDatePickerDialog(
            initial = initial,
            onPick = { newDate ->
                val time = draft.due?.toLocalTime() ?: LocalTime.of(10, 0)
                onChange(draft.copy(due = newDate.atTime(time)))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        val initial = draft.due?.toLocalTime() ?: LocalTime.of(10, 0)
        AddTimePickerDialog(
            initial = initial,
            onPick = { newTime ->
                val date = draft.due?.toLocalDate() ?: LocalDate.now().plusDays(2)
                onChange(draft.copy(due = date.atTime(newTime)))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun AddEditAsksStep(draft: AddDraft, slateName: String, onCancel: () -> Unit, onDone: (AddDraft) -> Unit) {
    val c = OT.colors
    var working by remember { mutableStateOf(draft) }
    var showAdd by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SheetHeader(
            addEditHeaderTitle(working.source),
            leading = "Cancel",
            trailing = "Done",
            onLeading = onCancel,
            onTrailing = { onDone(working) },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                addEditSubtitle(working.source),
                style = OT.type.callout,
                color = c.labelSecondary,
            )

            SectionLabel("Due date & time", Modifier.padding(top = 6.dp))
            AddDueTimeSection(
                due = working.due,
                onChange = { working = working.copy(due = it) },
                onOpenDatePicker = { showDatePicker = true },
                onOpenTimePicker = { showTimePicker = true },
            )

            SectionLabel("Scenes", Modifier.padding(top = 6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("How many scenes?", style = OT.type.body, color = c.labelPrimary)
                        Text("OneTake creates a scene item for each", style = OT.type.footnote, color = c.labelTertiary)
                    }
                    AddStepper(working.scenes, 0..8) { working = working.copy(scenes = it) }
                }
            }

            SectionLabel("Always included", Modifier.padding(top = 6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                ListRow(
                    title = "Headshot",
                    detail = if ("Both profiles" in working.asks) "Front + both profiles" else "Front only",
                    icon = Icons.Outlined.Portrait,
                    iconTint = c.accent,
                    showChevron = false,
                    showDivider = false,
                )
                OTDivider()
                ListRow(
                    title = "Slate",
                    detail = if (slateName.isNotEmpty()) "Using saved slate ($slateName)" else "Record once, reuse anywhere",
                    icon = Icons.Outlined.Badge,
                    iconTint = c.accent,
                    showChevron = false,
                    showDivider = false,
                )
            }

            SectionLabel("They asked for", Modifier.padding(top = 6.dp))
            if (working.asks.isEmpty()) {
                Text("Nothing extra — just headshot, slate and scenes.", style = OT.type.footnote, color = c.labelTertiary)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surfaceCard)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    working.asks.forEachIndexed { i, ask ->
                        AddEditAskRow(
                            ask = ask,
                            detail = addAskDetail(ask, slateName, working.role),
                            showDivider = i < working.asks.lastIndex,
                            onRemove = { working = working.copy(asks = working.asks.filter { it != ask }) },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.accentSoft)
                    .clickable { showAdd = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Add, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    Text(addEditAddButtonLabel(working.source), style = OT.type.subhead, color = c.accent)
                }
            }
        }
    }

    if (showDatePicker) {
        val initial = working.due?.toLocalDate() ?: LocalDate.now().plusDays(2)
        AddDatePickerDialog(
            initial = initial,
            onPick = { newDate ->
                val time = working.due?.toLocalTime() ?: LocalTime.of(10, 0)
                working = working.copy(due = newDate.atTime(time))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        val initial = working.due?.toLocalTime() ?: LocalTime.of(10, 0)
        AddTimePickerDialog(
            initial = initial,
            onPick = { newTime ->
                val date = working.due?.toLocalDate() ?: LocalDate.now().plusDays(2)
                working = working.copy(due = date.atTime(newTime))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showAdd) {
        val suggestions = listOf(
            "Both profiles" to "Front, left, right in headshots",
            "Full length" to "Head-to-toe framing check",
            "Idents" to "Holding passport/ID to camera",
            "Hands to camera" to "Show palms and backs of hands",
            "Current hair & look" to "Quick check of hair length and colour",
            "Availability" to "State shoot dates you can/can't make",
            "Landscape" to "Shoot all scenes landscape",
            "Portrait" to "Shoot all scenes portrait",
        ).filter { (label, _) -> label !in working.asks }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add deliverable", style = OT.type.headline, color = c.labelPrimary) },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (suggestions.isEmpty()) {
                        Text("All common deliverables have been added.", style = OT.type.body, color = c.labelSecondary)
                    } else {
                        suggestions.forEach { (label, desc) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.fillField)
                                    .clickable {
                                        working = working.copy(asks = working.asks + label)
                                        showAdd = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = OT.type.headline, color = c.labelPrimary)
                                    Text(desc, style = OT.type.footnote, color = c.labelSecondary)
                                }
                                Icon(Icons.Filled.Add, null, tint = c.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdd = false }) { Text("Close", color = c.accent) }
            },
            containerColor = c.surfaceCard,
        )
    }
}

@Composable
private fun AddErrorStep(onCancel: () -> Unit, onRetry: () -> Unit, onScan: () -> Unit, onType: () -> Unit) {
    val c = OT.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", style = OT.type.body, color = c.accent)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Warning, null, tint = c.warning, modifier = Modifier.size(40.dp))
            LargeTitle(
                "We couldn't read this notice",
                subtitle = "It looks like an image of a PDF with very small text. Nothing was lost — pick how to continue.",
            )
            Column {
                IconRow(Icons.Outlined.Refresh, "Try again", "Scans the notice again", onClick = onRetry)
                OTDivider(startIndent = 54.dp)
                IconRow(Icons.Outlined.DocumentScanner, "Scan the pages instead", "Clearer than a screenshot", onClick = onScan)
                OTDivider(startIndent = 54.dp)
                IconRow(Icons.Outlined.Keyboard, "Type the details", "Name, role and due date — 30 seconds", onClick = onType)
            }
        }
    }
}

// MARK: - Helper UI Components

@Composable
private fun AddValueRow(value: String, label: String, showDivider: Boolean = true) {
    val c = OT.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = OT.type.headline, color = c.labelPrimary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = OT.type.footnote, color = c.labelTertiary)
    }
    if (showDivider) OTDivider()
}

@Composable
private fun AddChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OT.colors
    val bg = if (selected) c.accent else c.fillField
    val fg = if (selected) c.labelOnAccent else c.labelPrimary
    val border = if (selected) null else BorderStroke(1.dp, c.separator)
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OT.type.subhead, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDatePickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val initialMillis = remember(initial) {
        initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val pickedMillis = state.selectedDateMillis
                    if (pickedMillis != null) {
                        val picked = Instant.ofEpochMilli(pickedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        onPick(picked)
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("Done", color = OT.colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OT.colors.labelSecondary) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimePickerDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select due time", style = OT.type.headline, color = OT.colors.labelPrimary) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text("Done", color = OT.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OT.colors.labelSecondary)
            }
        },
        containerColor = OT.colors.surfaceCard,
    )
}

@Composable
private fun AddDueTimeSection(
    due: LocalDateTime?,
    onChange: (LocalDateTime?) -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenTimePicker: () -> Unit,
) {
    val c = OT.colors
    val today = remember { LocalDate.now() }
    val tomorrow = remember { today.plusDays(1) }
    val weekdayDate = remember {
        if (tomorrow.dayOfWeek == DayOfWeek.FRIDAY) {
            today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        } else {
            today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
        }
    }
    val weekdayLabel = remember {
        weekdayDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    val currentLocalDate = due?.toLocalDate()
    val currentTime = due?.toLocalTime() ?: LocalTime.of(10, 0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = c.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Due date", style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
            if (due != null) {
                TextButton(onClick = { onChange(null) }) {
                    Text("Clear", style = OT.type.caption, color = c.labelTertiary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AddChip("Tomorrow", selected = currentLocalDate == tomorrow) {
                onChange(tomorrow.atTime(currentTime))
            }
            AddChip(weekdayLabel, selected = currentLocalDate == weekdayDate) {
                onChange(weekdayDate.atTime(currentTime))
            }
            val isCustomDate = currentLocalDate != null && currentLocalDate != tomorrow && currentLocalDate != weekdayDate
            val customDateLabel = if (isCustomDate) currentLocalDate.format(addDueDay) else "Pick date…"
            AddChip(customDateLabel, selected = isCustomDate) {
                onOpenDatePicker()
            }
        }

        if (due != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.fillField)
                    .clickable { onOpenTimePicker() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Schedule, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Due at", style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
                Text(
                    currentTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)),
                    style = OT.type.headline,
                    color = c.accent,
                )
            }
        }
    }
}

private fun addAskIcon(ask: String): ImageVector {
    val l = ask.lowercase()
    return when {
        l.contains("headshot") || l.contains("picture") || l.contains("photo") -> Icons.Outlined.Portrait
        l.contains("slate") || l.contains("ident") -> Icons.Outlined.Badge
        l.contains("contact") || l.contains("phone") || l.contains("number") -> Icons.Outlined.Phone
        l.contains("reel") || l.contains("showreel") || l.contains("video") || l.contains("intro") -> Icons.Outlined.Movie
        l.contains("link") -> Icons.Outlined.Link
        l == "both profiles" -> Icons.Outlined.Portrait
        l == "full length" || l == "full body" -> Icons.Outlined.AccessibilityNew
        l == "landscape" || l == "portrait" -> Icons.Outlined.Smartphone
        l == "hands to camera" -> Icons.Outlined.Person
        l == "availability" -> Icons.Outlined.CalendarMonth
        else -> Icons.Outlined.CheckCircle
    }
}

private fun addAskDetail(ask: String, slateName: String, role: String): String {
    val l = ask.lowercase()
    return when {
        l.contains("contact") || l.contains("phone") -> "Include in submission message or slate"
        l.contains("showreel") || l.contains("reel") -> "Include showreel link or video"
        l.contains("link") -> "Include in audition submission"
        l.contains("picture") || l.contains("photo") || l.contains("headshot") -> "Recent photos / headshot"
        l.contains("intro") || l.contains("slate") -> "Detailed introduction video"
        ask == "Both profiles" -> "Both profiles required"
        ask == "Full length" -> "Head-to-toe framing check"
        ask == "Landscape" -> "Shoot all scenes landscape"
        ask == "Portrait" -> "Shoot all scenes portrait"
        ask == "Idents" -> "Holding ID to camera"
        ask == "Hands to camera" -> "Show palms and backs of hands"
        ask == "Availability" -> "State shoot dates you can make"
        ask == "Current hair & look" -> "Confirm current hairstyle"
        else -> ""
    }
}

@Composable
private fun AddAskIconView(ask: String, tint: Color, size: Dp) {
    val icon = when {
        ask.startsWith("Scene") -> Icons.Outlined.Movie
        ask.startsWith("Headshot") -> Icons.Outlined.Portrait
        ask.startsWith("Slate") -> Icons.Outlined.Badge
        else -> addAskIcon(ask)
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun AddTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val c = OT.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = OT.type.caption, color = c.labelSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = OT.type.headline.copy(color = c.labelPrimary),
            cursorBrush = SolidColor(c.accent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isBlank()) {
                    Text(placeholder, style = OT.type.headline, color = c.labelTertiary)
                }
                inner()
            },
        )
    }
}

@Composable
private fun AddStepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = OT.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(c.fillField)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircleIconButton(
            Icons.Filled.Close,
            "Minus",
            size = 28.dp,
            onClick = { if (value > range.first) onChange(value - 1) },
        )
        Text(
            "$value",
            style = OT.type.headline,
            color = c.labelPrimary,
            modifier = Modifier.widthIn(min = 20.dp),
        )
        CircleIconButton(
            Icons.Filled.Add,
            "Plus",
            size = 28.dp,
            onClick = { if (value < range.last) onChange(value + 1) },
        )
    }
}

@Composable
private fun AddEditAskRow(ask: String, detail: String, showDivider: Boolean, onRemove: () -> Unit) {
    val c = OT.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AddRemoveDot(label = "Remove $ask", onClick = onRemove)
        Spacer(Modifier.width(12.dp))
        Icon(addAskIcon(ask), null, tint = c.labelSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ask, style = OT.type.body, color = c.labelPrimary)
            if (detail.isNotEmpty()) Text(detail, style = OT.type.footnote, color = c.labelTertiary)
        }
    }
    if (showDivider) OTDivider()
}

@Composable
private fun AddRemoveDot(label: String, onClick: () -> Unit) {
    val c = OT.colors
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(c.danger)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(2.dp)
                .background(Color.White, RoundedCornerShape(1.dp)),
        )
    }
}
