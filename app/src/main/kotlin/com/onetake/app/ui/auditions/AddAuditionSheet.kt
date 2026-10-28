package com.onetake.app.ui.auditions

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.SheetHeader
import com.onetake.app.ui.system.clipboardText
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// 06 Add an audition → 6.1 Reading the notice → (6.2 One thing missing) → 6c Here's what they want → 7.0 Detail (0 of 4)

private enum class AddStep { Reading, MissingDate, Review, EditAsks, CouldNotRead }

private val addDueLong = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.US)
private val addDueDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/** Rule-based and ScriptParser-backed casting notice & sides parser. */
private data class AddDraft(
    val project: String = "",
    val role: String = "",
    val projectType: String = "Feature film",
    val due: LocalDateTime? = null,
    val scenes: Int = 1,
    val asks: List<String> = emptyList(),
    val destination: Destination = Destination.ActorsAccess,
    val rawScriptText: String = "",
) {
    fun makeAudition(hasHeadshot: Boolean = false): Audition {
        // Use the pasted/scanned text as the scene only when it actually reads like sides — one of
        // the lines is yours, or there are at least two speakers. Notice prose would otherwise
        // become a fake "monologue" for the role.
        val hasCues = rawScriptText.isNotBlank() && ScriptParser.extractCharacters(rawScriptText).isNotEmpty()
        // One scene per slugline / "SCENE 2" header in the sides.
        val parsedScenes = if (!hasCues) emptyList() else
            ScriptParser.parseScenes(rawScriptText, myCharacter = role.ifBlank { "Me" })
                .filter { scene -> scene.lines.any { it.isMine } || scene.lines.distinctBy { it.character }.size >= 2 }
        val finalScenes = parsedScenes.ifEmpty { (1..maxOf(1, scenes)).map { ScriptScene.placeholder(it, role) } }

        val pieces = mutableListOf(
            Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front", isDone = hasHeadshot),
            Piece(kind = PieceKind.Slate, title = "Slate", detail = "Name, height, city", isDone = false),
        )
        for (scene in finalScenes) {
            pieces.add(Piece(kind = PieceKind.Scene, title = scene.title, detail = "", isDone = false))
        }

        return Audition(
            project = project.ifBlank { "Untitled audition" },
            projectType = projectType,
            role = role.ifBlank { "Role" },
            roleSize = "Lead",
            castingOffice = "",
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
private fun addParseNotice(text: String): AddDraft {
    val f = NoticeParser.parse(text)
    return AddDraft(
        project = f.project,
        role = f.role,
        due = f.due,
        asks = f.asks,
        destination = f.destination,
        rawScriptText = f.scriptText,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAuditionSheet(onDismiss: () -> Unit) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var path by remember { mutableStateOf(listOf<AddStep>()) }
    var notice by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(AddDraft()) }

    fun startReading(text: String) {
        if (text.isBlank()) {
            path = listOf(AddStep.CouldNotRead)
            return
        }
        notice = text
        draft = addParseNotice(text)
        path = listOf(AddStep.Reading)
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            notice = "" // Reading waits on this — never re-read the previous notice meanwhile.
            path = listOf(AddStep.Reading)
            scope.launch {
                val result = OcrManager.recognizeTextFromUri(context, uri)
                val text = result.getOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    path = listOf(AddStep.CouldNotRead)
                } else {
                    notice = text
                    draft = addParseNotice(text)
                }
            }
        }
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            notice = ""
            path = listOf(AddStep.Reading)
            scope.launch {
                val result = OcrManager.recognizeTextFromBitmap(bitmap)
                val text = result.getOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    path = listOf(AddStep.CouldNotRead)
                } else {
                    notice = text
                    draft = addParseNotice(text)
                }
            }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            notice = "" // Reading waits on this — never re-read the previous notice meanwhile.
            path = listOf(AddStep.Reading)
            scope.launch {
                val result = OcrManager.recognizeTextFromUri(context, uri)
                val text = result.getOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    path = listOf(AddStep.CouldNotRead)
                } else {
                    notice = text
                    draft = addParseNotice(text)
                }
            }
        }
    }
    // A notice shared from another app (MainActivity → AppStore.incomingShare): its text plus whatever
    // the attached images or PDFs say, read on the phone like a photo or file.
    LaunchedEffect(store.incomingShare) {
        val share = store.incomingShare ?: return@LaunchedEffect
        store.incomingShare = null
        notice = ""
        path = listOf(AddStep.Reading)
        val parts = buildList {
            share.text?.let { add(it) }
            share.uris.forEach { uri ->
                OcrManager.recognizeTextFromUri(context, uri).getOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
        val text = parts.joinToString("\n\n").trim()
        if (text.isBlank()) {
            path = listOf(AddStep.CouldNotRead)
        } else {
            notice = text
            draft = addParseNotice(text)
        }
    }

    val pickPhoto: () -> Unit = {
        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    // "Scan" = a photo of the pages with the system camera; falls back to the photo picker
    // if no camera app is available or camera permission hasn't been granted yet.
    val scan: () -> Unit = {
        try {
            scanLauncher.launch(null)
        } catch (e: Exception) {
            pickPhoto()
        }
    }

    fun save() {
        val hasHeadshot = store.hasHeadshot("front") || store.headshotsTaken > 0
        val audition = draft.makeAudition(hasHeadshot = hasHeadshot)
        store.add(audition)
        onDismiss()
        nav.resetTo(AppTab.Auditions)
        nav.push(Route.AuditionDetail(audition.id))
    }

    val step = path.lastOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OT.colors.bgCanvas,
    ) {
        BackHandler(enabled = path.isNotEmpty()) { path = path.dropLast(1) }
        when (step) {
            null -> AddCaptureStep(
                onClose = onDismiss,
                onPaste = { startReading(runCatching { clipboardText(context) }.getOrNull().orEmpty()) },
                onPhoto = pickPhoto,
                onScan = scan,
                onFile = { fileLauncher.launch(arrayOf("application/pdf", "text/plain", "application/rtf", "text/rtf")) },
                onType = {
                    draft = AddDraft()
                    path = path + AddStep.Review
                },
            )
            AddStep.Reading -> AddReadingStep(
                notice = notice,
                onCancel = onDismiss,
                onDone = { result ->
                    if (result.project.isEmpty() && result.role.isEmpty()) {
                        path = listOf(AddStep.CouldNotRead)
                    } else {
                        draft = result
                        path = path + (if (result.due == null) AddStep.MissingDate else AddStep.Review)
                    }
                },
            )
            AddStep.MissingDate -> AddMissingDateStep(
                draft = draft,
                onCancel = onDismiss,
                onContinue = { due ->
                    draft = draft.copy(due = due)
                    path = path + AddStep.Review
                },
            )
            AddStep.Review -> AddReviewStep(
                draft = draft,
                slateName = store.slate.name,
                onChange = { draft = it },
                onBack = { path = path.dropLast(1) },
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
                onCancel = onDismiss,
                // Back to capture: re-reading the same text would just fail the same way again.
                onRetry = { path = emptyList() },
                onScan = scan,
                onType = {
                    draft = AddDraft()
                    path = listOf(AddStep.Review)
                },
            )
        }
    }
}

// MARK: - 06 capture-first sheet

@Composable
private fun AddCaptureStep(
    onClose: () -> Unit,
    onPaste: () -> Unit,
    onPhoto: () -> Unit,
    onScan: () -> Unit,
    onFile: () -> Unit,
    onType: () -> Unit,
) {
    val c = OT.colors
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Add an audition",
                style = OT.type.title,
                color = c.labelPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            CircleIconButton(Icons.Filled.Close, "Close", onClick = onClose)
        }

        // The clipboard is only read when Paste is tapped.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.accentSoft)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.ContentPaste, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
                Column {
                    Text("Copied the notice?", style = OT.type.headline, color = c.labelPrimary)
                    Text("Paste the email or message — OneTake reads it here", style = OT.type.footnote, color = c.labelTertiary)
                }
            }
            PrimaryButton("Paste and set up", onClick = onPaste)
        }

        SectionLabel("Or bring it in")
        Row(Modifier.fillMaxWidth()) {
            AddWay(Icons.Outlined.Image, "Photo", onPhoto, Modifier.weight(1f))
            AddWay(Icons.Outlined.DocumentScanner, "Scan", onScan, Modifier.weight(1f))
            AddWay(Icons.Outlined.Folder, "File", onFile, Modifier.weight(1f))
            AddWay(Icons.Outlined.Keyboard, "Type it", onType, Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Share, null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text("Faster: in Mail or WhatsApp, Share → OneTake", style = OT.type.footnote, color = c.accent)
            }
        }
    }
}

