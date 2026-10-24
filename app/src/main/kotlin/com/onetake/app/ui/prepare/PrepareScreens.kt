package com.onetake.app.ui.prepare

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.onetake.app.CameraFlow
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.models.Audition
import com.onetake.app.models.PieceKind
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.formatDuration
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.PushedScreen
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.ToggleRow
import com.onetake.app.ui.system.KeepScreenOn
import com.onetake.app.ui.system.ReaderFiles
import com.onetake.app.ui.system.playAndAwait
import com.onetake.app.ui.system.ReaderTrackPlayer
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.system.rememberReaderPlayer
import com.onetake.app.ui.system.rememberTtsEngine
import com.onetake.app.ui.practice.LineLearningMode
import com.onetake.app.ui.theme.OT
import java.io.File
import kotlinx.coroutines.delay

// Android port of iOS Features/Prepare/PrepareViews.swift — 11 Prepare, 11.2 Teleprompter, 11.3–11.6 Run lines.
// Reader audio is the actor's OWN recorded voice only (files from Record Maya). No synthetic voices.

// MARK: - Shared prepare state (iOS @AppStorage / @State equivalents that outlive one screen)

enum class PrepLineMode(val label: String) {
    Full("Full lines"),
    FirstLetters("First letters"),
    MaskAndReveal("Mask & reveal"),
    AudioGap("Audio gap"),
    Hide("Hide mine"),
}

object PrepPrefs {
    var mode by mutableStateOf(PrepLineMode.Full)
    var textSize by mutableIntStateOf(1)
    var followVoice by mutableStateOf(true)
    var mirror by mutableStateOf(true)

    /** Rehearsal-copy edits (11.7): the original sides are never changed. */
    val edits = mutableStateMapOf<String, String>()
    val hidden = mutableStateMapOf<String, Boolean>()

    private val runs = HashMap<String, Int>()
    fun nextRun(key: String): Int {
        val n = (runs[key] ?: 0) + 1
        runs[key] = n
        return n
    }
}

// MARK: - Helpers shared with ReaderScreens.kt (internal, prefixed to avoid clashes)

internal fun prepHasMic(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

internal fun prepAudioMillis(file: File): Long? {
    if (!file.exists()) return null
    return runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()
}

/** Plays a recorded reader line; a corrupt/empty file (e.g. a too-short recording) must not crash the screen. */
internal fun prepPlay(player: ReaderTrackPlayer, sceneId: String, readerIndex: Int) {
    runCatching { player.play(sceneId, readerIndex) }
}

internal fun prepCapitalized(name: String): String = name.lowercase().replaceFirstChar { it.titlecase() }

/** "Maya" — the character the reader plays (first line that isn't yours). */
internal fun prepReaderName(scene: ScriptScene): String =
    scene.lines.firstOrNull { !it.isMine }?.character?.let { prepCapitalized(it) } ?: "Reader"

internal fun prepWords(text: String): List<String> = text.split(" ").filter { it.isNotBlank() }

/** Centered two-line header (title + caption) with a round leading button and optional trailing button. */
@Composable
internal fun PrepNavHeader(
    title: String,
    subtitle: String?,
    leadingIcon: ImageVector,
    leadingDescription: String,
    onLeading: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val c = OT.colors
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp)
            .heightIn(min = 60.dp),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) {
            CircleIconButton(leadingIcon, leadingDescription, onClick = onLeading)
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = OT.type.headline,
                color = c.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = OT.type.caption, color = c.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

/** iOS segmented Picker look: quiet track, white selected segment. */
@Composable
internal fun PrepSegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = OT.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.fillQuiet)
            .padding(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) c.surfaceRaised else Color.Transparent)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = OT.type.subhead.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (isSel) c.labelPrimary else c.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// MARK: - Private helpers

private fun prepRehearsalLines(scene: ScriptScene): List<ScriptLine> =
    scene.lines
        .filter { PrepPrefs.hidden[it.id] != true }
        .map { l -> PrepPrefs.edits[l.id]?.let { l.copy(text = it) } ?: l }

/** Index of the recorded reader file for this line: count of non-mine lines before it in the original scene. */
private fun prepReaderLineIndex(scene: ScriptScene, line: ScriptLine): Int {
    val pos = scene.lines.indexOfFirst { it.id == line.id }
    return if (pos < 0) 0 else scene.lines.take(pos).count { !it.isMine }
}

