package com.onetake.app.ui.shoot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalNavigator
import com.onetake.app.CameraFlow
import com.onetake.app.Route
import com.onetake.app.ui.components.Chevron
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.LargeTitle
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PlainButton
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.PushedScreen
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.otCard
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.theme.DarkOT
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.delay

// Android port of iOS ShootViews.swift — 13b Solo or Multi-Device, 13b.1/14/14.0/14.1 Multi-Device,
// 13c Before you roll. The camera flow itself lives in ShootFlowScreen.kt.

// MARK: - 13b How are you shooting?

@Composable
fun ShootModeScreen(auditionId: String) {
    val nav = LocalNavigator.current
    var multi by remember { mutableStateOf(false) }

    PushedScreen(
        bottomBar = {
            PrimaryButton(
                text = if (multi) "Pick a second screen" else "Continue solo",
                onClick = { nav.push(if (multi) Route.MultiDevice(auditionId) else Route.ShootSetup(auditionId)) },
            )
        },
    ) {
        LargeTitle(
            title = "How are you shooting?",
            subtitle = "Solo works for most tapes. Add your tablet or laptop when you want to see yourself or read big lines.",
        )
        Spacer(Modifier.height(20.dp))
        ShootModeOption(
            selected = !multi,
            icon = Icons.Filled.Smartphone,
            title = "Solo",
            detail = "Just this phone on a stand. Maya plays from its speaker, lines on its screen.",
            tags = listOf("Reader track", "“Action” / “Cut”"),
            onClick = { multi = false },
        )
        Spacer(Modifier.height(14.dp))
        ShootModeOption(
            selected = multi,
            icon = Icons.Filled.Tablet,
            title = "Multi-Device",
            detail = "Use your tablet, laptop or a spare phone as a second screen beside the lens.",
            tags = listOf("Monitor", "Prompter", "Remote", "Your devices"),
            onClick = { multi = true },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShootModeOption(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    detail: String,
    tags: List<String>,
    onClick: () -> Unit,
) {
    val c = OT.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceCard)
            .border(if (selected) 1.5.dp else GLASS_EDGE_WIDTH, if (selected) c.accent else GLASS_EDGE_COLOR, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShootIconTile(icon)
            Text(
                title,
                style = OT.type.title.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                color = c.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, tint = c.accent, modifier = Modifier.size(26.dp))
            } else {
                Box(Modifier.size(26.dp).padding(2.dp).border(1.5.dp, c.labelTertiary, CircleShape))
            }
        }
        Text(detail, style = OT.type.callout, color = c.labelSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                Text(
                    tag,
                    style = OT.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = c.labelSecondary,
                    modifier = Modifier.clip(CircleShape).background(c.fillQuiet).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun ShootIconTile(icon: ImageVector, tint: Color = OT.colors.accent) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(OT.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

// MARK: - 13b.1 / 14 / 14.0 / 14.1 Multi-Device (your own devices only)

private enum class MdStage { Pick, Connecting, Connected, Code }

private data class MdDevice(val icon: ImageVector, val name: String, val status: String, val ready: Boolean)

private val mdDevices = listOf(
    MdDevice(Icons.Filled.Tablet, "Studio Tablet", "Ready · same Wi-Fi", ready = true),
    MdDevice(Icons.Filled.Laptop, "Prompter Laptop", "Open OneTake on the laptop to use it", ready = false),
    MdDevice(Icons.Filled.Smartphone, "Remote Monitor Phone", "Asleep — wake it to connect", ready = false),
)

@Composable
fun MultiDeviceScreen(auditionId: String) {
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    var stage by remember { mutableStateOf(MdStage.Pick) }
    var device by remember { mutableStateOf(mdDevices.first()) }
    var jobs by remember { mutableStateOf(setOf("Monitor", "Prompter")) }

    BackHandler(enabled = stage != MdStage.Pick) { stage = MdStage.Pick }

    LaunchedEffect(stage) {
        if (stage == MdStage.Connecting) {
            delay(1500)
            stage = MdStage.Connected
            haptics.success()
        }
    }

    val subtitle = when (stage) {
        MdStage.Pick -> "This phone films"
        MdStage.Connecting -> "Connecting"
        MdStage.Connected -> "Tablet connected"
        MdStage.Code -> "For a device not on your Google Account"
    }
    MdScaffold(
        title = if (stage == MdStage.Code) "Connect with a code" else "Multi-Device",
        subtitle = subtitle,
        onBack = { if (stage == MdStage.Pick) nav.back() else stage = MdStage.Pick },
        showBottomBar = stage == MdStage.Connecting || stage == MdStage.Connected,
        bottomBar = {
            if (stage == MdStage.Connected) {
                PrimaryButton("Check placement", onClick = { nav.push(Route.ShootSetup(auditionId)) })
                SecondaryButton("End session", onClick = { stage = MdStage.Pick })
            } else {
                SecondaryButton("Cancel", onClick = { stage = MdStage.Pick })
            }
        },
    ) {
        when (stage) {
            MdStage.Pick -> MdPickContent(
                onPick = { d ->
                    device = d
                    stage = MdStage.Connecting
                },
                onCode = { stage = MdStage.Code },
            )
            MdStage.Connecting -> MdConnectingContent(device, jobs, onJobs = { jobs = it }, onCode = { stage = MdStage.Code })
            MdStage.Connected -> MdConnectedContent(device, jobs, onJobs = { jobs = it })
            MdStage.Code -> MdCodeContent()
        }
    }
}

@Composable
private fun MdScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    showBottomBar: Boolean,
    bottomBar: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OT.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = OT.type.headline, color = c.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = OT.type.caption, color = c.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(48.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            content = content,
        )
        if (showBottomBar) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.bgCanvas)
                    .navigationBarsPadding()
                    .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = bottomBar,
            )
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun MdStatusDot(color: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

@Composable
private fun MdPickContent(onPick: (MdDevice) -> Unit, onCode: () -> Unit) {
    val c = OT.colors
    LargeTitle(
        title = "Pick a second screen",
        subtitle = "Devices on your Google Account show up here when OneTake is open on them.",
    )
    Spacer(Modifier.height(20.dp))
    SectionLabel("Nearby · your Google Account", Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(4.dp))
    mdDevices.forEachIndexed { i, d ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button) { onPick(d) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShootIconTile(d.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.name, style = OT.type.body, color = c.labelPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MdStatusDot(if (d.ready) c.success else c.labelTertiary)
                    Text(d.status, style = OT.type.footnote, color = c.labelTertiary)
                }
            }
            Chevron()
        }
        if (i < mdDevices.lastIndex) OTDivider(startIndent = 52.dp)
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Not on your account?", Modifier.padding(start = 4.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onCode)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("CODE", style = OT.type.eyebrow, color = c.labelTertiary, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Connect with a code", style = OT.type.body, color = c.labelPrimary)
            Text("Any tablet, laptop or phone with OneTake", style = OT.type.footnote, color = c.labelTertiary)
        }
        Chevron()
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Takes are only ever recorded on this phone. The second screen keeps nothing.",
        style = OT.type.footnote,
        color = c.labelTertiary,
    )
}

