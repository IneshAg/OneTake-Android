package com.onetake.app.ui.profile

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.widget.Toast
import com.onetake.app.ui.system.ReaderFiles
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onetake.app.CameraFlow
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.models.PieceKind
import com.onetake.app.ui.camera.CameraBackground
import com.onetake.app.ui.camera.rememberCameraController
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.HeadshotImage
import com.onetake.app.ui.components.LargeTitle
import com.onetake.app.ui.components.ListRow
import com.onetake.app.ui.components.OTCard
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.PushedScreen
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.SheetHeader
import com.onetake.app.ui.components.ToggleRow
import com.onetake.app.ui.system.rememberHaptics
import java.io.File
import java.util.Locale
import com.onetake.app.ui.theme.OT

// MARK: - 10 Profile (tab root)

@Composable
fun ProfileScreen() {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val c = OT.colors
    val slate = store.slate

    // Pushed from the profile sheet (Route.Settings) — not a tab root, so it gets a back bar.
    PushedScreen {
        LargeTitle("Settings")
        Spacer(Modifier.height(12.dp))

        // Slate card: headshot, name, details → Edit slate
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Edit slate", role = Role.Button) { nav.push(Route.EditSlate) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeadshotImage(Modifier.size(64.dp).clip(CircleShape), contentDescription = "Your headshot")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(slate.name.ifBlank { "Add your name" }, style = OT.type.title, color = c.labelPrimary, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Edit, "Edit slate", tint = c.accent, modifier = Modifier.size(15.dp)) }
                }
                Text(
                    listOf(slate.height, slate.basedIn, slate.representation.ifEmpty { "Self-represented" })
                .filter { it.isNotBlank() }.joinToString(" · "),
                    style = OT.type.footnote,
                    color = c.labelTertiary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        com.onetake.app.ui.components.OTDivider()
        ListRow(title = "Headshots", value = "${store.headshotsTaken} of 3", onClick = { nav.push(Route.Headshots) })

        Spacer(Modifier.height(20.dp))
        SectionLabel("Every time you record")
        ToggleRow(title = "Slate at the start", checked = store.slateAtStart, onCheckedChange = { store.slateAtStart = it })
        ProfilePickerRow(
            title = "Reader volume",
            detail = "Casting should hear you first",
            value = store.readerVolume,
            options = listOf("Same as me", "Quieter", "Much quieter"),
            onSelect = { store.readerVolume = it },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("Appearance")
        ToggleRow(
            title = "Liquid Glass",
            detail = if (com.onetake.app.ui.components.isGlassSupported()) "Real-time backdrop refraction on floating nav bar" else "Requires Android 12+",
            checked = store.liquidGlass && com.onetake.app.ui.components.isGlassSupported(),
            onCheckedChange = { store.liquidGlass = it },
            showDivider = false,
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("More")
        ListRow(title = "Notifications", onClick = { nav.push(Route.Notifications) })
        ListRow(title = "Privacy & data", onClick = { nav.push(Route.Privacy) })
        // No accounts exist (everything stays on this phone), so there's nothing to sign out of.
    }
}

/** A settings row whose value opens a menu anchored to the row (iOS menu-style Picker). */
@Composable
private fun ProfilePickerRow(
    title: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    detail: String? = null,
    showDivider: Boolean = true,
) {
    val c = OT.colors
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        ListRow(
            title = title,
            detail = detail,
            value = value,
            showChevron = false,
            showDivider = showDivider,
            onClick = { expanded = true },
            trailing = { Icon(Icons.Filled.ExpandMore, null, tint = c.labelTertiary, modifier = Modifier.size(20.dp)) },
        )
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = OT.type.body, color = c.labelPrimary) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        trailingIcon = {
                            if (option == value) Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.semantics { selected = option == value },
                    )
                }
            }
        }
    }
}

// MARK: - 10.1 Edit slate (sheet-style: Cancel · Edit slate · Save)