private fun prepSceneTitle(a: Audition?, scene: ScriptScene): String {
    val detail = a?.pieces?.firstOrNull { it.kind == PieceKind.Scene && it.title == scene.title }?.detail
    return if (detail != null && detail.contains(" · ")) detail.substringBefore(" · ") else scene.title
}

/** "Night · about 1:30" from "INT. APARTMENT — NIGHT". */
private fun prepSceneSubtitle(scene: ScriptScene): String {
    val tod = if (scene.heading.contains("—")) scene.heading.substringAfterLast("—").trim() else scene.heading
    return "${prepCapitalized(tod)} · about ${formatDuration(scene.estimatedSeconds)}"
}

private fun prepFirstLetterWord(w: String): String {
    if (w.isEmpty()) return w
    val trail = w.takeLastWhile { !it.isLetterOrDigit() }
    val core = w.dropLast(trail.length)
    if (core.isEmpty()) return w
    val lead = core.takeWhile { !it.isLetterOrDigit() }
    val body = core.drop(lead.length)
    return lead + body.take(1) + (if (body.length > 1) "—" else "") + trail
}

private fun prepFirstLetters(text: String): String = prepWords(text).joinToString(" ") { prepFirstLetterWord(it) }

private fun prepFirstWords(text: String): String = prepWords(text).take(3).joinToString(" ") + "…"

@Composable
private fun PrepMissing(text: String) {
    PushedScreen {
        Spacer(Modifier.height(24.dp))
        Text(text, style = OT.type.body, color = OT.colors.labelSecondary)
    }
}

// MARK: - 11 Prepare