@Composable
private fun AddWay(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OT.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(c.fillField), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.labelPrimary, modifier = Modifier.size(24.dp))
        }
        Text(title, style = OT.type.footnote, color = c.labelSecondary)
    }
}

// MARK: - 6.1 Reading the notice

@Composable
private fun AddReadingStep(notice: String, onCancel: () -> Unit, onDone: (AddDraft) -> Unit) {
    val c = OT.colors
    val parsed = remember(notice) { addParseNotice(notice) }
    var found by remember(notice) { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val done by rememberUpdatedState(onDone)
    val rows = listOf(
        "Project" to parsed.project.ifEmpty { "not in the notice" },
        "Your role" to parsed.role.ifEmpty { "not in the notice" },
        "Due" to (parsed.due?.format(addDueLong) ?: "no date given"),
        "Sides" to if (parsed.scenes == 1) "1 scene" else "${parsed.scenes} scenes",
        "What they asked for" to when (parsed.asks.size) {
            0 -> "nothing specific"
            1 -> "1 thing"
            else -> "${parsed.asks.size} things"
        },
    )

    LaunchedEffect(notice) {
        // Blank while a scan/photo is still being recognised: keep showing "reading…" and wait —
        // the effect restarts once the recognised text arrives.
        if (notice.isBlank()) return@LaunchedEffect
        for (i in 1..rows.size) {
            delay(450)
            found = i
            haptics.tick()
        }
        delay(300)
        done(parsed)
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        SheetHeader("", leading = "Cancel", onLeading = onCancel)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LargeTitle("Reading the notice…", subtitle = "Extracting details and dates.")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceSunken)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(notice, style = OT.type.callout, color = c.labelSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                val chips = buildList {
                    if (found >= 2 && parsed.role.isNotEmpty()) add(parsed.role.uppercase())
                    if (found >= 1 && parsed.project.isNotEmpty()) add(parsed.project.uppercase())
                    if (found >= 3) parsed.due?.let { add(it.format(addDueDay) + ", " + it.otTime) }
                }
                if (chips.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach {
                            Text(
                                it,
                                style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = c.scriptOnHighlight,
                                maxLines = 1,
                                modifier = Modifier.clip(CircleShape).background(c.scriptHighlight).padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            SectionLabel("Found so far", Modifier.padding(top = 6.dp))
            Column {
                rows.forEachIndexed { i, (title, value) ->
                    val isFound = i < found
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.width(56.dp)) {
                            Text(
                                if (isFound) "FOUND" else "READING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = c.labelTertiary,
                                maxLines = 1,
                            )
                            Text(if (isFound) "✓" else "…", style = OT.type.footnote, color = c.labelPrimary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, style = OT.type.body, color = c.labelPrimary)
                            Text(
                                when {
                                    isFound -> value
                                    i == found -> "reading…"
                                    else -> "next"
                                },
                                style = OT.type.footnote,
                                color = c.labelTertiary,
                            )
                        }
                        if (isFound) {
                            Icon(Icons.Filled.Check, "Found", tint = c.accent, modifier = Modifier.size(20.dp))
                        } else {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.labelTertiary, strokeWidth = 2.dp)
                        }
                    }
                    if (i < rows.lastIndex) OTDivider(startIndent = 68.dp)
                }
            }
        }
    }
}

