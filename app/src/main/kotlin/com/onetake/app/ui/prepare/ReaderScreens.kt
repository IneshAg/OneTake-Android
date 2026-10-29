package com.onetake.app.ui.prepare

import com.onetake.app.ui.system.playAndAwait
import android.widget.Toast
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.models.formatDuration
import com.onetake.app.ui.components.LargeTitle
import com.onetake.app.ui.components.ListRow
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.PushedScreen
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.otCard
import com.onetake.app.ui.system.ReaderFiles
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.system.rememberReaderPlayer
import com.onetake.app.ui.system.rememberReaderRecorder
import com.onetake.app.ui.theme.OT
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Android port of iOS ReaderView (12) and RecordReaderView (13).
// The reader is ALWAYS the actor's own recorded voice — no AI voice, no other people's tracks.

private val rdrVolumes = listOf("Same as me", "Quieter", "Much quieter")

private fun rdrBarHeight(i: Int): Float {
    val v = ((i * 37 + 11) % 17) / 16f
    return 0.3f + 0.7f * v
}

/** Static waveform bars; bars before `progress` use the active colour. */
@Composable
private fun RdrWave(
    bars: Int,
    progress: Float,
    active: Color,
    idle: Color,
    modifier: Modifier,
    seed: Int = 0,
) {
    Canvas(modifier) {
        val n = bars.coerceAtLeast(1)
        val slot = size.width / n
        val w = slot * 0.5f
        for (i in 0 until n) {
            val h = rdrBarHeight(i + seed) * size.height
            val x = i * slot + (slot - w) / 2f
            drawRoundRect(
                color = if (i.toFloat() / n < progress) active else idle,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

@Composable
private fun RdrPlayCircle(
    playing: Boolean,
    description: String,
    onClick: () -> Unit,
    size: Dp = 36.dp,
    background: Color = OT.colors.fillField,
    tint: Color = OT.colors.labelPrimary,
) {
    Box(
        Modifier
            .size(maxOf(size, 48.dp))
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

// MARK: - 12 Reader — "Who reads Maya?"

@Composable
fun ReaderScreen(auditionId: String, sceneIndex: Int = 0) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val player = rememberReaderPlayer()
    val c = OT.colors
    val a = store.audition(auditionId)
    val scene = a?.scenes?.getOrNull(sceneIndex)
    val readerName = scene?.let { prepReaderName(it) } ?: "Reader"
    val readerLines = scene?.lines?.filter { !it.isMine } ?: emptyList()
    val sceneId = scene?.id.orEmpty()

    var playingLine by remember { mutableStateOf<Int?>(null) }
    var volumeMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    PushedScreen(
        bottomBar = {
            PrimaryButton(text = "Continue", onClick = { player.stop(); nav.back() })
        },
    ) {
        LargeTitle(
            title = "Who reads $readerName?",
            subtitle = "Record $readerName’s lines in your own voice. We leave the pauses for yours, and it plays under your takes.",
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("Your reader tracks")
        Spacer(Modifier.height(4.dp))

        // A real per-line listing of recorded audio, backed by the
        // same .m4a files RecordReaderScreen writes — not a fabricated list of named "tracks".
        if (readerLines.isEmpty()) {
            Text("No reader lines in this scene yet.", style = OT.type.footnote, color = c.labelTertiary)
        } else {
            readerLines.forEachIndexed { i, line ->
                val recorded = ReaderFiles.file(context, sceneId, i).exists()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .then(
                            if (recorded) Modifier.clickable {
                                if (playingLine == i) {
                                    player.stop(); playingLine = null
                                } else {
                                    player.play(sceneId, i); playingLine = i; haptics.tick()
                                }
                            } else Modifier
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RdrPlayCircle(
                        playing = playingLine == i,
                        description = if (recorded) "Play line ${i + 1}" else "Line ${i + 1} not recorded",
                        onClick = {
                            if (!recorded) return@RdrPlayCircle
                            if (playingLine == i) { player.stop(); playingLine = null } else { player.play(sceneId, i); playingLine = i }
                        },
                        background = if (recorded) c.accentSoft else c.fillField,
                        tint = if (recorded) c.accent else c.labelTertiary,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Line ${i + 1}", style = OT.type.body, color = c.labelPrimary)
                        Text(
                            if (recorded) "Recorded · “${line.text.take(40)}”" else "Not recorded yet",
                            style = OT.type.footnote,
                            color = c.labelTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (i < readerLines.size - 1) OTDivider(startIndent = 56.dp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { volumeMenu = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = c.labelSecondary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$readerName’s volume on the tape", style = OT.type.body, color = c.labelPrimary)
                Text("Casting hears you first", style = OT.type.footnote, color = c.labelTertiary)
            }
            Box {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(store.readerVolume, style = OT.type.body, color = c.labelSecondary)
                    Icon(Icons.Filled.UnfoldMore, null, tint = c.labelSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = volumeMenu, onDismissRequest = { volumeMenu = false }) {
                    rdrVolumes.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v, style = OT.type.body) },
                            onClick = {
                                store.readerVolume = v
                                volumeMenu = false
                                haptics.tick()
                            },
                            trailingIcon = if (v == store.readerVolume) {
                                { Icon(Icons.Filled.Check, null, tint = c.accent) }
                            } else null,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        ListRow(
            title = "Record a new track",
            detail = "Your voice · we leave pauses for your lines",
            icon = Icons.Filled.Mic,
            onClick = {
                player.stop()
                playingLine = null
                nav.push(Route.RecordReader(auditionId, sceneIndex))
            },
        )
    }
}

// MARK: - 13 Record the reader yourself

@Composable
fun RecordReaderScreen(auditionId: String, sceneIndex: Int = 0) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val recorder = rememberReaderRecorder()
    val player = rememberReaderPlayer()
    val c = OT.colors
    val a = store.audition(auditionId)
    val scene = a?.scenes?.getOrNull(sceneIndex)
    val readerPositions = scene?.lines?.let { ls -> ls.indices.filter { !ls[it].isMine } } ?: emptyList()
    if (a == null || scene == null || readerPositions.isEmpty()) {
        PushedScreen {
            Spacer(Modifier.height(24.dp))
            Text("There are no reader lines to record in this scene.", style = OT.type.body, color = c.labelSecondary)
        }
        return
    }
    val lines = scene.lines
    val count = readerPositions.size
    val readerName = prepReaderName(scene)

    var phase by rememberSaveable { mutableIntStateOf(0) } // 0 intro · 1 recording lines · 2 all done
    var lineIndex by rememberSaveable { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableLongStateOf(0L) }
    val durations = remember { mutableStateMapOf<Int, Int>() }
    val pauses = remember { mutableStateMapOf<Int, Float>() }
    var lastSaved by remember { mutableStateOf<Int?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }
    var playingAll by remember { mutableStateOf(false) }
    var playAllMs by remember { mutableLongStateOf(0L) }

    fun startRec() {
        player.stop()
        if (!recorder.start(scene.id, lineIndex)) {
            Toast.makeText(context, "Couldn't start recording. Is another app using the microphone?", Toast.LENGTH_SHORT).show()
            return
        }
        recMs = 0L
        recording = true
        phase = 1
        haptics.impact()
    }

    fun goNext() {
        if (lineIndex + 1 < count) lineIndex += 1 else phase = 2
    }

    fun stopRec(advance: Boolean) {
        recorder.stop()
        recording = false
        durations[lineIndex] = ((recMs + 500L) / 1000L).toInt().coerceAtLeast(1)
        lastSaved = lineIndex
        haptics.success()
        if (advance) goNext()
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted && pendingStart) startRec()
        pendingStart = false
    }

    fun requestStart() {
        if (prepHasMic(context)) {
            micDenied = false
            startRec()
        } else {
            pendingStart = true
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Seconds of silence left for your line before reader line k (only when your line precedes it). */
    fun pauseFor(k: Int): Float? {
        val pos = readerPositions[k]
        val prev = lines.getOrNull(pos - 1) ?: return null
        if (!prev.isMine) return null
        return pauses[k] ?: ((prepWords(prev.text).size * 0.35f * 2f).roundToInt() / 2f).coerceIn(2f, 6f)
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val t0 = System.currentTimeMillis()
        while (true) {
            recMs = System.currentTimeMillis() - t0
            delay(100L)
        }
    }

    LaunchedEffect(playingAll) {
        if (!playingAll) return@LaunchedEffect
        val t0 = System.currentTimeMillis()
        launch {
            while (true) {
                playAllMs = System.currentTimeMillis() - t0
                delay(100L)
            }
        }
        for (k in 0 until count) {
            pauseFor(k)?.let { delay((it * 1000f).toLong()) }
            // Wait for each recording to finish — lines recorded on an earlier visit aren't in
            // `durations`, and a guessed 2 s cut them off or overlapped the next one.
            val ms = prepAudioMillis(ReaderFiles.file(context, scene.id, k)) ?: 0L
            player.playAndAwait(scene.id, k, text = "", ttsEngine = null, timeoutMs = ms + 2_000)
            delay(150L)
        }
        player.stop()
        playingAll = false
    }

    when (phase) {
        // 13.1 — intro
        0 -> PushedScreen(
            bottomBar = {
                if (micDenied) {
                    Text(
                        "Microphone access is off. Allow it in Settings to record $readerName.",
                        style = OT.type.footnote,
                        color = c.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
                PrimaryButton("Record line ${lineIndex + 1}", onClick = { requestStart() })
            },
        ) {
            LargeTitle(
                title = "Read $readerName’s lines",
                subtitle = "${if (count == 1) "1 line" else "$count lines"}, about ${count * 10} seconds. One line at a time.",
            )
            Spacer(Modifier.height(12.dp))
            RdrTipRow(Icons.AutoMirrored.Filled.VolumeUp, "Softer than you’d act it", "Casting should hear you first.")
            OTDivider(startIndent = 50.dp)
            RdrTipRow(Icons.Filled.ContentCut, "Don’t leave gaps", "We add pauses for your lines after.")
            OTDivider(startIndent = 50.dp)
            RdrTipRow(Icons.Filled.Hearing, "Quiet room, no headphones", "Hold the phone like a call.")
            Spacer(Modifier.height(16.dp))
            val hasMic = prepHasMic(context)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.fillQuiet)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Mic, null, tint = c.labelSecondary, modifier = Modifier.size(20.dp))
                Text("Phone microphone", style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (hasMic) c.success else c.labelTertiary))
                Text(
                    if (hasMic) "Sounds clear" else "Needs access",
                    style = OT.type.footnote,
                    color = if (hasMic) c.success else c.labelTertiary,
                )
            }
        }

        // 13.2 / 13.3 — one line at a time
        1 -> Column(
            Modifier
                .fillMaxSize()
                .background(c.bgCanvas),
        ) {
            PrepNavHeader(
                title = "Record $readerName",
                subtitle = if (recording) "Recording line ${lineIndex + 1}" else "Line ${lineIndex + 1} of $count",
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingDescription = "Back",
                onLeading = {
                    if (recording) {
                        recorder.stop()
                        recording = false
                    }
                    nav.back()
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OT.dims.gutter),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(count) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        i == lineIndex -> c.accent
                                        durations.containsKey(i) -> c.reader
                                        else -> c.fillQuiet
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                val pos = readerPositions[lineIndex]
                val cue = lines.getOrNull(pos - 1)?.takeIf { it.isMine }
                if (cue != null) {
                    SectionLabel("After your line")
                    Spacer(Modifier.height(4.dp))
                    Text(cue.text, style = OT.type.callout, color = c.labelSecondary)
                    Spacer(Modifier.height(16.dp))
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (recording) Modifier.clip(RoundedCornerShape(20.dp)).background(c.reader.copy(alpha = 0.12f))
                            else Modifier.otCard(20.dp, c.surfaceCard)
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${readerName.uppercase()} · LINE ${lineIndex + 1}",
                        style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = c.reader,
                    )
                    Text(
                        lines[pos].text,
                        fontSize = 22.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.labelPrimary,
                    )
                }
                Spacer(Modifier.height(14.dp))

                if (recording) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(OT.camera.recRed))
                        Text(
                            formatDuration((recMs / 1000L).toInt()),
                            style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = OT.camera.recRed,
                        )
                        RdrWave(
                            bars = 30,
                            progress = 1f,
                            active = OT.camera.recRed,
                            idle = OT.camera.recRed,
                            modifier = Modifier.weight(1f).height(24.dp),
                            seed = (recMs / 150L).toInt(),
                        )
                    }
                } else {
                    val prev = lastSaved
                    val savedHere = durations[lineIndex]
                    if (prev != null && prev == lineIndex - 1) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = c.success, modifier = Modifier.size(18.dp))
                            Text(
                                "Line ${prev + 1} saved · ${formatDuration(durations[prev] ?: 0)}",
                                style = OT.type.footnote,
                                color = c.labelSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) {
                                        runCatching { ReaderFiles.file(context, scene.id, prev).delete() }
                                        durations.remove(prev)
                                        lineIndex = prev
                                        lastSaved = null
                                    }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Undo", style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                            }
                        }
                    } else if (savedHere != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = c.success, modifier = Modifier.size(18.dp))
                            Text(
                                "Line ${lineIndex + 1} saved · ${formatDuration(savedHere)}",
                                style = OT.type.footnote,
                                color = c.labelSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) { prepPlay(player, scene.id, lineIndex) }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Play", style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                            }
                        }
                    }
                }
                if (micDenied) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Microphone access is off. Allow it in Settings to record $readerName.",
                        style = OT.type.footnote,
                        color = c.danger,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (recording) "Tap stop when you’ve said the line" else "Tap record when you’re ready",
                    style = OT.type.footnote,
                    color = c.labelTertiary,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (recording) {
                        RdrSideButton(Icons.Filled.Headphones, "Listen", "Stop and listen back") {
                            stopRec(advance = false)
                            prepPlay(player, scene.id, lineIndex)
                        }
                    } else if (lineIndex > 0) {
                        RdrSideButton(Icons.Filled.FastRewind, "Line $lineIndex", "Back to line $lineIndex") {
                            player.stop()
                            lineIndex -= 1
                            lastSaved = null
                        }
                    } else {
                        Spacer(Modifier.width(72.dp))
                    }

                    // Record / stop
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .border(4.dp, if (recording) OT.camera.recRed else c.fillField, CircleShape)
                            .clickable(role = Role.Button) {
                                if (recording) stopRec(advance = true) else requestStart()
                            }
                            .semantics { contentDescription = if (recording) "Stop recording" else "Record line ${lineIndex + 1}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (recording) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(OT.camera.recRed))
                        } else {
                            Box(Modifier.size(66.dp).clip(CircleShape).background(OT.camera.recRed))
                        }
                    }

                    if (recording) {
                        RdrSideButton(Icons.Filled.FastForward, "Next", "Save and go to the next line") {
                            stopRec(advance = true)
                        }
                    } else {
                        val label = if (durations.containsKey(lineIndex)) "Next" else "Skip"
                        RdrSideButton(Icons.Filled.FastForward, label, if (label == "Skip") "Skip this line" else "Next line") {
                            player.stop()
                            goNext()
                        }
                    }
                }
            }
        }

        // 13.7 — all lines done
        else -> {
            val total = (0 until count).sumOf { (durations[it] ?: 0).toDouble() + (pauseFor(it) ?: 0f).toDouble() }.roundToInt()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(c.bgCanvas),
            ) {
                PrepNavHeader(
                    title = "$readerName’s track",
                    subtitle = "${if (count == 1) "1 line" else "$count lines"} · ${formatDuration(total)} with pauses",
                    leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    leadingDescription = "Back",
                    onLeading = {
                        player.stop()
                        playingAll = false
                        lineIndex = count - 1
                        phase = 1
                    },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = OT.dims.gutter),
                ) {
                    LargeTitle(
                        title = if (count == 1) "Your line is done" else "All $count lines done",
                        subtitle = "Play the scene to check the timing. Amber is where you speak.",
                    )
                    Spacer(Modifier.height(12.dp))
                    for (k in 0 until count) {
                        val p = pauseFor(k)
                        if (p != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(3.dp).height(20.dp).clip(CircleShape).background(c.due))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Your line · ${String.format(Locale.US, "%.1f", p)} s",
                                    style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                                    color = c.due,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(c.fillQuiet),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RdrStepperButton("−", "Shorter pause") { pauses[k] = (p - 0.5f).coerceAtLeast(0.5f) }
                                    Box(Modifier.width(0.5.dp).height(18.dp).background(c.separator))
                                    RdrStepperButton("+", "Longer pause") { pauses[k] = (p + 0.5f).coerceAtMost(10f) }
                                }
                            }
                            OTDivider()
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RdrPlayCircle(
                                playing = false,
                                description = "Play $readerName line ${k + 1}",
                                onClick = { prepPlay(player, scene.id, k) },
                                size = 28.dp,
                                background = c.accentSoft,
                                tint = c.accent,
                            )
                            Text("$readerName · ${k + 1}", style = OT.type.body, color = c.labelPrimary, modifier = Modifier.weight(1f))
                            Text(formatDuration(durations[k] ?: 0), style = OT.type.footnote, color = c.labelTertiary)
                        }
                        OTDivider()
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Pauses come from how long your lines took in Run lines. Nudge any that feel rushed.",
                        style = OT.type.footnote,
                        color = c.labelTertiary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RdrPlayCircle(
                            playing = playingAll,
                            description = if (playingAll) "Pause the scene" else "Play the scene",
                            onClick = {
                                if (playingAll) player.stop()
                                playingAll = !playingAll
                            },
                            size = 44.dp,
                            background = c.accentSoft,
                            tint = c.accent,
                        )
                        RdrWave(
                            bars = 36,
                            progress = if (playingAll && total > 0) (playAllMs / 1000f) / total else 0f,
                            active = c.reader,
                            idle = c.fillField,
                            modifier = Modifier.weight(1f).height(28.dp),
                        )
                        Text(
                            formatDuration(if (playingAll) (playAllMs / 1000L).toInt() else total),
                            style = OT.type.footnote,
                            color = c.labelSecondary,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
                ) {
                    // Every line is already saved to disk as it's recorded (ReaderLineRecorder
                    // writes straight to ReaderFiles) — there's no separate named-track object to
                    // create, so this just confirms and returns to Reader, which now shows the
                    // real recorded/not-recorded state per line.
                    PrimaryButton("Save track", onClick = {
                        player.stop()
                        playingAll = false
                        haptics.success()
                        nav.back()
                    })
                }
            }
        }
    }
}

@Composable
private fun RdrTipRow(icon: ImageVector, title: String, detail: String) {
    val c = OT.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OT.type.body, color = c.labelPrimary)
            Text(detail, style = OT.type.footnote, color = c.labelTertiary)
        }
    }
}

@Composable
private fun RdrSideButton(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    val c = OT.colors
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.fillField),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.labelPrimary, modifier = Modifier.size(24.dp)) }
        Text(label, style = OT.type.caption, color = c.labelSecondary)
    }
}

@Composable
private fun RdrStepperButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 48.dp, height = 40.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = OT.type.headline, color = OT.colors.labelPrimary)
    }
}