@Composable
fun PrepareScreen(auditionId: String, sceneIndex: Int) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val c = OT.colors
    val a = store.audition(auditionId)
    val practicePiece = if (a == null) store.practice.firstOrNull { it.id == auditionId } else null

    val fallbackScene = remember(a?.id, a?.role, sceneIndex) {
        if (a != null) {
            ScriptScene.placeholder(sceneIndex + 1, a.role)
        } else null
    }

    val scene = a?.scenes?.getOrNull(sceneIndex)
        ?: a?.scenes?.firstOrNull()
        ?: practicePiece?.scene
        ?: fallbackScene

    LaunchedEffect(a?.id) {
        if (a != null && a.scenes.isEmpty() && fallbackScene != null) {
            store.update(a.id) { current ->
                if (current.scenes.isEmpty()) current.copy(scenes = listOf(fallbackScene)) else current
            }
        }
    }

    if ((a == null && practicePiece == null) || scene == null) {
        PrepMissing("This scene isn’t available any more.")
        return
    }

    var peeks by remember(sceneIndex) { mutableStateOf(mapOf<String, Int>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var showPrompter by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<ScriptLine?>(null) }

    val mode = PrepPrefs.mode
    val readerName = prepReaderName(scene)
    val mineLines = scene.lines.filter { it.isMine }
    val mineCount = mineLines.size
    val learned = store.practiceProgress(auditionId, sceneIndex)?.linesLearned?.coerceAtMost(mineCount) ?: 0
    val visible = if (editing) scene.lines else scene.lines.filter { PrepPrefs.hidden[it.id] != true }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            PrepNavHeader(
                title = if (a != null) "Scene ${scene.number} of ${maxOf(1, a.scenes.size)}" else "Scene ${scene.number}",
                subtitle = prepSceneSubtitle(scene),
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingDescription = "Back",
                onLeading = { nav.back() },
            ) {
                CircleIconButton(Icons.Filled.TextFields, "Text size", onClick = { showPrompter = true })
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OT.dims.gutter)
                    .padding(top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    prepSceneTitle(a, scene),
                    style = OT.type.largeTitle,
                    color = c.labelPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                // Casting's adjustment (from a callback) — seen before every take.
                a?.notes?.takeIf { it.isNotBlank() }?.let { note ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.accentSoft)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("FROM CASTING", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                        Text(note, style = OT.type.callout, color = c.labelPrimary)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(mineCount) { i ->
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (i < learned) Modifier.background(c.accent)
                                        else Modifier.border(1.dp, c.labelTertiary, CircleShape)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("$learned of $mineCount lines learned", style = OT.type.footnote, color = c.labelTertiary)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { if (a != null) nav.push(Route.Reader(a.id)) }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(c.reader))
                        Text(
                            "$readerName · your recording",
                            style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = c.reader,
                            maxLines = 1,
                        )
                        Icon(Icons.Filled.ExpandMore, null, tint = c.reader, modifier = Modifier.size(16.dp))
                    }
                }

                if (editing) {
                    PrepEditBanner(onRestore = {
                        scene.lines.forEach { PrepPrefs.edits.remove(it.id); PrepPrefs.hidden.remove(it.id) }
                        haptics.tick()
                    })
                }

                var mineNumber = 0
                visible.forEach { line ->
                    if (line.isMine) mineNumber += 1
                    val number = mineNumber
                    val text = PrepPrefs.edits[line.id] ?: line.text
                    val level = peeks[line.id] ?: 0
                    val hiddenInCopy = PrepPrefs.hidden[line.id] == true
                    val (shown, placeholder) = when {
                        !line.isMine || editing || mode == PrepLineMode.Full -> text to false
                        mode == PrepLineMode.FirstLetters -> (if (level >= 1) text else prepFirstLetters(text)) to false
                        level == 0 -> "Tap to peek" to true
                        level == 1 -> prepFirstLetters(text) to false
                        else -> text to false
                    }
                    val chip = if (line.isMine && !editing && mode == PrepLineMode.Hide && level == 1)
                        "Peeking line $number · tap again for the whole line" else null
                    val onTap: (() -> Unit)? = when {
                        editing -> { { editingLine = line } }
                        line.isMine && mode != PrepLineMode.Full -> {
                            {
                                val next = if (mode == PrepLineMode.Hide) (level + 1) % 3 else if (level == 0) 1 else 0
                                peeks = peeks + (line.id to next)
                                haptics.tick()
                            }
                        }
                        else -> null
                    }
                    PrepLineView(
                        line = line,
                        text = shown,
                        isPlaceholder = placeholder,
                        hiddenInCopy = hiddenInCopy,
                        peekChip = chip,
                        onTap = onTap,
                    )
                }
            }
        }

        // Floating action capsule (Full lines ⌄ · Run lines ▶ · Record ◎)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(CircleShape)
                .background(c.surfaceRaised)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                PrepCapsule(mode.label, Icons.Filled.UnfoldMore, onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    PrepLineMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label, style = OT.type.body) },
                            onClick = {
                                PrepPrefs.mode = m
                                peeks = emptyMap()
                                menuOpen = false
                                haptics.tick()
                            },
                            trailingIcon = if (m == mode) {
                                { Icon(Icons.Filled.Check, null, tint = c.accent) }
                            } else null,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (editing) "Done editing" else "Edit rehearsal copy", style = OT.type.body) },
                        onClick = {
                            editing = !editing
                            menuOpen = false
                        },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, tint = c.labelSecondary) },
                    )
                }
            }
            PrepCapsule("Run lines", Icons.Filled.PlayArrow, onClick = { nav.push(Route.RunLines(auditionId, sceneIndex)) })
            PrepCapsule("Record", Icons.Filled.RadioButtonChecked, filled = true, onClick = {
                if (a != null) nav.push(Route.ShootMode(a.id)) else nav.openCamera(CameraFlow.PracticeTake)
            })
        }
    }

    if (showPrompter) PrompterSettingsSheet(onDismiss = { showPrompter = false })
    editingLine?.let { line -> PrepEditDialog(line = line, onDismiss = { editingLine = null }) }
}

@Composable
private fun PrepCapsule(label: String, icon: ImageVector, onClick: () -> Unit, filled: Boolean = false) {
    val c = OT.colors
    val fg = if (filled) c.labelOnAccent else c.labelPrimary
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (filled) c.accent else c.fillField)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PrepLineView(
    line: ScriptLine,
    text: String,
    isPlaceholder: Boolean,
    hiddenInCopy: Boolean,
    peekChip: String?,
    onTap: (() -> Unit)?,
) {
    val c = OT.colors
    val caption = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    val alpha = if (hiddenInCopy) 0.4f else 1f
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(vertical = 2.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (line.isMine) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(c.accent)
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${line.character} · YOU", style = caption, color = c.accent)
                    Text(
                        text,
                        style = if (isPlaceholder) OT.type.body else OT.type.headline.copy(fontSize = 19.sp, lineHeight = 25.sp),
                        color = if (isPlaceholder) c.labelSecondary else c.labelPrimary,
                    )
                    if (hiddenInCopy) Text("Hidden in your copy", style = OT.type.caption, color = c.labelTertiary)
                }
            }
        } else {
            Column(Modifier.padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(line.character, style = caption, color = c.reader)
                Text(text, style = OT.type.body, color = c.labelSecondary)
                if (hiddenInCopy) Text("Hidden in your copy", style = OT.type.caption, color = c.labelTertiary)
            }
        }
        if (peekChip != null) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.accentSoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Visibility, null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text(peekChip, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }
        }
    }
}