@Composable
fun EditSlateScreen() {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val c = OT.colors
    var name by rememberSaveable { mutableStateOf(store.slate.name) }
    var height by rememberSaveable { mutableStateOf(store.slate.height) }
    var basedIn by rememberSaveable { mutableStateOf(store.slate.basedIn) }
    var representation by rememberSaveable { mutableStateOf(store.slate.representation) }
    val draft = store.slate.copy(name = name.trim(), height = height.trim(), basedIn = basedIn.trim(), representation = representation.trim())

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Spacer(Modifier.height(8.dp))
        SheetHeader(
            title = "Edit slate",
            leading = "Cancel",
            trailing = "Save",
            trailingEnabled = draft.name.isNotEmpty(),
            onLeading = { nav.back() },
            onTrailing = {
                store.slate = draft
                nav.back()
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SlateField(label = "Name", value = name, onValueChange = { name = it }, placeholder = "Name")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SlateField(label = "Height", value = height, onValueChange = { height = it }, placeholder = "Height", modifier = Modifier.weight(1f))
                SlateField(label = "Based in", value = basedIn, onValueChange = { basedIn = it }, placeholder = "Based in", modifier = Modifier.weight(1f))
            }
            SlateField(label = "Representation", value = representation, onValueChange = { representation = it }, placeholder = "Self-represented")
            Text(
                "Said at the start of every tape, in this order. Change it any time.",
                style = OT.type.footnote,
                color = c.labelTertiary,
            )
            Spacer(Modifier.height(8.dp))
            SectionLabel("Preview")
            val previewShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(previewShape)
                    .background(OT.colors.surfaceCard)
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, previewShape)
                    .padding(18.dp),
            ) {
                Text(draft.spoken, style = OT.type.headline, color = c.labelPrimary)
            }
        }
    }
}

@Composable
private fun SlateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = OT.colors
    val fieldShape = RoundedCornerShape(16.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = OT.type.body.copy(color = c.labelPrimary),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(fieldShape)
                        .background(Color(0xFF111114))
                        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, fieldShape)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = OT.type.body, color = c.labelTertiary, maxLines = 1)
                        inner()
                    }
                    if (value.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clickable(role = Role.Button, onClickLabel = "Clear $label") { onValueChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(c.labelTertiary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Close, "Clear $label", tint = c.bgCanvas, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            },
        )
    }
}

// MARK: - 10b Privacy & data

private fun formatStorageSize(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024L * 1024L -> "${maxOf(1L, bytes / 1024L)} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
}

