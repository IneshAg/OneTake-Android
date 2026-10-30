package com.onetake.app.ui.practice

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.onetake.app.models.formatDuration
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.onetake.app.data.ScriptParser
import com.onetake.app.ocr.OcrManager
import kotlinx.coroutines.launch
import com.onetake.app.models.PracticePiece
import com.onetake.app.ui.prepare.PrepLineMode
import com.onetake.app.ui.prepare.PrepPrefs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.CameraFlow
import com.onetake.app.TabBarClearance
import com.onetake.app.ui.camera.CameraBackground
import com.onetake.app.ui.camera.FramingAssistOverlay
import com.onetake.app.ui.camera.LiveTapeCheckEngine
import com.onetake.app.ui.camera.RecordStartErrorDialog
import com.onetake.app.ui.camera.CameraController
import com.onetake.app.ui.camera.rememberCameraController
import com.onetake.app.ui.components.CameraPill
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.IconRow
import com.onetake.app.ui.components.LargeTitle
import com.onetake.app.ui.components.ListRow
import com.onetake.app.ui.components.OTCard
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.ProgressRing
import com.onetake.app.ui.components.PushedScreen
import com.onetake.app.ui.components.ReadinessDashes
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.system.KeepScreenOn
import com.onetake.app.ui.system.VoiceCommand
import com.onetake.app.ui.system.fileProviderUri
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.system.rememberVoiceCommandEngine
import java.io.File
import com.onetake.app.ui.components.SFSymbols
import com.onetake.app.ui.practice.components.PracticeTopBar
import com.onetake.app.ui.practice.components.CompactDrillGrid
import com.onetake.app.ui.practice.components.PracticeRepertoireList
import com.onetake.app.ui.theme.OT

// MARK: - 24 Practice (tab root) - System 2 Monolithic Studio