/** 11.7 — editing the rehearsal copy. */
@Composable
private fun PrepEditBanner(onRestore: () -> Unit) {
    val c = OT.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.accentSoft)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Edit, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Editing your rehearsal copy", style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            Text(
                "Tap a line to change or hide it. The original sides stay as they were.",
                style = OT.type.caption,
                color = c.labelSecondary,
            )
        }
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRestore)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Restore", style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
        }
    }
}

@Composable
private fun PrepEditDialog(line: ScriptLine, onDismiss: () -> Unit) {
    val c = OT.colors
    var text by remember(line.id) { mutableStateOf(PrepPrefs.edits[line.id] ?: line.text) }
    val isHidden = PrepPrefs.hidden[line.id] == true
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceCard,
        title = { Text(if (line.isMine) "${line.character} · YOU" else line.character, style = OT.type.headline, color = c.labelPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    if (isHidden) PrepPrefs.hidden.remove(line.id) else PrepPrefs.hidden[line.id] = true
                    onDismiss()
                }) {
                    Text(if (isHidden) "Show this line" else "Hide this line", color = c.accent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = text.trim()
                if (t.isEmpty() || t == line.text) PrepPrefs.edits.remove(line.id) else PrepPrefs.edits[line.id] = t
                onDismiss()
            }) { Text("Save", color = c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.labelSecondary) }
        },
    )
}

// MARK: - 11.2 Teleprompter settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrompterSettingsSheet(onDismiss: () -> Unit) {
    val c = OT.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.bgCanvas) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Teleprompter", style = OT.type.headline, color = c.labelPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Done", style = OT.type.headline, color = c.accent) }
            }
            val size = PrepPrefs.textSize.coerceIn(0, 2)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Because every time I tried",
                    color = Color.White,
                    fontSize = listOf(20, 26, 34)[size].sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { scaleX = if (PrepPrefs.mirror) -1f else 1f },
                )
            }
            Text(
                "Sized to read from your mark, about 6 ft away",
                style = OT.type.footnote,
                color = c.labelTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel("Text size")
            PrepSegmentedControl(listOf("Regular", "Large", "Big print"), size) { PrepPrefs.textSize = it }
            SectionLabel("Scroll")
            PrepSegmentedControl(listOf("Follows my voice", "Timed"), if (PrepPrefs.followVoice) 0 else 1) {
                PrepPrefs.followVoice = it == 0
            }
            ToggleRow(
                title = "Mirror text",
                checked = PrepPrefs.mirror,
                onCheckedChange = { PrepPrefs.mirror = it },
                detail = "For a beam-splitter prompter",
                showDivider = false,
            )
        }
    }
}

// MARK: - Speech follower (android.speech) — the highlight follows your voice

private class RunSpeechFollower(private val context: Context) {
    var spokenWords by mutableIntStateOf(0)
        private set

    val available: Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var base = 0
    private val handler = Handler(Looper.getMainLooper())
    private val relisten = Runnable { if (wanted) listen() }

    private fun count(b: Bundle?): Int =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            ?.split(" ")?.count { it.isNotBlank() } ?: 0

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                wanted = false
                return
            }
            handler.removeCallbacks(relisten)
            if (wanted) handler.postDelayed(relisten, 300)
        }
        override fun onResults(results: Bundle?) {
            base += count(results)
            spokenWords = base
            handler.removeCallbacks(relisten)
            if (wanted) handler.postDelayed(relisten, 50)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (wanted) spokenWords = base + count(partialResults)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        if (!available) return
        wanted = true
        base = 0
        spokenWords = 0
        handler.removeCallbacks(relisten)
        runCatching { recognizer?.cancel() }
        listen()
    }

    private fun listen() {
        val r = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            ?.also { it.setRecognitionListener(listener); recognizer = it }
            ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        runCatching { r.startListening(intent) }
    }

    fun stop() {
        wanted = false
        handler.removeCallbacks(relisten)
        runCatching { recognizer?.cancel() }
        base = 0
        spokenWords = 0
    }

    fun destroy() {
        stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}

