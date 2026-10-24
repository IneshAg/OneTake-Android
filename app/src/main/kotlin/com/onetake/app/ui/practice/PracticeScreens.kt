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
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.onetake.app.ui.system.rememberHaptics
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
                val targetId = store.practice.firstOrNull()?.id
                    ?: store.auditions.firstOrNull()?.id
                    ?: run {
                        store.addPractice(com.onetake.app.data.DefaultRepertoire.defaultPiece)
                        com.onetake.app.data.DefaultRepertoire.defaultPiece.id
                    }
                when (mode) {
                    LineLearningMode.FIRST_LETTER -> {
                        PrepPrefs.mode = PrepLineMode.FirstLetters
                        nav.push(Route.RunLines(targetId, 0))
                    }
                    LineLearningMode.MASK_AND_REVEAL -> {
                        PrepPrefs.mode = PrepLineMode.MaskAndReveal
                        nav.push(Route.RunLines(targetId, 0))
                    }
                    LineLearningMode.AUDIO_GAP -> {
                        PrepPrefs.mode = PrepLineMode.AudioGap
                        nav.push(Route.RunLines(targetId, 0))
                    }
                    LineLearningMode.HANDS_FREE_WALK -> {
                        nav.push(Route.AudioWalk(targetId, 0))
                    }
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
                    nav.push(Route.RunLines(piece.id, 0))
                },
                onAddScriptClick = { nav.push(Route.PracticeSetup) },
            )
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

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            isOcrProcessing = true
            scope.launch {
                val result = OcrManager.recognizeTextFromBitmap(bitmap)
                val text = result.getOrNull().orEmpty().trim()
                if (text.isNotBlank()) {
                    textInput = text
                }
                isOcrProcessing = false
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isOcrProcessing = true
            scope.launch {
                val result = OcrManager.recognizeTextFromUri(context, uri)
                val text = result.getOrNull().orEmpty().trim()
                if (text.isNotBlank()) {
                    textInput = text
                }
                isOcrProcessing = false
            }
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isOcrProcessing = true
            scope.launch {
                val result = OcrManager.recognizeTextFromUri(context, uri)
                val text = result.getOrNull().orEmpty().trim()
                if (text.isNotBlank()) {
                    textInput = text
                }
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
                            Text("Paste dialogue, scan pages, or import sides PDF...\nExample:\nSAM: Hello\nNINA: Hi", style = OT.type.body, color = c.labelTertiary)
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
                                    textInput = clipText
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
                                    scanLauncher.launch(null)
                                } catch (e: Exception) {
                                    photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.DocumentScanner, null, tint = c.labelPrimary, modifier = Modifier.size(14.dp))
                            Text("Scan sides", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
                        }
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(c.fillQuiet)
                            .clickable {
                                fileLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain"))
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Folder, null, tint = c.labelSecondary, modifier = Modifier.size(14.dp))
                            Text("PDF / Photo", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Continue to character setup",
                onClick = { if (textInput.isNotBlank()) onPicked(textInput, titleInput) },
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
            Spacer(Modifier.height(12.dp))
            PracticeLockNote()
        }
    }
}

@Composable
private fun PracticeLockNote() {
    val c = OT.colors
    Row(
        Modifier.clip(CircleShape).background(c.fillQuiet).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Lock, null, tint = c.labelSecondary, modifier = Modifier.size(12.dp))
        Text("Practice pieces and takes stay on this phone", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.labelSecondary)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            characters.take(3).forEach { char ->
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
        Spacer(Modifier.height(12.dp))
        PracticeLockNote()
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
fun PracticeTakeScreen() {
    val nav = LocalNavigator.current
    val camera = rememberCameraController()
    val haptics = rememberHaptics()
    var front by remember { mutableStateOf(true) }
    val recording = camera.state == CameraController.State.Recording
    KeepScreenOn()

    Box(Modifier.fillMaxSize().background(OT.camera.bg)) {
        CameraBackground(camera, Modifier.fillMaxSize(), front = front)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = OT.dims.gutter),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Filled.Close, "Close", onClick = { nav.closeCamera() }, onDark = true)
                Spacer(Modifier.weight(1f))
                val secs = camera.elapsed.toInt()
                CameraPill("Practice · TK 4  %02d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60))
            }
            Spacer(Modifier.height(8.dp))
            // Top of the scene, so the actor can glance at their first line.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(OT.camera.glass).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("TOP OF THE SCENE", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = OT.camera.label2)
                Text("SAM · YOU", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = OT.camera.label2)
                Text("You said you’d be home by eight.", style = OT.type.headline.copy(fontSize = 20.sp, lineHeight = 25.sp), color = OT.camera.label)
                Text("NINA", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = OT.camera.label2.copy(alpha = 0.5f))
                Text("I said I’d try. There’s a difference.", style = OT.type.footnote, color = OT.camera.label2.copy(alpha = 0.5f))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CameraPill("Practice · no slate, never sent", icon = Icons.Filled.Lock, background = Color.Black.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                PracticeRecordButton(recording) {
                    haptics.impact()
                    if (recording) camera.stopRecording() else camera.startRecording()
                }
                CircleIconButton(
                    Icons.Filled.Cameraswitch,
                    "Switch camera",
                    onClick = { front = !front },
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