// MARK: - 6.2 One thing missing

@Composable
private fun AddMissingDateStep(draft: AddDraft, onCancel: () -> Unit, onContinue: (LocalDateTime?) -> Unit) {
    val c = OT.colors
    val today = remember { LocalDate.now() }
    val tomorrow = remember { today.plusDays(1) }
    val friday = remember { today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)) }
    var choice by remember { mutableStateOf<Int?>(null) } // 0 tomorrow · 1 Friday · 2 picked · 3 no date yet
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        SheetHeader("", leading = "Cancel", onLeading = onCancel)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LargeTitle("One thing missing", subtitle = "Everything else is filled in from the notice.")
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
                    ) { Icon(Icons.Outlined.CalendarMonth, null, tint = c.due, modifier = Modifier.size(22.dp)) }
                    Column {
                        Text("When is it due?", style = OT.type.headline, color = c.labelPrimary)
                        Text("The notice didn't give a date.", style = OT.type.footnote, color = c.labelSecondary)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddChip("Tomorrow", choice == 0) { choice = 0 }
                    AddChip(friday.format(addDueDay), choice == 1) { choice = 1 }
                    AddChip(picked?.format(addDueDay) ?: "Pick a date", choice == 2) { showPicker = true }
                    AddChip("No date yet", choice == 3) { choice = 3 }
                }
            }

            SectionLabel("From the notice", Modifier.padding(top = 4.dp))
            Column {
                AddValueRow(draft.project.ifEmpty { "—" }, "Project")
                AddValueRow(draft.role.ifEmpty { "—" }, "Your role")
                AddValueRow("${draft.scenes} scenes, your lines found", "Sides")
                AddValueRow(
                    draft.asks.joinToString(", ").lowercase().replaceFirstChar { it.titlecase(Locale.US) }.ifEmpty { "—" },
                    "They asked for",
                    showDivider = false,
                )
            }
        }
        PrimaryButton(
            if (choice == null) "Choose a due date first" else "Continue",
            onClick = {
                val due: LocalDateTime? = when (choice) {
                    0 -> tomorrow.atTime(10, 0)
                    1 -> friday.atTime(10, 0)
                    2 -> picked?.atTime(10, 0)
                    else -> null
                }
                onContinue(due)
            },
            enabled = choice != null,
            modifier = Modifier.padding(horizontal = OT.dims.gutter, vertical = 8.dp),
        )
    }

    if (showPicker) {
        AddDatePickerDialog(
            initial = picked ?: tomorrow,
            onPick = {
                picked = it
                choice = 2
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AddValueRow(value: String, label: String, showDivider: Boolean = true) {
    val c = OT.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = OT.type.body, color = c.labelPrimary)
        Text(label, style = OT.type.footnote, color = c.labelTertiary)
    }
    if (showDivider) OTDivider()
}

@Composable
private fun AddChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OT.colors
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else c.surfaceCard)
            .then(if (selected) Modifier else Modifier.border(0.5.dp, c.separator, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) c.labelOnAccent else c.labelPrimary,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDatePickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = OT.colors
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Done", color = c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.labelSecondary) }
        },
    ) { DatePicker(state = state) }
}