// MARK: - 11.3–11.6 Run lines

private fun prepRunText(
    text: String,
    mode: PrepLineMode,
    level: Int,
    spoken: Int,
    accent: Color,
    rest: Color,
): AnnotatedString = buildAnnotatedString {
    if (level == 1) {
        withStyle(SpanStyle(color = accent)) { append(prepFirstWords(text)) }
        return@buildAnnotatedString
    }
    val effective = if (level == 2) PrepLineMode.Full else mode
    val words = prepWords(text)
    var hiddenAppended = false
    words.forEachIndexed { i, w ->
        val sep = if (i < words.size - 1) " " else ""
        if (i < spoken) {
            withStyle(SpanStyle(color = accent)) { append(w + sep) }
        } else if (effective == PrepLineMode.Full) {
            withStyle(SpanStyle(color = rest)) { append(w + sep) }
        } else if (effective == PrepLineMode.FirstLetters) {
            withStyle(SpanStyle(color = rest)) { append(prepFirstLetterWord(w) + sep) }
        } else if (effective == PrepLineMode.MaskAndReveal) {
            val masked = if (i % 2 == 0) "—" else w
            withStyle(SpanStyle(color = rest)) { append(masked + sep) }
        } else if (effective == PrepLineMode.AudioGap) {
            withStyle(SpanStyle(color = rest)) { append(w + sep) }
        } else if (!hiddenAppended) {
            hiddenAppended = true
            withStyle(SpanStyle(color = rest)) { append(if (i == 0) "· · ·" else "…") }
        }
    }
}