@Composable
private fun MdDeviceCard(device: MdDevice, connected: Boolean) {
    val c = OT.colors
    Row(
        Modifier.fillMaxWidth().otCard(20.dp, c.surfaceCard).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShootIconTile(device.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.name, style = OT.type.headline.copy(fontWeight = FontWeight.Normal), color = c.labelPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MdStatusDot(if (connected) c.success else c.warning)
                Text(
                    if (connected) "Connected · same Wi-Fi · 12 ms" else "Waiting for you to accept…",
                    style = OT.type.footnote,
                    color = c.labelSecondary,
                )
            }
        }
        if (connected) {
            Icon(Icons.Filled.CheckCircle, "Connected", tint = c.success, modifier = Modifier.size(24.dp))
        } else {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.warning)
        }
    }
}

private val mdJobs = listOf(
    "Monitor" to Icons.Filled.Visibility,
    "Prompter" to Icons.AutoMirrored.Filled.Notes,
    "Remote" to Icons.Filled.PlayArrow,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MdJobChips(jobs: Set<String>, onJobs: (Set<String>) -> Unit) {
    val c = OT.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        mdJobs.forEach { (job, icon) ->
            val on = job in jobs
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .padding(vertical = 6.dp)
                    .clip(CircleShape)
                    .background(if (on) c.accent else c.fillField)
                    .toggleable(value = on, role = Role.Checkbox) { onJobs(if (it) jobs + job else jobs - job) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val fg = if (on) c.labelOnAccent else c.labelPrimary
                Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
                Text(job, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = fg)
            }
        }
    }
}

@Composable
private fun MdConnectingContent(device: MdDevice, jobs: Set<String>, onJobs: (Set<String>) -> Unit, onCode: () -> Unit) {
    val c = OT.colors
    LargeTitle(
        title = "Accept on your tablet",
        subtitle = "A prompt just appeared on ${device.name}. Tap Connect there.",
    )
    Spacer(Modifier.height(16.dp))
    MdDeviceCard(device, connected = false)
    Spacer(Modifier.height(20.dp))
    SectionLabel("What the tablet does", Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(4.dp))
    MdJobChips(jobs, onJobs)
    Spacer(Modifier.height(4.dp))
    Text(
        "Change any time. Monitor + Prompter is best for a tablet beside the lens.",
        style = OT.type.footnote,
        color = c.labelSecondary,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onCode),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Not seeing it? Use code", style = OT.type.footnote, color = c.labelSecondary)
        Text("482 915", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
    }
}