// MARK: - 6c Here's what they want (review + save)

private fun addAskIcon(ask: String): ImageVector = when (ask) {
    "Slate first" -> Icons.Outlined.Badge
    "Landscape" -> Icons.Outlined.Smartphone
    "Full length" -> Icons.Outlined.AccessibilityNew
    "Both profiles" -> Icons.Outlined.Portrait
    "Under 3 min" -> Icons.Outlined.Timer
    else -> Icons.AutoMirrored.Filled.Send
}

private fun addAskDetail(ask: String, slateName: String, role: String): String = when (ask) {
    "Slate first" -> "Name, height, city"
    "Landscape" -> "Reader off camera"
    "Full length" -> "After the slate"
    "Both profiles" -> "Left and right"
    "Under 3 min" -> "Tape check warns you"
    else -> {
        val parts = slateName.trim().split(" ").filter { it.isNotEmpty() }
        val last = parts.lastOrNull().orEmpty()
        val first = parts.firstOrNull().orEmpty()
        listOf(last, first, role).filter { it.isNotEmpty() }.joinToString("_").uppercase()
    }
}

@Composable
private fun AddAskIconView(ask: String, tint: Color, size: Dp) {
    Icon(
        addAskIcon(ask),
        null,
        tint = tint,
        modifier = Modifier.size(size).then(if (ask == "Landscape") Modifier.rotate(90f) else Modifier),
    )
}

