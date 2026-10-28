package com.onetake.app.ui.shoot

import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.speech.SpeechRecognizer
import android.provider.Settings
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.content.Context
import android.app.NotificationManager
import android.Manifest
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
fun ShootModeScreen(auditionId: String, scene: Int = -1) {
    val nav = LocalNavigator.current

    PushedScreen(
        bottomBar = {
            PrimaryButton(text = "Continue solo", onClick = { nav.push(Route.ShootSetup(auditionId, scene)) })
        },
    ) {
        LargeTitle(
            title = "How are you shooting?",
            subtitle = "Solo works for most tapes.",
        )
        Spacer(Modifier.height(20.dp))
        ShootModeOption(
            selected = true,
            icon = Icons.Filled.Smartphone,
            title = "Solo",
            detail = "Just this phone on a stand. Your reader plays from its speaker, lines on its screen.",
            tags = listOf("Reader track", "“Action” / “Cut”"),
            onClick = {},
        )
        Spacer(Modifier.height(14.dp))
        // There's no device pairing in the app yet, so this is shown as unavailable rather than
        // staging a fake "Tablet connected" flow.
        ShootModeOption(
            selected = false,
            enabled = false,
            icon = Icons.Filled.Tablet,
            title = "Multi-Device",
            detail = "Use your tablet, laptop or a spare phone as a second screen beside the lens.",
            tags = listOf("Not available yet"),
            onClick = {},
        )
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShootModeOption(
    selected: Boolean,
    enabled: Boolean = true,
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
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
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

// MARK: - 13c Before you roll

@Composable
fun ShootSetupScreen(auditionId: String, scene: Int = -1) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val c = OT.colors
    val roll = { nav.openCamera(CameraFlow.Shoot(auditionId, scene)) }

    // Re-read the phone's real state whenever the screen comes back (e.g. from Settings).
    var status by remember { mutableStateOf(SetupStatus.read(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = SetupStatus.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val okCount = listOf(status.camera, status.mic, status.voice, status.quiet).count { it }

    PushedScreen(
        bottomBar = { PrimaryButton("Open the camera", onClick = roll) },
    ) {
        LargeTitle(title = "Before you roll", subtitle = "Simple · one phone")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$okCount of 4 set", style = OT.type.title, color = c.labelPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < okCount) c.accent else c.warning))
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
            SetupCheckRow(
                "Camera",
                "Front camera · you can flip it",
                if (status.camera) "Ready" else "Camera access is off — the camera will ask",
                ok = status.camera,
            )
            OTDivider()
            SetupCheckRow(
                "Microphone",
                status.micName,
                if (status.mic) "Records with every take" else "Microphone access is off — takes will be silent",
                ok = status.mic,
            )
            OTDivider()
            SetupCheckRow(
                "Hands-free",
                "Say “Action” / “Cut”",
                if (status.voice) "Voice commands work on this phone" else "Speech recognition isn't available — tap to record",
                ok = status.voice,
            )
            OTDivider()
            SetupCheckRow(
                "Interruptions",
                if (status.quiet) "Do Not Disturb is on" else "Calls and alerts can cut in",
                "A notification mid-scene throws the reader’s timing",
                ok = status.quiet,
            ) {
                if (!status.quiet) {
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(c.fillField)
                            .clickable(role = Role.Button) { SetupStatus.openDoNotDisturb(context) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Turn on Do Not Disturb", style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
                        Icon(Icons.Filled.DarkMode, null, tint = c.labelPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, tint = c.labelTertiary, modifier = Modifier.size(16.dp))
            Text("Each take is saved when you say “Cut” or tap stop.", style = OT.type.footnote, color = c.labelTertiary)
        }
    }
}

/** What "Before you roll" checks — read from the phone, not assumed. */
private data class SetupStatus(
    val camera: Boolean,
    val mic: Boolean,
    val micName: String,
    val voice: Boolean,
    val quiet: Boolean,
) {
    companion object {
        fun read(context: Context): SetupStatus {
            fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            val mic = granted(Manifest.permission.RECORD_AUDIO)
            val audio = context.getSystemService(AudioManager::class.java)
            val inputs = audio?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
            val micName = when {
                inputs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE } -> "Wired microphone"
                inputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } -> "Bluetooth microphone"
                else -> "Phone microphone"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            return SetupStatus(
                camera = granted(Manifest.permission.CAMERA),
                mic = mic,
                micName = micName,
                voice = mic && runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false),
                quiet = nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL,
            )
        }

        /** Opens the system Do Not Disturb settings (falls back to sound settings). */
        fun openDoNotDisturb(context: Context) {
            val intents = listOf(Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_SOUND_SETTINGS))
            for (intent in intents) {
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
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