@Composable
fun PrivacyScreen() {
    val store = LocalAppStore.current
    val context = LocalContext.current
    val c = OT.colors
    var confirmDelete by remember { mutableStateOf(false) }

    var takesBytes by remember { mutableStateOf(0L) }
    var headshotsBytes by remember { mutableStateOf(0L) }
    var scriptsBytes by remember { mutableStateOf(0L) }
    var readerBytes by remember { mutableStateOf(0L) }

    /** Bumped after "Delete all local data" so the sizes are measured again. */
    var wipes by remember { mutableIntStateOf(0) }

    LaunchedEffect(store.takes.size, store.headshotsTaken, store.auditions.size, wipes) {
        scriptsBytes = (store.auditions.sumOf { it.scenes.sumOf { s -> s.lines.sumOf { l -> l.text.length } } } * 2L)
        // Walking every recording folder is disk I/O, so it's done off the main thread.
        val (takes, headshots, reader) = withContext(Dispatchers.IO) {
            // Real recordings now live under filesDir in audition-*/practice-* subfolders (moved out
            // of cacheDir once they're kept — see MediaStore.persist), plus the exports folder.
            val mediaDirs = context.filesDir.listFiles { f ->
                f.isDirectory && (f.name.startsWith("audition-") || f.name.startsWith("practice-") || f.name == "exports")
            } ?: emptyArray()
            val media = mediaDirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

            val photos = (File(context.filesDir, "headshots").listFiles() ?: emptyArray()).sumOf { it.length() }

            // Reader lines live under reader/<scene id>/, plus any flat files left by older builds.
            val legacyReader = context.filesDir.listFiles { f -> f.isFile && f.name.startsWith(ReaderFiles.LEGACY_PREFIX) } ?: emptyArray()
            val voice = legacyReader.sumOf { it.length() } +
                File(context.filesDir, ReaderFiles.DIR).walkTopDown().filter { it.isFile }.sumOf { it.length() }
            Triple(media, photos, voice)
        }
        takesBytes = takes
        headshotsBytes = headshots
        readerBytes = reader
    }

    val totalBytes = takesBytes + headshotsBytes + scriptsBytes + readerBytes
    val totalSizeText = formatStorageSize(totalBytes)

    PushedScreen {
        LargeTitle(
            "Privacy & data",
            subtitle = "Your script, recordings and headshots stay on this phone until you choose to send a tape.",
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("On this phone", Modifier.weight(1f))
            Text(totalSizeText, style = OT.type.headline, color = c.labelPrimary)
        }
        Spacer(Modifier.height(8.dp))
        // Dynamic Storage bar
        Row(
            Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = "$totalSizeText used on device" },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (totalBytes <= 0L) {
                Box(Modifier.fillMaxWidth().fillMaxHeight().clip(CircleShape).background(c.fillQuiet))
            } else {
                val totalF = totalBytes.toFloat()
                if (takesBytes > 0) Box(Modifier.weight(takesBytes / totalF).fillMaxHeight().clip(CircleShape).background(c.accent))
                if (scriptsBytes > 0) Box(Modifier.weight(scriptsBytes / totalF).fillMaxHeight().clip(CircleShape).background(c.reader))
                if (headshotsBytes > 0) Box(Modifier.weight(headshotsBytes / totalF).fillMaxHeight().clip(CircleShape).background(c.success))
                if (readerBytes > 0) Box(Modifier.weight(readerBytes / totalF).fillMaxHeight().clip(CircleShape).background(c.labelTertiary))
            }
        }
        Spacer(Modifier.height(6.dp))
        ListRow(title = "Takes", detail = "${store.takes.size} recorded takes", value = formatStorageSize(takesBytes), showChevron = false)
        ListRow(title = "Scripts & sides", detail = "${store.auditions.size} audition scripts", value = formatStorageSize(scriptsBytes), showChevron = false)
        ListRow(title = "Headshots", detail = "${store.headshotsTaken} photos", value = formatStorageSize(headshotsBytes), showChevron = false)
        ListRow(title = "Reader recordings", detail = "Your own voice, per line", value = formatStorageSize(readerBytes), showChevron = false, showDivider = false)

        Spacer(Modifier.height(20.dp))
        // Everything stays on this phone: there are no private links or synced sessions, and the
        // "Privacy Policy" link pointed at example.com — so neither is claimed here.
        SectionLabel("Sent tapes")
        ListRow(
            title = "Where your tapes go",
            detail = "Only where you share them from the share sheet. OneTake has no server.",
            showChevron = false,
            showDivider = false,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { confirmDelete = true },
            contentAlignment = Alignment.CenterStart,
        ) { Text("Delete all local data", style = OT.type.headline, color = c.danger) }
        Text(
            "Clears headshots, reader tracks and every take. Sent tapes already delivered aren’t affected.",
            style = OT.type.footnote,
            color = c.labelTertiary,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all local data?", style = OT.type.headline) },
            text = { Text("Clears headshots, reader tracks and every take. Sent tapes already delivered aren’t affected.", style = OT.type.callout) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.filesDir.listFiles { f ->
                            f.isDirectory && (f.name.startsWith("audition-") || f.name.startsWith("practice-") || f.name == "exports")
                        }?.forEach { it.deleteRecursively() }
                        File(context.filesDir, "headshots").listFiles()?.forEach { it.delete() }
                        context.filesDir.listFiles { f -> f.isFile && f.name.startsWith(ReaderFiles.LEGACY_PREFIX) }?.forEach { it.delete() }
                        File(context.filesDir, ReaderFiles.DIR).deleteRecursively()
                        context.cacheDir.listFiles { f -> f.name.startsWith("take-") }?.forEach { it.delete() }
                    }
                    store.resetAfterMediaWipe()
                    wipes++
                    confirmDelete = false
                }) { Text("Delete", color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.labelPrimary) }
            },
            containerColor = c.surfaceRaised,
        )
    }
}

// MARK: - 10c Notifications

@Composable
fun NotificationSettingsScreen() {
    val store = LocalAppStore.current
    val context = LocalContext.current
    val c = OT.colors
    val rosterShape = RoundedCornerShape(24.dp)
    // Android 13+ needs permission to post; ask when a reminder is switched on.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun enable(set: (Boolean) -> Unit): (Boolean) -> Unit = { on ->
        set(on)
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    PushedScreen(title = "Notifications") {
        Text(
            "Reminders come from this phone — nothing is sent to a server.",
            style = OT.type.callout,
            color = c.labelSecondary,
        )
        Spacer(Modifier.height(18.dp))
        SectionLabel("Deadlines", Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(rosterShape)
                .background(OT.colors.surfaceCard)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, rosterShape),
        ) {
            ToggleRow("Morning it’s due", store.notifyMorning, enable { store.notifyMorning = it }, detail = "8:00 AM on the due date")
            ToggleRow("Two hours before", store.notifyTwoHours, enable { store.notifyTwoHours = it }, detail = "Only if it isn’t sent yet", showDivider = false)
        }
    }
}