@Composable
private fun AddReviewStep(
    draft: AddDraft,
    slateName: String,
    onChange: (AddDraft) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
) {
    val c = OT.colors
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(c.fillField)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Edit", style = OT.type.subhead, color = c.labelPrimary) }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LargeTitle(
                "Here's what they want",
                subtitle = "${draft.asks.size + 1} things found in the email. Tap any line to change it, then save.",
            )

            SectionLabel("Due")
            Box(Modifier.fillMaxWidth().clickable { showPicker = true }.padding(vertical = 2.dp)) {
                val due = draft.due
                if (due != null) {
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), due.toLocalDate())
                    val dayText = when {
                        days <= 0L -> "today"
                        days == 1L -> "1 day"
                        else -> "$days days"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(due.format(addDueDay), style = OT.type.title, color = c.labelPrimary, modifier = Modifier.alignByBaseline())
                        Text("${due.otTime} · $dayText", style = OT.type.headline, color = c.due, modifier = Modifier.alignByBaseline())
                    }
                } else {
                    Text("Add a due date", style = OT.type.headline, color = c.accent, modifier = Modifier.heightIn(min = 48.dp).padding(top = 12.dp))
                }
            }

            SectionLabel("They asked for")
            val cells = listOf(Triple("__role", "${draft.role.ifEmpty { "Your role" }} · ${draft.scenes} scenes", "Your lines found")) +
                draft.asks.map { Triple(it, it, addAskDetail(it, slateName, draft.role)) }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                cells.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (key, title, detail) ->
                            Column(
                                Modifier.weight(1f).clickable(onClick = onEdit),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (key == "__role") {
                                    Icon(Icons.Outlined.Person, null, tint = c.labelSecondary, modifier = Modifier.size(22.dp))
                                } else {
                                    AddAskIconView(key, c.labelSecondary, 22.dp)
                                }
                                Text(title, style = OT.type.headline, color = c.labelPrimary)
                                if (detail.isNotEmpty()) Text(detail, style = OT.type.footnote, color = c.labelTertiary)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SectionLabel("Details", Modifier.padding(top = 4.dp))
            AddTextField(draft.project, "Project") { onChange(draft.copy(project = it)) }
            AddTextField(draft.role, "Role") { onChange(draft.copy(role = it)) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.fillField)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Scenes: ${draft.scenes}", style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
                AddStepper(draft.scenes, 0..6) { onChange(draft.copy(scenes = it)) }
            }
            ListRow(
                title = draft.destination.label,
                detail = draft.destination.spec,
                icon = Icons.AutoMirrored.Filled.Send,
                value = "Change",
                valueColor = c.accent,
                showChevron = false,
                showDivider = false,
                onClick = {
                    val all = Destination.entries
                    onChange(draft.copy(destination = all[(all.indexOf(draft.destination) + 1) % all.size]))
                },
            )
        }
        PrimaryButton(
            "Save audition",
            onClick = onSave,
            modifier = Modifier.padding(horizontal = OT.dims.gutter, vertical = 8.dp),
        )
    }

    if (showPicker) {
        AddDatePickerDialog(
            initial = draft.due?.toLocalDate() ?: LocalDate.now().plusDays(1),
            onPick = { date ->
                val time = draft.due?.toLocalTime()
                onChange(draft.copy(due = if (time != null) date.atTime(time) else date.atTime(10, 0)))
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AddTextField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val c = OT.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = OT.type.body.copy(color = c.labelPrimary),
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.fillField)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = OT.type.body, color = c.labelTertiary)
                inner()
            }
        },
    )
}

@Composable
private fun AddStepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = OT.colors
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.fillField),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp, 40.dp)
                .clickable(enabled = value > range.first, role = Role.Button) { onChange(value - 1) },
            contentAlignment = Alignment.Center,
        ) { Text("−", style = OT.type.headline, color = if (value > range.first) c.labelPrimary else c.labelTertiary) }
        Box(Modifier.size(0.5.dp, 20.dp).background(c.separator))
        Box(
            Modifier
                .size(48.dp, 40.dp)
                .clickable(enabled = value < range.last, role = Role.Button) { onChange(value + 1) },
            contentAlignment = Alignment.Center,
        ) { Text("+", style = OT.type.headline, color = if (value < range.last) c.labelPrimary else c.labelTertiary) }
    }
}

// MARK: - 6c.1 Edit what they want

private val addBeforeScenes = setOf("Slate first", "Full length", "Both profiles")