@Composable
fun RunLinesScreen(auditionId: String, sceneIndex: Int) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val player = rememberReaderPlayer()
    val tts = rememberTtsEngine()
    val c = OT.colors
    val a = store.audition(auditionId)
    val practicePiece = if (a == null) store.practice.firstOrNull { it.id == auditionId } else null
    val scene = a?.scenes?.getOrNull(sceneIndex)
        ?: a?.scenes?.firstOrNull()
        ?: practicePiece?.scene
        ?: if (a != null) {
            ScriptScene.placeholder(sceneIndex + 1, a.role)
        } else null
    val lines = if (scene != null) prepRehearsalLines(scene) else emptyList()
    if ((a == null && practicePiece == null) || scene == null || lines.isEmpty()) {
        PrepMissing("There are no lines to run in this scene.")
        return
    }

    KeepScreenOn()
    val mode = PrepPrefs.mode
    val readerName = prepReaderName(scene)
    val runKey = "$auditionId/$sceneIndex"

    var runNumber by remember { mutableIntStateOf(PrepPrefs.nextRun(runKey)) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val needed = remember { mutableStateMapOf<Int, Boolean>() }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endedAt by remember { mutableLongStateOf(0L) }
    var showPrompter by remember { mutableStateOf(false) }

    var micGranted by remember { mutableStateOf(prepHasMic(context)) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val follower = remember { RunSpeechFollower(context.applicationContext) }
    DisposableEffect(follower) { onDispose { follower.destroy() } }
    LaunchedEffect(Unit) {
        if (PrepPrefs.followVoice && follower.available && !micGranted) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val speechOn = PrepPrefs.followVoice && follower.available && micGranted

    val safeIndex = index.coerceIn(0, lines.size - 1)
    val line = lines[safeIndex]

    fun advance() {
        level = 0
        if (index + 1 < lines.size) {
            index += 1
        } else {
            finished = true
            endedAt = System.currentTimeMillis()
            haptics.success()
            // A complete run (not a pause) is what "lines learned" means: yours, said unprompted.
            val mine = lines.indices.filter { lines[it].isMine }
            store.recordRun(auditionId, sceneIndex, learned = mine.count { it !in needed }, total = mine.size)
        }
    }

    fun restart(from: Int) {
        player.stop()
        tts.stop()
        index = from
        level = 0
        finished = false
        needed.clear()
        startedAt = System.currentTimeMillis()
        runNumber = PrepPrefs.nextRun(runKey)
    }

    // Reader lines play your own recording or TTS, then move on. Your lines wait for your voice, audio gap, or a tap.
    LaunchedEffect(safeIndex, finished, runNumber, speechOn) {
        if (finished) {
            follower.stop()
            tts.stop()
            return@LaunchedEffect
        }
        val current = lines[safeIndex]
        if (!current.isMine) {
            follower.stop()
            val ri = prepReaderLineIndex(scene, current)
            val fileMs = prepAudioMillis(ReaderFiles.file(context, scene.id, ri)) ?: 0L
            val pacingMs = maxOf(fileMs, LineLearningMode.calculateSpokenPacingMillis(prepWords(current.text).size))
            // Move on when the reader has actually finished (TTS often runs longer than the
            // estimate), keeping the estimated pacing as a floor when nothing could be played.
            val started = System.currentTimeMillis()
            player.playAndAwait(scene.id, ri, current.text, tts, timeoutMs = pacingMs * 3 + 2_000)
            delay((pacingMs - (System.currentTimeMillis() - started)).coerceAtLeast(250))
            advance()
        } else if (mode == PrepLineMode.AudioGap) {
            val words = prepWords(current.text).size
            val gapMs = LineLearningMode.calculateAudioGapMillis(words)
            delay(gapMs)
            advance()
        } else if (speechOn) {
            follower.start()
        } else if (!PrepPrefs.followVoice) {
            follower.stop()
            delay(prepWords(current.text).size * 450L + 1500L)
            advance()
        }
    }

    val spoken = follower.spokenWords
    LaunchedEffect(spoken) {
        if (!finished && line.isMine && speechOn && spoken > 0 && spoken >= prepWords(line.text).size) {
            follower.stop()
            advance()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas),
    ) {
        val elapsedSec = (((if (finished) endedAt else System.currentTimeMillis()) - startedAt) / 1000L).toInt().coerceAtLeast(0)
        PrepNavHeader(
            title = "Run $runNumber",
            subtitle = if (finished) "${formatDuration(elapsedSec)} · ${mode.label}" else mode.label,
            leadingIcon = Icons.Filled.Close,
            leadingDescription = "Close",
            onLeading = { player.stop(); tts.stop(); nav.back() },
        ) {
            CircleIconButton(Icons.Filled.TextFields, "Text size", onClick = { showPrompter = true })
        }

        if (finished) {
            RunFinishedContent(
                lines = lines,
                needed = needed.keys.toSet(),
                modifier = Modifier.weight(1f),
                onDrill = { restart(it) },
                onAgain = { restart(0) },
                onDone = { nav.back() },
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OT.dims.gutter),
            ) {
                // progress
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEachIndexed { i, l ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        i < safeIndex -> if (l.isMine) c.accent else c.reader
                                        i == safeIndex -> c.labelPrimary
                                        else -> c.fillQuiet
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Line ${safeIndex + 1} of ${lines.size}" + if (mode != PrepLineMode.Full) " · ${mode.label}" else "",
                    style = OT.type.footnote,
                    color = c.labelTertiary,
                )
                Spacer(Modifier.height(48.dp))

                if (safeIndex > 0) {
                    val prev = lines[safeIndex - 1]
                    SectionLabel(if (prev.isMine) "You said" else "${prepCapitalized(prev.character)} said")
                    Spacer(Modifier.height(4.dp))
                    Text(prev.text, style = OT.type.callout, color = c.labelTertiary)
                    Spacer(Modifier.height(28.dp))
                }

                val status = if (line.isMine) {
                    when {
                        !speechOn -> "Your line"
                        level > 0 -> "Your line · quiet for 3 s"
                        else -> "Your line · listening"
                    }
                } else {
                    "${prepCapitalized(line.character)} · your recording"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.GraphicEq, null, tint = c.reader, modifier = Modifier.size(20.dp))
                    Text(status.uppercase(), style = OT.type.eyebrow.copy(fontSize = 12.sp), color = c.reader)
                }
                Spacer(Modifier.height(10.dp))

                val size = listOf(24, 30, 36)[PrepPrefs.textSize.coerceIn(0, 2)]
                val runStyle = TextStyle(fontSize = size.sp, lineHeight = (size * 1.22f).sp, fontWeight = FontWeight.Bold)
                if (line.isMine) {
                    Text(
                        prepRunText(
                            text = line.text,
                            mode = mode,
                            level = level,
                            spoken = if (speechOn) spoken else 0,
                            accent = c.accent,
                            rest = if (speechOn || mode != PrepLineMode.Full) c.labelTertiary else c.labelPrimary,
                        ),
                        style = runStyle,
                    )
                } else {
                    Text(line.text, style = runStyle, color = c.labelPrimary)
                }
                Spacer(Modifier.height(28.dp))

                val hint = if (line.isMine) {
                    when {
                        level == 1 -> "Here’s a start. Tap Line? again for the whole line."
                        speechOn -> "The highlight follows your voice"
                        !PrepPrefs.followVoice -> null
                        else -> "Tap next when you’ve said it"
                    }
                } else if (lines.getOrNull(safeIndex + 1)?.isMine == true) {
                    "Your line is next"
                } else null
                if (hint != null) {
                    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(c.accent))
                        Spacer(Modifier.width(8.dp))
                        Text(hint, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (!line.isMine && !ReaderFiles.file(context, scene.id, prepReaderLineIndex(scene, line)).exists()) {
                    Text(
                        "You haven’t recorded this line yet. Record $readerName yourself to hear it here.",
                        style = OT.type.footnote,
                        color = c.labelTertiary,
                    )
                }
            }

            // Floating controls (pause · Line? · skip)
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 40.dp, vertical = 8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.14f))
                        .clip(CircleShape)
                        .background(c.surfaceRaised)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RunRoundButton(Icons.Filled.Pause, "Pause") {
                        player.stop()
                        follower.stop()
                        finished = true
                        endedAt = System.currentTimeMillis()
                    }
                    val label = when {
                        mode == PrepLineMode.Full -> if (level == 1) "Whole line" else "Line?"
                        level == 1 -> "Whole line"
                        level == 2 -> "Hide line"
                        else -> "Line?"
                    }
                    PrimaryButton(
                        text = label,
                        onClick = {
                            level = if (mode == PrepLineMode.Full) (if (level == 1) 0 else 1) else (level + 1) % 3
                            if (level > 0) needed[safeIndex] = true
                            haptics.tick()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = line.isMine,
                    )
                    RunRoundButton(Icons.Filled.FastForward, "Next line") {
                        player.stop()
                        advance()
                    }
                }
            }
        }
    }

    if (showPrompter) PrompterSettingsSheet(onDismiss = { showPrompter = false })
}

