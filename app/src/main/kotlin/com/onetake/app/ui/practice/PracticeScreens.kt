package com.onetake.app.ui.practice

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.onetake.app.ui.practice.components.PracticeTopBar
import com.onetake.app.ui.practice.components.PracticeHeroCard
import com.onetake.app.ui.practice.components.LineLearningGym
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

    val activePiece = store.practice.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = TabBarClearance),
    ) {
        // 1. Top Action Bar: Heavy "Practice" title + [ + Import ] + [ Avatar ]
        PracticeTopBar(
            onAddClick = { nav.push(Route.PracticeSetup) },
            onAvatarClick = { onOpenProfile?.invoke() },
        )

        Spacer(Modifier.height(16.dp))

        // 2. Monolithic Hero Stage Card: Interactive Scene Partner (ActOnCue "On Cue" Perform Mode)
        PracticeHeroCard(
            title = activePiece?.title ?: "Hamlet",
            subtitle = if (activePiece != null) {
                "${activePiece.detail} · Interactive Reader Active"
            } else {
                "Act III, Scene 1 · Interactive Reader Active"
            },
            eyebrow = "SCENE PARTNER · ON CUE",
            onClick = {
                val auditionId = store.auditions.firstOrNull()?.id
                if (auditionId != null) {
                    nav.push(Route.RunLines(auditionId, 0))
                } else {
                    nav.push(Route.PracticeSetup)
                }
            },
        )

        Spacer(Modifier.height(28.dp))

        // 3. Inset Gym Roster: ActOnCue-inspired Line Learning Modes
        LineLearningGym(
            onSelectMode = { mode ->
                when (mode) {
                    LineLearningMode.FIRST_LETTER,
                    LineLearningMode.MASK_AND_REVEAL,
                    LineLearningMode.AUDIO_GAP -> {
                        val auditionId = store.auditions.firstOrNull()?.id
                        if (auditionId != null) {
                            nav.push(Route.RunLines(auditionId, 0))
                        } else {
                            nav.push(Route.PracticeSetup)
                        }
                    }
                    LineLearningMode.HANDS_FREE_WALK -> {
                        nav.openCamera(CameraFlow.PracticeTake)
                    }
                }
            },
        )

        Spacer(Modifier.height(28.dp))

        // 4. Inset Roster: Saved Scripts & Assigned Voice Partners
        PracticeRepertoireList(
            pieces = store.practice,
            onPieceClick = { piece ->
                val auditionId = store.auditions.firstOrNull()?.id
                if (auditionId != null) {
                    nav.push(Route.RunLines(auditionId, 0))
                } else {
                    nav.push(Route.PracticeSetup)
                }
            },
            onAddScriptClick = { nav.push(Route.PracticeSetup) },
        )
    }
}

// MARK: - 24b / 24c Practise a scene

@Composable
fun PracticeSetupScreen() {
    var step by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = step == 1) { step = 0 }
    if (step == 0) PracticeSourceStep(onPicked = { step = 1 }) else PracticeSceneStep(onBack = { step = 0 })
}

@Composable
private fun PracticeSourceStep(onPicked: () -> Unit) {
    val nav = LocalNavigator.current
    val c = OT.colors
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
            Spacer(Modifier.height(12.dp))
            val sources = listOf(
                Triple(Icons.Filled.ContentPaste, "Paste", "A scene or monologue you copied"),
                Triple(Icons.Filled.DocumentScanner, "Scan pages", "A script from class or a book you own"),
                Triple(Icons.Filled.Folder, "PDF or file", "From your files or Google Drive"),
                Triple(Icons.Filled.Keyboard, "Type it in", "Write it out, or dictate it"),
                Triple(Icons.Filled.Movie, "From an audition", "Rework sides you already taped"),
            )
            sources.forEachIndexed { i, (icon, title, detail) ->
                IconRow(icon = icon, title = title, detail = detail, onClick = onPicked)
                if (i < sources.lastIndex) OTDivider(startIndent = 54.dp)
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Recent sides")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("The Last Summer · Sc 1", "Winter Play").forEach { name ->
                    Box(
                        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onPicked),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.heightIn(min = 36.dp).clip(CircleShape).background(c.surfaceCard)
                                .border(0.5.dp, c.separator, CircleShape).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(name, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
private fun PracticeSceneStep(onBack: () -> Unit) {
    val nav = LocalNavigator.current
    val c = OT.colors
    var playing by rememberSaveable { mutableStateOf("Sam") }
    var reader by rememberSaveable { mutableStateOf("Your recording") }
    val other = if (playing == "Sam") "Nina" else "Sam"
    val samLine = "You said you’d be home by eight."
    val ninaLine = "I said I’d try. There’s a difference."

    PushedScreen(
        onBack = onBack,
        bottomBar = {
            PrimaryButton("Start practising", onClick = { nav.openCamera(CameraFlow.PracticeTake) })
        },
    ) {
        LargeTitle("Kitchen Argument")
        Text("2 characters · 12 lines · about 1:40", style = OT.type.footnote, color = c.labelSecondary)
        Spacer(Modifier.height(16.dp))

        SectionLabel("Who are you playing?")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PracticeCharacterCard("Sam", if (playing == "Sam") "You play Sam · 6 lines" else "6 lines", playing == "Sam", Modifier.weight(1f)) { playing = "Sam" }
            PracticeCharacterCard("Nina", if (playing == "Nina") "You play Nina · 6 lines" else "6 lines", playing == "Nina", Modifier.weight(1f)) { playing = "Nina" }
        }
        Spacer(Modifier.height(12.dp))

        // First exchange, so the actor sees whose lines are theirs.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceSunken).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val samMine = playing == "Sam"
            Text(if (samMine) "SAM · YOU" else "SAM", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (samMine) c.accent else c.reader)
            Text(samLine, style = if (samMine) OT.type.headline else OT.type.callout, color = if (samMine) c.labelPrimary else c.labelSecondary)
            Spacer(Modifier.height(4.dp))
            Text(if (!samMine) "NINA · YOU" else "NINA", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (!samMine) c.accent else c.reader)
            Text(ninaLine, style = if (!samMine) OT.type.headline else OT.type.callout, color = if (!samMine) c.labelPrimary else c.labelSecondary)
        }
        Spacer(Modifier.height(16.dp))

        // Reader is the actor's own recorded voice only.
        SectionLabel("Who reads $other?")
        PracticeReaderOption("Your recording", "Record $other’s lines first · plays on cue", reader == "Your recording", showDivider = true) { reader = "Your recording" }
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