@Composable
private fun AddEditAsksStep(draft: AddDraft, slateName: String, onCancel: () -> Unit, onDone: (AddDraft) -> Unit) {
    val c = OT.colors
    var working by remember { mutableStateOf(draft) }
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        SheetHeader(
            "What they want",
            leading = "Cancel",
            trailing = "Done",
            onLeading = onCancel,
            onTrailing = { onDone(working) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "OneTake read this from the email. It can miss things — add, remove or change any step. Only what's here becomes a step.",
                style = OT.type.callout,
                color = c.labelSecondary,
            )

            SectionLabel("Scenes", Modifier.padding(top = 6.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AddRemoveDot("Remove scene clips") { working = working.copy(scenes = 0) }
                Icon(Icons.Outlined.Movie, null, tint = c.labelSecondary, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scene clips · ${working.scenes}", style = OT.type.body, color = c.labelPrimary)
                    if (working.scenes > 0) {
                        Text(
                            (1..working.scenes).joinToString(" · ") { "Scene $it" },
                            style = OT.type.footnote,
                            color = c.labelTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AddStepper(working.scenes, 0..6) { working = working.copy(scenes = it) }
            }

            val before = working.asks.filter { it in addBeforeScenes }
            val after = working.asks.filter { it !in addBeforeScenes }
            if (before.isNotEmpty()) {
                SectionLabel("Before the scenes", Modifier.padding(top = 6.dp))
                Column {
                    before.forEachIndexed { i, ask ->
                        AddEditAskRow(ask, addAskDetail(ask, slateName, working.role), showDivider = i < before.lastIndex) {
                            working = working.copy(asks = working.asks - ask)
                        }
                    }
                }
            }
            if (after.isNotEmpty()) {
                SectionLabel("How it's shot and sent", Modifier.padding(top = 6.dp))
                Column {
                    after.forEachIndexed { i, ask ->
                        AddEditAskRow(ask, addAskDetail(ask, slateName, working.role), showDivider = i < after.lastIndex) {
                            working = working.copy(asks = working.asks - ask)
                        }
                    }
                }
            }

            Box(Modifier.padding(top = 8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.accentSoft)
                        .clickable(role = Role.Button) { showAdd = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, null, tint = c.labelOnAccent, modifier = Modifier.size(16.dp))
                    }
                    Text("Add something they asked for", style = OT.type.headline, color = c.accent)
                }
                val suggestions = listOf("Slate first", "Full length", "Both profiles", "Landscape", "Under 3 min", working.destination.label)
                    .filter { it !in working.asks }
                DropdownMenu(
                    expanded = showAdd,
                    onDismissRequest = { showAdd = false },
                    modifier = Modifier.widthIn(min = 220.dp).background(c.surfaceCard),
                ) {
                    if (suggestions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Everything's already here", style = OT.type.body, color = c.labelTertiary) },
                            onClick = { showAdd = false },
                            enabled = false,
                        )
                    }
                    suggestions.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s, style = OT.type.body, color = c.labelPrimary) },
                            onClick = {
                                working = working.copy(asks = working.asks + s)
                                showAdd = false
                            },
                            leadingIcon = { AddAskIconView(s, c.labelSecondary, 20.dp) },
                        )
                    }
                }
            }
            Text(
                "No scene clip this time? Set Scene clips to 0 — the slate and shots still work.",
                style = OT.type.footnote,
                color = c.labelTertiary,
            )
        }
    }
}

@Composable
private fun AddEditAskRow(ask: String, detail: String, showDivider: Boolean, onRemove: () -> Unit) {
    val c = OT.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AddRemoveDot("Remove $ask", onRemove)
        AddAskIconView(ask, c.labelSecondary, 22.dp)
        Column(Modifier.weight(1f)) {
            Text(ask, style = OT.type.body, color = c.labelPrimary)
            if (detail.isNotEmpty()) Text(detail, style = OT.type.footnote, color = c.labelTertiary)
        }
    }
    if (showDivider) OTDivider(startIndent = 82.dp)
}

/** Red "remove" dot (iOS edit-mode minus). */
@Composable
private fun AddRemoveDot(label: String, onClick: () -> Unit) {
    val c = OT.colors
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(c.danger), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp, 2.dp).clip(CircleShape).background(Color.White))
        }
    }
}

// MARK: - S4 Notice couldn't be read

@Composable
private fun AddErrorStep(onCancel: () -> Unit, onRetry: () -> Unit, onScan: () -> Unit, onType: () -> Unit) {
    val c = OT.colors
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        SheetHeader("", leading = "Cancel", onLeading = onCancel)
        Column(
            Modifier
                .weight(1f)
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