// MARK: - 08 Headshots

@Composable
fun HeadshotsScreen(auditionId: String? = null) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val c = OT.colors
    val taken = store.headshotsTaken
    val targetAudition = auditionId?.let { store.audition(it) } ?: store.active.firstOrNull() ?: store.auditions.firstOrNull()
    val project = targetAudition?.project ?: "your audition"
    val hasFront = store.hasHeadshot("front")
    val hasLeft = store.hasHeadshot("left")
    val hasRight = store.hasHeadshot("right")

    fun onProceed() {
        if (targetAudition != null) {
            store.markDone(PieceKind.Headshot, targetAudition.id)
            val updated = store.audition(targetAudition.id)
            val next = updated?.nextPiece
            when (next?.kind) {
                PieceKind.Slate, PieceKind.FullLength -> nav.push(Route.ShootMode(targetAudition.id))
                PieceKind.Scene -> {
                    if (updated.scenes.isNotEmpty()) {
                        nav.push(Route.Prepare(targetAudition.id, 0))
                    } else {
                        nav.push(Route.AuditionDetail(targetAudition.id))
                    }
                }
                else -> nav.push(Route.AuditionDetail(targetAudition.id))
            }
        } else {
            nav.back()
        }
    }

    PushedScreen(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasFront) {
                    val nextTitle = targetAudition?.nextPiece?.title
                    val proceedText = when {
                        targetAudition != null && nextTitle != null && nextTitle != "Headshot" -> "Proceed to $nextTitle"
                        targetAudition != null -> "Proceed with audition"
                        else -> "Done"
                    }
                    PrimaryButton(
                        text = proceedText,
                        onClick = { onProceed() },
                    )
                    if (taken < 3) {
                        SecondaryButton(
                            text = "Add profile angles",
                            onClick = { nav.openCamera(CameraFlow.Headshot) },
                        )
                    } else {
                        SecondaryButton(
                            text = "Retake all headshots",
                            onClick = { nav.openCamera(CameraFlow.Headshot) },
                        )
                    }
                } else {
                    PrimaryButton(
                        text = "Take your headshot",
                        onClick = { nav.openCamera(CameraFlow.Headshot) },
                    )
                }
            }
        },
    ) {
        LargeTitle("Headshots", subtitle = "Front is what casting sees first. Profiles help on commercial calls.")
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().height(264.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeadshotSlot(
                title = "Front",
                asset = "front",
                photoUri = store.headshot("front"),
                done = hasFront,
                modifier = Modifier.weight(1.15f).fillMaxHeight(),
                action = if (hasFront) "Retake" else null,
                onAction = { nav.openCamera(CameraFlow.Headshot) },
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeadshotSlot("Left profile", "left", done = hasLeft, photoUri = store.headshot("left"), modifier = Modifier.weight(1f).fillMaxWidth())
                HeadshotSlot("Right profile", "right", done = hasRight, photoUri = store.headshot("right"), modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
        Spacer(Modifier.height(14.dp))

        if (taken >= 3) {
            OTCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All three done", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.CheckCircle, null, tint = c.success, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Front and both profiles are saved on this phone and attached to $project.",
                    style = OT.type.callout,
                    color = c.labelSecondary,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .then(if (i < taken) Modifier.background(c.accent) else Modifier.border(1.dp, c.labelTertiary, CircleShape)),
                        )
                    }
                }
                Text(
                    if (taken == 0) "0 of 3" else "$taken of 3 · Front is attached to $project",
                    style = OT.type.footnote,
                    color = c.labelSecondary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeadshotTip(Icons.Filled.WbSunny, "Face a window", "not a lamp", Modifier.weight(1f))
            HeadshotTip(Icons.Filled.CropPortrait, "Plain wall", "an arm’s length behind", Modifier.weight(1f))
            HeadshotTip(Icons.Filled.Smartphone, "Eye level", "a little above is fine", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeadshotSlot(
    title: String,
    asset: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    photoUri: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = OT.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xFF111114))
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    ) {
        if (done) {
            HeadshotImage(Modifier.fillMaxSize(), name = asset, uri = photoUri, contentDescription = "$title headshot")
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)),
                ),
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (action == null) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        title,
                        style = if (action == null) OT.type.footnote.copy(fontWeight = FontWeight.SemiBold) else OT.type.headline,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                if (action != null) {
                    Box(
                        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.heightIn(min = 36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(action, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White) }
                    }
                }
            }
        } else {
            val dash = c.labelTertiary
            Canvas(Modifier.fillMaxSize()) {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), 0f)),
                )
            }
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Face, null, tint = c.labelSecondary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(6.dp))
                Text(title, style = OT.type.headline, color = c.labelPrimary, textAlign = TextAlign.Center)
                Text("Not taken", style = OT.type.footnote, color = c.labelTertiary)
            }
        }
    }
}