@Composable
fun PracticeScreen(
    onOpenProfile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val c = OT.colors
    val isRepertoireEmpty = store.practice.isEmpty()
    val scrollState = rememberScrollState()
    var selectedPiece by remember { mutableStateOf<PracticePiece?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .then(if (!isRepertoireEmpty) Modifier.verticalScroll(scrollState) else Modifier)
            .statusBarsPadding()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = TabBarClearance),
    ) {
        // 1. Top Action Bar: Bold "Practice" title + [ + Import ] + [ Avatar ]
        PracticeTopBar(
            onAddClick = { nav.push(Route.PracticeSetup) },
            onAvatarClick = { onOpenProfile?.invoke() },
        )

        Spacer(Modifier.height(18.dp))

        // 2. Compact 2x2 Drill Suite (Option 1) - ALWAYS visible on practice tab
        CompactDrillGrid(
            onSelectMode = { mode ->
                when (mode) {
                    LineLearningMode.FIRST_LETTER -> PrepPrefs.mode = PrepLineMode.FirstLetters
                    LineLearningMode.MASK_AND_REVEAL -> PrepPrefs.mode = PrepLineMode.MaskAndReveal
                    LineLearningMode.AUDIO_GAP -> PrepPrefs.mode = PrepLineMode.AudioGap
                    LineLearningMode.HANDS_FREE_WALK -> Unit
                }
                val targetId = store.practice.firstOrNull()?.id ?: store.auditions.firstOrNull()?.id
                if (targetId != null) {
                    if (mode == LineLearningMode.HANDS_FREE_WALK) {
                        nav.push(Route.AudioWalk(targetId, 0))
                    } else {
                        nav.push(Route.RunLines(targetId, 0))
                    }
                } else {
                    nav.push(Route.PracticeSetup)
                }
            },
        )

        Spacer(Modifier.height(24.dp))

        if (isRepertoireEmpty) {
            // Centered empty state below Quick Drills with theater masks symbol
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = SFSymbols.TheaterMasksFill,
                        contentDescription = null,
                        tint = Color(0xFF86868B),
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Practice",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF86868B),
                    )
                }
            }
        } else {
            // 3. Inset Roster: Saved Scripts & Assigned Voice Partners
            PracticeRepertoireList(
                pieces = store.practice,
                onPieceClick = { piece ->
                    selectedPiece = piece
                },
                onAddScriptClick = { nav.push(Route.PracticeSetup) },
            )
        }
    }

    selectedPiece?.let { piece ->
        PracticePieceActionSheet(
            piece = piece,
            onDismiss = { selectedPiece = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticePieceActionSheet(
    piece: PracticePiece,
    onDismiss: () -> Unit,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val c = OT.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val practiceTakes = store.takes.filter { it.auditionId == piece.id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surfaceCard,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(piece.title, style = OT.type.title, color = c.labelPrimary)
                    Text("${piece.detail} · ${piece.linesLearned} of ${piece.linesTotal} lines off-book", style = OT.type.footnote, color = c.labelTertiary)
                }
                CircleIconButton(Icons.Filled.Close, "Close", onClick = onDismiss)
            }

            OTDivider()

            PracticeActionRow(
                icon = Icons.Filled.PlayArrow,
                title = "Run Lines (Voice Rehearsal)",
                subtitle = "Speak your cues and listen to your reader partner",
                onClick = {
                    onDismiss()
                    nav.push(Route.RunLines(piece.id, 0))
                },
            )

            PracticeActionRow(
                icon = Icons.Filled.Edit,
                title = "Prepare & Read Script",
                subtitle = "Full sides, line cues, teleprompter speed and edits",
                onClick = {
                    onDismiss()
                    nav.push(Route.Prepare(piece.id, 0))
                },
            )

            PracticeActionRow(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                title = "Hands-Free Audio Walk",
                subtitle = "Continuous looped TTS reading in your earphones",
                onClick = {
                    onDismiss()
                    nav.push(Route.AudioWalk(piece.id, 0))
                },
            )

            PracticeActionRow(
                icon = Icons.Filled.PhotoCamera,
                title = "Record Practice Take",
                subtitle = "Step into camera framing with teleprompter and partner audio",
                onClick = {
                    onDismiss()
                    nav.openCamera(CameraFlow.PracticeTake(piece.id))
                },
            )

            if (practiceTakes.isNotEmpty()) {
                PracticeActionRow(
                    icon = Icons.Filled.Movie,
                    title = "View Practice Takes (${practiceTakes.size})",
                    subtitle = "Compare takes, trim clips, review performance",
                    onClick = {
                        onDismiss()
                        nav.push(Route.Takes(piece.id))
                    },
                )
            }

            PracticeActionRow(
                icon = Icons.Filled.Edit,
                title = "Edit Piece Details",
                subtitle = "Change script title or character role",
                onClick = { showEdit = true },
            )

            PracticeActionRow(
                icon = Icons.Filled.Delete,
                title = "Delete Piece",
                subtitle = "Permanently remove this script from practice",
                destructive = true,
                onClick = { showDelete = true },
            )
        }
    }

    if (showEdit) {
        PracticePieceEditDialog(
            piece = piece,
            onDismiss = {
                showEdit = false
                onDismiss()
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = c.surfaceCard,
            title = { Text("Delete “${piece.title}”?", style = OT.type.headline, color = c.labelPrimary) },
            text = { Text("This will permanently remove the script and any practice takes recorded for it.", style = OT.type.body, color = c.labelSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    store.deletePracticePiece(piece.id)
                    haptics.tick()
                    onDismiss()
                }) {
                    Text("Delete", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel", color = c.labelSecondary)
                }
            },
        )
    }
}

@Composable
private fun PracticePieceEditDialog(
    piece: PracticePiece,
    onDismiss: () -> Unit,
) {
    val store = LocalAppStore.current
    val c = OT.colors
    var title by remember(piece.id) { mutableStateOf(piece.title) }
    var detail by remember(piece.id) { mutableStateOf(piece.detail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceCard,
        title = { Text("Edit Practice Piece", style = OT.type.headline, color = c.labelPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Script Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Role / Scene Partner") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim().ifEmpty { piece.title }
                val d = detail.trim().ifEmpty { piece.detail }
                store.updatePracticePiece(piece.id, title = t, character = d)
                onDismiss()
            }) {
                Text("Save", color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.labelSecondary) }
        },
    )
}

@Composable
private fun PracticeActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val c = OT.colors
    val fg = if (destructive) c.danger else c.labelPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (destructive) c.danger.copy(alpha = 0.15f) else Color(0x1AFFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = OT.type.body.copy(fontWeight = FontWeight.SemiBold), color = fg)
            Text(subtitle, style = OT.type.caption, color = c.labelTertiary)
        }
    }
}