@Composable
private fun MdConnectedContent(device: MdDevice, jobs: Set<String>, onJobs: (Set<String>) -> Unit) {
    val c = OT.colors
    Spacer(Modifier.height(8.dp))
    MdDeviceCard(device, connected = true)
    Spacer(Modifier.height(20.dp))
    SectionLabel("Its jobs", Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(4.dp))
    MdJobChips(jobs, onJobs)
    Spacer(Modifier.height(16.dp))
    SectionLabel("Put the tablet right beside the lens", Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceSunken)
            .padding(horizontal = 20.dp)
            .semantics { contentDescription = "Diagram: your mark six feet away, phone, then the tablet right beside it" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.AccessibilityNew, null, tint = c.labelSecondary, modifier = Modifier.size(28.dp))
            Text("your mark · 6 ft", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.labelSecondary)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(width = 28.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1A22)),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(6.dp).clip(CircleShape).background(Color.Black)) }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(width = 76.dp, height = 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.accentSoft)
                .border(1.5.dp, c.accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("Tablet", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent) }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Your eyes stay near the lens while you read, so your eyeline doesn’t drift.",
        style = OT.type.footnote,
        color = c.labelSecondary,
    )
}

/** 14.0 Connect with a code — amber countdown ring (amber = time). */
@Composable
private fun MdCodeContent() {
    val c = OT.colors
    var secondsLeft by remember { mutableIntStateOf(300) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            secondsLeft = if (secondsLeft > 1) secondsLeft - 1 else 300 // a new code every 5 minutes
        }
    }
    val amber = DarkOT.due
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(OT.camera.bg)
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Text("TIME LEFT", style = OT.type.eyebrow, color = Color.White.copy(alpha = 0.7f))
            Text(
                "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                style = OT.type.title.copy(fontFeatureSettings = "tnum"),
                color = amber,
            )
        }
        Box(
            Modifier.fillMaxWidth(0.74f).aspectRatio(1f).align(Alignment.Center).padding(top = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                val d = size.minDimension - stroke
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                val sz = Size(d, d)
                drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, tl, sz, style = Stroke(stroke))
                drawArc(amber, -90f, 360f * secondsLeft / 300f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CODE", style = OT.type.eyebrow, color = Color.White.copy(alpha = 0.7f))
                Text(
                    "482 915",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = OT.type.number.copy(fontSize = 40.sp, lineHeight = 46.sp),
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "On the tablet or laptop, open OneTake → Second screen → Enter code. A new code appears every 5 minutes.",
        style = OT.type.callout,
        color = c.labelSecondary,
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = c.labelTertiary)
        Text("Waiting for a device…", style = OT.type.footnote, color = c.labelTertiary)
    }
}

// MARK: - 13c Before you roll

@Composable
fun ShootSetupScreen(auditionId: String) {
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val c = OT.colors
    var silenced by remember { mutableStateOf(false) }
    val roll = { nav.openCamera(CameraFlow.Shoot(auditionId)) }

    PushedScreen(
        bottomBar = {
            PrimaryButton("Set up by voice", onClick = roll, icon = Icons.Filled.GraphicEq, iconTrailing = true)
            PlainButton("Start without voice", onClick = roll)
        },
    ) {
        LargeTitle(title = "Before you roll", subtitle = "Simple · one phone")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${if (silenced) 4 else 3} of 4 set", style = OT.type.title, color = c.labelPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < 3 || silenced) c.accent else c.warning))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val rosterShape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(rosterShape)
                .background(c.surfaceCard)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, rosterShape)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            SetupCheckRow("Camera", "Back camera · sharper", "Screen faces away — we talk you through it", ok = true)
            OTDivider()
            SetupCheckRow("Hands-free", "Your voice + earbuds click", "“Action”, “Cut”, “Line?” · volume buttons on", ok = true)
            OTDivider()
            SetupCheckRow("Microphone", "Studio Wireless Mic", "Good level from your mark", ok = true) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(14) { i ->
                        Box(
                            Modifier.size(width = 9.dp, height = 4.dp).clip(CircleShape)
                                .background(if (i < 9) c.success else c.fillQuiet)
                        )
                    }
                }
            }
            OTDivider()
            SetupCheckRow("Interruptions", "Calls and alerts can cut in", "A notification mid-scene throws Maya’s timing", ok = silenced) {
                if (!silenced) {
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(c.fillField)
                            .clickable(role = Role.Button) {
                                silenced = true
                                haptics.success()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Silence for this shoot", style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
                        Icon(Icons.Filled.DarkMode, null, tint = c.labelPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, tint = c.labelTertiary, modifier = Modifier.size(16.dp))
            Text("Every take saves while you record — even if a call gets through.", style = OT.type.footnote, color = c.labelTertiary)
        }
    }
}

@Composable
private fun SetupCheckRow(
    eyebrow: String,
    title: String,
    detail: String,
    ok: Boolean,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = OT.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(if (ok) c.accent else c.warning))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eyebrow, style = OT.type.footnote, color = c.labelTertiary)
            Text(title, style = OT.type.headline, color = c.labelPrimary)
            Text(detail, style = OT.type.footnote, color = if (ok) c.labelSecondary else c.warning)
            extra?.invoke(this)
        }
        Chevron()
    }
}