@Composable
private fun HeadshotTip(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    val c = OT.colors
    val tipShape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(tipShape)
            .background(OT.colors.surfaceCard)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, tipShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = c.labelSecondary, modifier = Modifier.size(20.dp))
        Text(title, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
        Text(detail, style = OT.type.footnote, color = c.labelTertiary)
    }
}

// MARK: - 09 Headshot capture: front → left → right (always dark)

private enum class HeadshotAngle(val label: String) { Front("Front"), Left("Left"), Right("Right") }

@Composable
fun HeadshotFlowScreen() {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val camera = rememberCameraController()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var angle by rememberSaveable { mutableStateOf(HeadshotAngle.Front) }
    var front by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(OT.camera.bg)) {
        CameraBackground(camera, Modifier.fillMaxSize(), front = front, photoOnly = true)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar: close · angle picker · balance. Lives above the oval area, never over it.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.Filled.Close, "Close", onClick = { nav.closeCamera() }, onDark = true)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    HeadshotAnglePicker(angle) { angle = it }
                }
                Spacer(Modifier.width(48.dp))
            }

            // Face oval, centred in the space between the top bar and the controls.
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                val ovalColor = OT.camera.label.copy(alpha = 0.85f)
                Canvas(
                    Modifier
                        .sizeIn(maxWidth = 220.dp, maxHeight = 297.dp)
                        .fillMaxHeight()
                        .aspectRatio(200f / 270f, matchHeightConstraintsFirst = true),
                ) {
                    drawOval(color = ovalColor, style = Stroke(width = 1.5.dp.toPx()))
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = OT.dims.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (angle == HeadshotAngle.Front) "Hold still — you’re centred" else "Hold still — that’s your profile",
                    style = OT.type.headline.copy(fontWeight = FontWeight.SemiBold),
                    color = OT.camera.label,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeadshotChip("Light", "soft and even")
                    HeadshotChip("Angle", if (angle == HeadshotAngle.Front) "square" else "side on")
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(78.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .clip(CircleShape)
                            .clickable(enabled = !capturing, role = Role.Button) {
                                capturing = true
                                haptics.impact()
                                camera.capturePhoto { uri ->
                                    capturing = false
                                    if (uri == null) {
                                        // Nothing was saved: stay on this angle, tick nothing off.
                                        Toast.makeText(context, "Couldn't take that photo. Try again.", Toast.LENGTH_SHORT).show()
                                        return@capturePhoto
                                    }
                                    store.saveHeadshot(angle.name.lowercase(), uri.toString())
                                    store.auditions.forEach { a ->
                                        if (a.pieces.any { it.kind == PieceKind.Headshot && !it.isDone }) {
                                            store.markDone(PieceKind.Headshot, a.id)
                                        }
                                    }
                                    val next = HeadshotAngle.entries.getOrNull(angle.ordinal + 1)
                                    if (next != null) {
                                        angle = next
                                    } else {
                                        haptics.success()
                                        nav.closeCamera()
                                    }
                                }
                            }
                            .semantics { contentDescription = "Take photo" },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White)) }
                    CircleIconButton(
                        Icons.Filled.Cameraswitch,
                        "Switch camera",
                        onClick = { front = !front },
                        onDark = true,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Tap, or say “Now”", style = OT.type.footnote, color = OT.camera.label2)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HeadshotAnglePicker(selected: HeadshotAngle, onSelect: (HeadshotAngle) -> Unit) {
    Row(
        Modifier.height(48.dp).clip(CircleShape).background(OT.camera.glass).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadshotAngle.entries.forEach { a ->
            val isSel = a == selected
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isSel) Color.White else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(a) }
                    .semantics { this.selected = isSel }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    a.label,
                    style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSel) Color.Black else OT.camera.label2,
                )
            }
        }
    }
}

@Composable
private fun HeadshotChip(title: String, value: String) {
    Row(
        Modifier.heightIn(min = 30.dp).clip(CircleShape).background(OT.camera.glass).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(OT.colors.success))
        Text(title, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = OT.camera.label)
        Text(value, style = OT.type.caption, color = OT.camera.label2)
    }
}