// MARK: - 24b / 24c Practise a scene

@Composable
fun PracticeSetupScreen() {
    var step by rememberSaveable { mutableStateOf(0) }
    var scriptText by rememberSaveable { mutableStateOf("") }
    var sceneTitle by rememberSaveable { mutableStateOf("Practice Scene") }

    BackHandler(enabled = step == 1) { step = 0 }
    if (step == 0) {
        PracticeSourceStep(
            initialText = scriptText,
            onPicked = { text, title ->
                scriptText = text
                sceneTitle = title
                step = 1
            }
        )
    } else {
        PracticeSceneStep(
            scriptText = scriptText,
            sceneTitle = sceneTitle,
            onBack = { step = 0 }
        )
    }
}

@Composable
private fun PracticeSourceStep(
    initialText: String,
    onPicked: (String, String) -> Unit,
) {
    val nav = LocalNavigator.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = LocalAppStore.current
    val c = OT.colors

    var textInput by rememberSaveable { mutableStateOf(initialText) }
    var titleInput by rememberSaveable { mutableStateOf("Practice Scene") }
    var isOcrProcessing by remember { mutableStateOf(false) }
    var pendingScanFile by remember { mutableStateOf<File?>(null) }

    fun applyImportedScript(imported: String) {
        val cleaned = imported.trim()
        if (cleaned.isBlank()) return
        val combined = if (textInput.isBlank()) cleaned else "$textInput\n\n$cleaned"
        textInput = combined
        if (titleInput.isBlank() || titleInput == "Practice Scene") {
            val suggested = ScriptParser.parseScenes(combined, myCharacter = "Me")
                .firstOrNull()
                ?.title
                ?.takeIf { it.isNotBlank() && !it.startsWith("Scene ", ignoreCase = true) }
            if (suggested != null) {
                titleInput = suggested
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = pendingScanFile
        pendingScanFile = null
        if (captured && file != null && file.exists()) {
            isOcrProcessing = true
            scope.launch {
                val uri = fileProviderUri(context, file)
                val result = OcrManager.recognizeTextFromUri(context, uri)
                runCatching { file.delete() }
                applyImportedScript(result.getOrNull().orEmpty())
                isOcrProcessing = false
            }
        } else {
            runCatching { file?.delete() }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isOcrProcessing = true
            scope.launch {
                val result = OcrManager.recognizeTextFromUri(context, uri)
                applyImportedScript(result.getOrNull().orEmpty())
                isOcrProcessing = false
            }
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            isOcrProcessing = true
            scope.launch {
                val result = OcrManager.recognizeTextFromUris(context, uris)
                applyImportedScript(result.getOrNull().orEmpty())
                isOcrProcessing = false
            }
        }
    }

    val recentSides = remember(store.auditions, store.practice) {
        val fromAuditions = store.auditions.flatMap { a ->
            a.scenes.filter { it.lines.isNotEmpty() }.map { s ->
                "${a.project} · ${s.title}" to (s.lines.joinToString("\n") { "${it.character}: ${it.text}" } to s.title)
            }
        }
        val fromPractice = store.practice.mapNotNull { p ->
            p.scene?.takeIf { it.lines.isNotEmpty() }?.let { s ->
                p.title to (s.lines.joinToString("\n") { "${it.character}: ${it.text}" } to s.title)
            }
        }
        fromAuditions + fromPractice
    }

    Column(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = OT.dims.gutter).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { nav.back() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.heightIn(min = 38.dp).clip(CircleShape).background(c.surfaceCard)
                        .border(0.5.dp, c.separator, CircleShape).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", style = OT.type.body, color = c.labelPrimary) }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter).padding(bottom = 24.dp).navigationBarsPadding(),
        ) {
            LargeTitle(
                "Practise a scene",
                subtitle = "Any monologue or scene. OneTake finds the characters and you pick yours.",
            )
            Spacer(Modifier.height(16.dp))

            // Editable Scene Title
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceCard)
                    .border(0.5.dp, c.separator, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "SCENE TITLE",
                    style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                    color = c.labelSecondary,
                )
                BasicTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    textStyle = OT.type.headline.copy(color = c.labelPrimary),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (titleInput.isBlank()) {
                            Text("Scene title", style = OT.type.headline, color = c.labelTertiary)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))

            // Script Input Box (Obsidian Studio Surface)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceCard)
                    .border(0.5.dp, c.separator, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SCRIPT TEXT",
                        style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                        color = c.labelSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (isOcrProcessing) {
                        Text("Reading script with OCR…", style = OT.type.caption, color = c.accent)
                    } else if (textInput.isNotBlank()) {
                        Text(
                            "Clear",
                            style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = c.danger,
                            modifier = Modifier.clip(CircleShape).clickable { textInput = "" }.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                BasicTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    textStyle = OT.type.body.copy(color = c.labelPrimary, lineHeight = 22.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                    decorationBox = { innerTextField ->
                        if (textInput.isBlank()) {
                            Text("Paste dialogue, scan pages, or import sides PDF…", style = OT.type.body, color = c.labelTertiary)
                        }
                        innerTextField()
                    }
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.fillQuiet)
                            .clickable {
                                val clipText = clipboard.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    applyImportedScript(clipText)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.ContentPaste, null, tint = c.labelPrimary, modifier = Modifier.size(14.dp))
                            Text("Paste", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
                        }
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.fillQuiet)
                            .clickable {
                                try {
                                    val tempFile = File(context.cacheDir, "ocr-practice-${System.currentTimeMillis()}.jpg")
                                    pendingScanFile = tempFile
                                    scanLauncher.launch(fileProviderUri(context, tempFile))
                                } catch (e: Exception) {
                                    pendingScanFile = null
                                    photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.DocumentScanner, null, tint = c.labelPrimary, modifier = Modifier.size(14.dp))
                            Text(
                                if (textInput.isBlank()) "Scan sides" else "+ Scan page",
                                style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                                color = c.labelPrimary,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.fillQuiet)
                            .clickable {
                                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Image, null, tint = c.labelSecondary, modifier = Modifier.size(14.dp))
                            Text("Photos", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelSecondary)
                        }
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.fillQuiet)
                            .clickable {
                                fileLauncher.launch(arrayOf("application/pdf", "text/plain", "application/rtf", "text/rtf", "image/*"))
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Folder, null, tint = c.labelSecondary, modifier = Modifier.size(14.dp))
                            Text("PDF / File", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Continue to character setup",
                onClick = { if (textInput.isNotBlank()) onPicked(textInput, titleInput.ifBlank { "Practice Scene" }) },
                enabled = textInput.isNotBlank(),
            )

            if (recentSides.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Recent sides")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentSides.forEach { (label, data) ->
                        val (text, title) = data
                        Box(
                            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = {
                                titleInput = title
                                onPicked(text, title)
                            }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.heightIn(min = 36.dp).clip(CircleShape).background(c.surfaceCard)
                                    .border(0.5.dp, c.separator, CircleShape).padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeSceneStep(
    scriptText: String,
    sceneTitle: String,
    onBack: () -> Unit,
) {
    val nav = LocalNavigator.current
    val store = LocalAppStore.current
    val c = OT.colors
    val detectedCharacters = remember(scriptText) { ScriptParser.extractCharacters(scriptText) }
    val characters = if (detectedCharacters.isNotEmpty()) detectedCharacters else listOf("Me")
    var playing by rememberSaveable(detectedCharacters) { mutableStateOf(characters.first()) }
    var reader by rememberSaveable { mutableStateOf("Automated Partner (TTS)") }

    val parsedScene = remember(scriptText, playing, sceneTitle) {
        ScriptParser.parse(scriptText, myCharacter = playing, sceneTitle = sceneTitle)
    }

    PushedScreen(
        onBack = onBack,
        bottomBar = {
            PrimaryButton(
                "Start practising",
                onClick = {
                    val piece = PracticePiece(
                        title = sceneTitle.ifBlank { "Practice Scene" },
                        detail = "$playing · ${parsedScene.lines.size} lines",
                        linesLearned = 0,
                        linesTotal = parsedScene.lines.count { it.isMine },
                        character = playing,
                        scene = parsedScene,
                    )
                    store.addPractice(piece)
                    nav.push(Route.RunLines(piece.id, 0))
                }
            )
        },
    ) {
        LargeTitle(sceneTitle)
        Text("${characters.size} characters · ${parsedScene.lines.size} lines · about ${formatDuration(parsedScene.estimatedSeconds)}", style = OT.type.footnote, color = c.labelSecondary)
        Spacer(Modifier.height(16.dp))

        SectionLabel("Who are you playing?")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            characters.chunked(2).forEach { rowChars ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowChars.forEach { char ->
                        val isSelected = playing.equals(char, ignoreCase = true)
                        val lineCount = parsedScene.lines.count { it.character.equals(char, ignoreCase = true) }
                        PracticeCharacterCard(
                            name = char,
                            detail = if (isSelected) "You play $char · $lineCount lines" else "$lineCount lines",
                            selected = isSelected,
                            modifier = Modifier.weight(1f),
                            onClick = { playing = char }
                        )
                    }
                    if (rowChars.size == 1 && characters.size > 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Dialogue Preview: Displays the actual parsed scene lines
        SectionLabel("First lines")
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceSunken).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parsedScene.lines.take(4).forEach { line ->
                val isMine = line.isMine
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (isMine) "${line.character} · YOU" else line.character,
                        style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isMine) c.accent else c.reader,
                    )
                    Text(
                        line.text,
                        style = if (isMine) OT.type.headline else OT.type.callout,
                        color = if (isMine) c.labelPrimary else c.labelSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("Partner cue reader")
        PracticeReaderOption("Automated Partner (TTS)", "Speech synthesizer speaks partner lines on cue", reader == "Automated Partner (TTS)", showDivider = true) { reader = "Automated Partner (TTS)" }
        PracticeReaderOption("Nobody", "I’ll hear the cues in my head", reader == "Nobody", showDivider = false) { reader = "Nobody" }
    }
}

@Composable
private fun PracticeCharacterCard(name: String, detail: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = OT.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surfaceCard)
            .border(if (selected) 1.5.dp else 0.5.dp, if (selected) c.accent else c.separator, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(name, style = OT.type.headline.copy(fontSize = 19.sp), color = if (selected) c.accent else c.labelPrimary)
        Text(detail, style = OT.type.footnote, color = c.labelSecondary)
    }
}

@Composable
private fun PracticeReaderOption(title: String, detail: String, selected: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    val c = OT.colors
    ListRow(
        title = title,
        detail = detail,
        showChevron = false,
        showDivider = showDivider,
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        trailing = {
            if (selected) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = c.labelOnAccent, modifier = Modifier.size(14.dp))
                }
            } else {
                Box(Modifier.size(22.dp).border(1.5.dp, c.labelTertiary, CircleShape))
            }
        },
    )
}

// MARK: - 24e Practice take (no slate, never sent) — always dark

@Composable
fun PracticeTakeScreen(practicePieceId: String? = null) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val camera = rememberCameraController()
    val haptics = rememberHaptics()
    val reader = com.onetake.app.ui.system.rememberReaderPlayer()
    val tts = com.onetake.app.ui.system.rememberTtsEngine()
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf(true) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var readerSpeaking by remember { mutableStateOf(false) }
    var lineIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var timerSeconds by remember { androidx.compose.runtime.mutableIntStateOf(3) }
    var liveTapeCheck by remember { mutableStateOf(LiveTapeCheckEngine.LiveTapeCheck()) }

    val recording = camera.state == CameraController.State.Recording
    val piece = remember(store.practice, practicePieceId) {
        if (!practicePieceId.isNullOrBlank()) {
            store.practice.firstOrNull { it.id == practicePieceId } ?: store.practice.firstOrNull()
        } else {
            store.practice.firstOrNull()
        }
    }
    KeepScreenOn()

    val liveAnalyzer = remember {
        LiveTapeCheckEngine.Analyzer(isFrontCamera = front) { check ->
            liveTapeCheck = check
        }
    }
    LaunchedEffect(front, camera.viewAspect, camera.hasMicPermission) {
        liveAnalyzer.isFrontCamera = front
        liveAnalyzer.viewAspect = camera.viewAspect
        liveAnalyzer.micPermissionGranted = camera.hasMicPermission
    }
    LaunchedEffect(camera.hasPermission) {
        if (camera.hasPermission) camera.setAnalyzer(liveAnalyzer)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { camera.clearAnalyzer() }
    }

    val scene = piece?.scene
    val sceneLines = scene?.lines.orEmpty()

    LaunchedEffect(lineIndex, recording, scene?.id) {
        reader.volume = store.readerVolumeLevel
        tts.volume = store.readerVolumeLevel
        val line = sceneLines.getOrNull(lineIndex)
        if (recording && scene != null && line != null && !line.isMine) {
            readerSpeaking = true
            val targetIdx = lineIndex
            val readerIdx = sceneLines.take(lineIndex).count { !it.isMine }
            runCatching {
                reader.playOrSynthesize(scene.id, readerIdx, line.text, tts) {
                    scope.launch {
                        readerSpeaking = false
                        if (recording && lineIndex == targetIdx && lineIndex < sceneLines.lastIndex) {
                            lineIndex = targetIdx + 1
                        }
                    }
                }
            }.onFailure {
                readerSpeaking = false
            }
        } else {
            readerSpeaking = false
            reader.stop()
            tts.stop()
        }
    }

    val appContext = context.applicationContext
    fun savePracticeTake(uri: android.net.Uri?, pieceId: String) {
        val p = store.practice.firstOrNull { it.id == pieceId } ?: return
        val number = p.takesRecorded + 1
        com.onetake.app.data.MediaStore.persist(appContext, uri, "take-$number-${System.currentTimeMillis()}.mp4", "practice-${p.id}")
            ?.let {
                store.incrementPracticeTakes(p.id)
                android.widget.Toast.makeText(appContext, "Practice take #$number saved", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdown = null
    }

    fun rollPractice() {
        if (countdown != null || recording) return
        lineIndex = 0
        if (timerSeconds == 0) {
            if (camera.startRecording()) haptics.impact() else haptics.warning()
            return
        }
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (n in timerSeconds downTo 1) {
                countdown = n
                haptics.tick()
                kotlinx.coroutines.delay(1000)
            }
            countdown = null
            countdownJob = null
            if (camera.startRecording()) haptics.impact() else haptics.warning()
        }
    }

    fun stopPracticeTake() {
        if (!recording) return
        haptics.impact()
        val pieceId = piece?.id
        camera.stopRecording { uri -> if (pieceId != null) savePracticeTake(uri, pieceId) }
    }

    val activeVoiceCommands = remember(recording, countdown) {
        when {
            countdown != null -> emptySet()
            recording -> setOf(VoiceCommand.Cut)
            else -> setOf(VoiceCommand.Record)
        }
    }
    val voiceEngine = rememberVoiceCommandEngine(
        activeCommands = activeVoiceCommands,
        enabled = voiceEnabled && camera.hasMicPermission && !readerSpeaking,
        onCommand = { cmd, _ ->
            when (cmd) {
                VoiceCommand.Record -> if (!recording && countdown == null) rollPractice()
                VoiceCommand.Cut -> if (recording) stopPracticeTake()
                else -> Unit
            }
        },
    )

    RecordStartErrorDialog(camera)
    Box(Modifier.fillMaxSize().background(OT.camera.bg)) {
        CameraBackground(camera, Modifier.fillMaxSize(), front = front)
        FramingAssistOverlay(
            check = liveTapeCheck,
            showGrid = !recording,
            showFaceReticle = !recording,
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = OT.dims.gutter),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircleIconButton(Icons.Filled.Close, "Close", onClick = { nav.closeCamera() }, onDark = true)
                Spacer(Modifier.weight(1f))
                if (voiceEngine.available) {
                    Row(
                        Modifier
                            .heightIn(min = 32.dp)
                            .clip(CircleShape)
                            .background(OT.camera.glass)
                            .clickable(role = Role.Button) { voiceEnabled = !voiceEnabled }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            if (voiceEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = if (voiceEnabled) "Voice commands on" else "Voice commands off",
                            tint = if (voiceEnabled && voiceEngine.isListening) OT.colors.accent else if (voiceEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            if (voiceEnabled) "Voice" else "Muted",
                            style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = if (voiceEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
                if (!recording) {
                    CameraPill(
                        text = if (timerSeconds == 0) "Instant" else "${timerSeconds}s timer",
                        modifier = Modifier.clickable(role = Role.Button) {
                            if (countdown == null) {
                                timerSeconds = when (timerSeconds) {
                                    0 -> 3
                                    3 -> 5
                                    else -> 0
                                }
                            }
                        },
                    )
                }
                val secs = camera.elapsed.toInt()
                val takeNumber = (piece?.takesRecorded ?: 0) + 1
                CameraPill("Practice · TK $takeNumber  %02d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60))
            }
            Spacer(Modifier.height(8.dp))

            val currentLine = sceneLines.getOrNull(lineIndex)
            val nextLine = sceneLines.getOrNull(lineIndex + 1)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OT.camera.glass)
                    .clickable(enabled = sceneLines.size > 1, role = Role.Button) {
                        if (lineIndex < sceneLines.lastIndex) lineIndex += 1
                    }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val eyebrow = if (sceneLines.isNotEmpty()) {
                    "${piece?.title?.uppercase().orEmpty()} · LINE ${lineIndex + 1} OF ${sceneLines.size}"
                } else {
                    "PRACTICE SCENE"
                }
                Text(eyebrow, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = OT.camera.label2)
                if (currentLine != null) {
                    Text(
                        if (currentLine.isMine) "${currentLine.character.uppercase()} · YOU" else currentLine.character.uppercase(),
                        style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (currentLine.isMine) OT.camera.label2 else com.onetake.app.ui.theme.DarkOT.reader,
                    )
                    Text(
                        currentLine.text,
                        style = OT.type.headline.copy(fontSize = 20.sp, lineHeight = 25.sp),
                        color = if (currentLine.isMine) OT.camera.label else OT.camera.label.copy(alpha = 0.75f),
                    )
                    if (nextLine != null) {
                        Text(
                            nextLine.character.uppercase(),
                            style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = OT.camera.label2.copy(alpha = 0.5f),
                        )
                        Text(nextLine.text, style = OT.type.footnote, color = OT.camera.label2.copy(alpha = 0.5f), maxLines = 2)
                    }
                } else {
                    Text(
                        piece?.title ?: "No practice scene attached — add one from Practice",
                        style = OT.type.headline.copy(fontSize = 18.sp),
                        color = OT.camera.label,
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                countdown?.let { n ->
                    Box(
                        Modifier.fillMaxSize().clickable { cancelCountdown() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", fontSize = 148.sp, lineHeight = 156.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            if (!recording && countdown == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CameraPill(
                        text = "${liveTapeCheck.framingLabel} · ${liveTapeCheck.lightLabel}",
                        background = Color.Black.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (voiceEnabled && voiceEngine.available && countdown == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CameraPill(
                        text = voiceEngine.lastHeard ?: if (recording) "Say \"Cut\" to stop" else "Say \"Action\" or tap record",
                        icon = Icons.Filled.Mic,
                        background = Color.Black.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CameraPill("Practice · no slate, never sent", icon = Icons.Filled.Lock, background = Color.Black.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                PracticeRecordButton(recording) {
                    when {
                        countdown != null -> cancelCountdown()
                        recording -> stopPracticeTake()
                        else -> rollPractice()
                    }
                }
                CircleIconButton(
                    Icons.Filled.Cameraswitch,
                    "Switch camera",
                    onClick = { if (!recording && countdown == null) front = !front },
                    onDark = true,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun PracticeRecordButton(isRecording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (isRecording) "Stop recording" else "Record" },
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(OT.camera.recRed))
        } else {
            Box(Modifier.size(64.dp).clip(CircleShape).background(OT.camera.recRed))
        }
    }
}