@Composable
private fun RunRoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = OT.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(c.fillField)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = c.labelPrimary, modifier = Modifier.size(24.dp))
    }
}

// MARK: - 11.6 Run finished

@Composable
private fun RunFinishedContent(
    lines: List<ScriptLine>,
    needed: Set<Int>,
    modifier: Modifier,
    onDrill: (Int) -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val c = OT.colors
    val mineCount = lines.count { it.isMine }
    val neededMine = needed.filter { lines.getOrNull(it)?.isMine == true }.sorted()
    val onOwn = (mineCount - neededMine.size).coerceAtLeast(0)
    val summary = when (neededMine.size) {
        0 -> "on your own."
        1 -> "on your own. You needed a start on one line."
        else -> "on your own. You needed a start on ${neededMine.size} lines."
    }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("This run")
            Row {
                Text(
                    "$onOwn",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.labelPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(6.dp))
                Text("of $mineCount", style = OT.type.title, color = c.labelSecondary, modifier = Modifier.alignByBaseline())
            }
            Text(summary, style = OT.type.callout, color = c.labelSecondary)
            Spacer(Modifier.height(20.dp))
            SectionLabel("Your lines this run")
            Spacer(Modifier.height(4.dp))
            lines.forEachIndexed { i, l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val isNeeded = l.isMine && i in needed
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (l.isMine && !isNeeded) c.accentSoft else c.fillQuiet),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isNeeded -> Text("?", style = OT.type.caption.copy(fontWeight = FontWeight.Bold), color = c.labelSecondary)
                            l.isMine -> Icon(Icons.Filled.Check, "Said on your own", tint = c.accent, modifier = Modifier.size(14.dp))
                            else -> Text(l.character.take(1), style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp), color = c.labelTertiary)
                        }
                    }
                    Text(
                        l.text,
                        style = if (l.isMine) OT.type.headline else OT.type.callout,
                        color = if (l.isMine) c.labelPrimary else c.labelTertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (i < lines.size - 1) OTDivider(startIndent = 32.dp)
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val drill = neededMine.firstOrNull()
            if (drill != null) {
                SecondaryButton("Drill line ${drill + 1}", onClick = { onDrill(drill) })
            } else {
                SecondaryButton("Done", onClick = onDone)
            }
            PrimaryButton("Run again", onClick = onAgain)
        }
    }
}
