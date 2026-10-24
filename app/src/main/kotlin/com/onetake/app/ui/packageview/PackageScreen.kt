package com.onetake.app.ui.packageview

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppTab
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.models.Audition
import com.onetake.app.models.Destination
import com.onetake.app.models.PieceKind
import com.onetake.app.models.Take
import com.onetake.app.models.formatDuration
import com.onetake.app.models.otDue
import com.onetake.app.models.otTime
import com.onetake.app.models.otWeekdayCaps
import androidx.compose.material3.HorizontalDivider
import com.onetake.app.ui.components.CanvasBox
import com.onetake.app.ui.components.Chevron
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.HeadshotImage
import com.onetake.app.ui.components.OTCard
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PlainButton
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.ScheduleRow
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.ToggleRow
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.system.share
import com.onetake.app.ui.theme.OT
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - 20 Package · Ready to send (+ 20.1 destination, 20.3 sending, 20.4 failed, 21 sent)

private enum class PkgPhase { Review, Sending, Failed, Sent }

private fun buildPackageFileName(audition: Audition, slateName: String): String {
    val cleanSlate = slateName.ifBlank { "ACTOR" }.replace(Regex("[^a-zA-Z0-9]"), "_").uppercase()
    val cleanRole = audition.role.ifBlank { "ROLE" }.replace(Regex("[^a-zA-Z0-9]"), "_").uppercase()
    val cleanProject = audition.project.ifBlank { "PROJECT" }.replace(Regex("[^a-zA-Z0-9]"), "_").uppercase()
    return "${cleanSlate}_${cleanRole}_${cleanProject}.mp4"
}

private fun calculatePackageDuration(audition: Audition, takes: List<Take>): Int {
    return audition.pieces.sumOf { piece ->
        when (piece.kind) {
            PieceKind.Headshot -> 3
            PieceKind.Slate, PieceKind.FullLength -> 5
            PieceKind.Scene -> {
                val matchingScene = audition.scenes.firstOrNull { it.id == piece.id || it.title == piece.title }
                val targetSceneId = matchingScene?.id ?: piece.id
                val take = takes.firstOrNull { (it.sceneId == targetSceneId || it.sceneId == piece.id) && it.isCircled }
                    ?: takes.firstOrNull { it.isCircled }
                take?.durationSeconds ?: 60
            }
        }
    }
}

private fun calculatePackageMb(audition: Audition, takes: List<Take>): Int {
    val durationSeconds = calculatePackageDuration(audition, takes)
    return (durationSeconds * 1.4f).toInt().coerceIn(12, 450)
}

@Composable
fun PackageScreen(auditionId: String) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(PkgPhase.Review) }
    var progress by remember { mutableFloatStateOf(0f) }
    var useMobileData by remember { mutableStateOf(true) }
    var sendJob by remember { mutableStateOf<Job?>(null) }

    val a = store.audition(auditionId)
    if (a == null) {
        CanvasBox {}
        return
    }

    /** No hold-to-send: one tap sends. "Delivered" only appears once the destination confirms. */
    fun send() {
        sendJob?.cancel()
        phase = PkgPhase.Sending
        progress = 0f
        sendJob = scope.launch {
            while (progress < 1f) {
                delay(60)
                if (phase != PkgPhase.Sending) return@launch
                progress = minOf(1f, progress + 0.02f)
            }
            // Destination confirmed receipt → only now is it "Delivered".
            store.markSent(auditionId)
            haptics.success()
            phase = PkgPhase.Sent
        }
    }

    fun cancelSend() {
        sendJob?.cancel()
        phase = PkgPhase.Review
    }

    val done: () -> Unit = { nav.resetTo(AppTab.Auditions) }

    BackHandler(enabled = phase != PkgPhase.Review) {
        when (phase) {
            PkgPhase.Sending, PkgPhase.Failed -> cancelSend()
            PkgPhase.Sent -> done()
            PkgPhase.Review -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(OT.colors.bgCanvas)) {
        when (phase) {
            PkgPhase.Review -> PkgReview(a, onSend = { send() })
            PkgPhase.Sending -> PkgSendProgress(
                audition = a,
                progress = progress,
                onCancel = { cancelSend() },
                onDrop = { sendJob?.cancel(); phase = PkgPhase.Failed },
            )
            PkgPhase.Failed -> PkgSendFailed(
                audition = a,
                progress = progress,
                useMobileData = useMobileData,
                onMobileData = { useMobileData = it },
                onBack = { cancelSend() },
                onRetry = { send() },
            )
            PkgPhase.Sent -> PkgSent(a, onDone = done)
        }
    }
}

// MARK: - Review

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PkgReview(a: Audition, onSend: () -> Unit) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val c = OT.colors
    var showDestination by remember { mutableStateOf(false) }
    val cardShape = remember { RoundedCornerShape(24.dp) }
    val pkgFileName = remember(a, store.slate.name) { buildPackageFileName(a, store.slate.name) }

    fun handleSend() {
        if (a.destination == Destination.EmailOrLink) {
            share(context, "OneTake self-tape — ${a.project} (${a.role})")
        }
        onSend()
    }

    Column(Modifier.fillMaxSize()) {
        PkgTopBar(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back", onClick = { nav.back() })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
        ) {
            Text("Ready to send.", style = OT.type.largeTitle, color = c.labelPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text("Due ${a.due.otDue} · ${a.readyCount} of ${a.totalCount} ready", style = OT.type.callout, color = c.labelSecondary)
            Spacer(Modifier.height(20.dp))

            val durationSecs = calculatePackageDuration(a, store.takes)
            val durationText = formatDuration(durationSecs)
            val pkgMb = calculatePackageMb(a, store.takes)

            // Monolithic Assembled Tape Summary Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape)
                    .background(OT.colors.surfaceCard)
                    .padding(18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "DELIVERY PACKAGE",
                        style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = OT.colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    Text(durationText, style = OT.type.headline, color = c.labelPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(a.project, style = OT.type.title, color = Color.White)
                Text("Role: ${a.role}", style = OT.type.callout, color = c.labelSecondary)
                Spacer(Modifier.height(14.dp))
                PkgTapeStrip(a, store.takes)
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Package contents & verification", Modifier.padding(start = 4.dp, bottom = 8.dp))

            // Inset Grouped Roster
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape)
                    .background(OT.colors.surfaceCard),
            ) {
                PkgRow(icon = Icons.Outlined.Movie, title = pkgFileName, detail = "Named the way casting asked · one file", titleWeight = FontWeight.SemiBold, titleSmall = true, showDivider = true)
                PkgRow(icon = Icons.AutoMirrored.Filled.Send, title = a.destination.label, value = "$pkgMb MB · 1080p", onClick = { showDestination = true }, showDivider = true)
                PkgRow(icon = Icons.Filled.CheckCircle, iconTint = c.success, title = "Tape check", value = "Passed", onClick = { nav.push(Route.TapeCheck(a.id)) }, showDivider = true)
                PkgRow(icon = Icons.Filled.GraphicEq, iconTint = c.success, title = "Audio", value = "Both voices clear", showDivider = true)
                PkgRow(icon = Icons.Outlined.PlayArrow, title = "Preview the whole tape", detail = "Exactly what casting gets · $durationText", showDivider = false)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bgCanvas)
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PrimaryButton(
                "Send to ${a.destination.label}",
                onClick = { handleSend() },
                icon = Icons.AutoMirrored.Filled.Send,
                iconTrailing = true,
            )
            PlainButton("Choose different takes", onClick = { nav.push(Route.Takes(a.id)) })
        }
    }

    if (showDestination) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showDestination = false },
            sheetState = sheetState,
            containerColor = c.surfaceCard,
        ) {
            PkgDestinationSheet(
                selected = a.destination,
                onSelect = { d ->
                    store.setDestination(a.id, d)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showDestination = false
                        if (d == Destination.EmailOrLink) share(context, "OneTake tape — ${a.project}")
                    }
                },
            )
        }
    }
}

@Composable
private fun PkgTopBar(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(icon, label, onClick = onClick)
    }
}

@Composable
private fun PkgRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    value: String? = null,
    iconTint: Color = OT.colors.labelSecondary,
    titleWeight: FontWeight = FontWeight.Normal,
    titleSmall: Boolean = false,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = OT.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = (if (titleSmall) OT.type.subhead else OT.type.body).copy(fontWeight = titleWeight),
                    color = c.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) Text(detail, style = OT.type.footnote, color = c.labelTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) Text(value, style = OT.type.footnote, color = c.labelSecondary, maxLines = 1)
            Chevron()
        }
        if (showDivider) {
            HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
        }
    }
}

// MARK: - Tape strip

@Composable
private fun PkgTapeStrip(a: Audition, takes: List<Take>) {
    val c = OT.colors
    val soft = c.accent.copy(alpha = 0.4f)
    val duration = calculatePackageDuration(a, takes)
    val sceneCount = a.scenes.size
    val scenePart = if (sceneCount == 1) "1 scene" else "$sceneCount scenes"
    val parts = buildList {
        if (a.pieces.any { it.kind == PieceKind.Headshot }) add("headshot")
        if (a.pieces.any { it.kind == PieceKind.Slate }) add("slate")
        add(scenePart)
    }

    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Tape: ${parts.joinToString(", ")}. ${formatDuration(duration)}."
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            a.pieces.forEach { piece ->
                val weight = when (piece.kind) {
                    PieceKind.Headshot -> 0.1f
                    PieceKind.Slate, PieceKind.FullLength -> 0.12f
                    PieceKind.Scene -> 0.45f
                }
                val color = when (piece.kind) {
                    PieceKind.Headshot, PieceKind.Slate, PieceKind.FullLength -> soft
                    PieceKind.Scene -> c.accent
                }
                Box(Modifier.weight(weight).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(color))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            a.pieces.forEach { piece ->
                val (title, detail, color) = when (piece.kind) {
                    PieceKind.Headshot -> Triple("Headshot", "Front", soft)
                    PieceKind.Slate -> Triple("Slate", "0:05", soft)
                    PieceKind.FullLength -> Triple("Full length", "0:05", soft)
                    PieceKind.Scene -> {
                        val take = takes.firstOrNull { it.sceneId == piece.id && it.isCircled } ?: takes.firstOrNull { it.isCircled }
                        val d = if (take != null) "Take ${take.number} · ${formatDuration(take.durationSeconds)}" else "Pending"
                        Triple(piece.title, d, c.accent)
                    }
                }
                PkgLegend(title, detail, color, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PkgLegend(title: String, detail: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Text(title, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = OT.colors.labelPrimary, maxLines = 1)
        }
        Text(detail, style = OT.type.caption, color = OT.colors.labelTertiary, maxLines = 1)
    }
}

// MARK: - 20.1 / 20.2 Where's it going?

@Composable
private fun PkgDestinationSheet(selected: Destination, onSelect: (Destination) -> Unit) {
    val c = OT.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
    ) {
        Text(
            "Where’s it going?",
            style = OT.type.headline.copy(fontSize = 20.sp, lineHeight = 25.sp),
            color = c.labelPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "We match the format, size and file name each site accepts.",
            style = OT.type.callout,
            color = c.labelSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Destination.entries.forEachIndexed { i, d ->
            val on = d == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.RadioButton) { onSelect(d) }
                    .semantics { contentDescription = "${d.label}, ${d.spec}" + if (on) ", selected" else "" }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.label, style = OT.type.body, color = c.labelPrimary)
                    Text(d.spec, style = OT.type.footnote, color = c.labelTertiary)
                }
                if (on) {
                    Icon(Icons.Filled.CheckCircle, null, tint = c.accent, modifier = Modifier.size(24.dp))
                } else {
                    Box(Modifier.padding(2.dp).size(20.dp).border(1.5.dp, c.labelTertiary, CircleShape))
                }
            }
            if (i < Destination.entries.size - 1) OTDivider()
        }
    }
}

// MARK: - 20.3 Sending

@Composable
private fun PkgSendProgress(audition: Audition, progress: Float, onCancel: () -> Unit, onDrop: () -> Unit) {
    val store = LocalAppStore.current
    val c = OT.colors
    val dest = audition.destination.label
    val pct = (progress * 100).toInt()
    val totalMb = calculatePackageMb(audition, store.takes)
    val mb = (progress * totalMb).toInt()
    val secondsLeft = maxOf(1, ((1 - progress) * 90).toInt())
    Column(Modifier.fillMaxSize()) {
        PkgTopBar(icon = Icons.Filled.ExpandMore, label = "Cancel sending", onClick = onCancel)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("Sending to $dest", Modifier.padding(top = 4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(
                    "$pct",
                    style = OT.type.number.copy(fontSize = 72.sp, lineHeight = 80.sp),
                    color = c.labelPrimary,
                )
                Text("%", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
            }
            Text("about $secondsLeft s left · $mb of $totalMb MB", style = OT.type.footnote, color = c.labelTertiary)
            Spacer(Modifier.height(8.dp))
            // Progress bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(c.fillQuiet)
                    .semantics { contentDescription = "$pct percent sent" },
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(c.accent))
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel("Steps")
            Spacer(Modifier.height(4.dp))
            val sceneCount = audition.scenes.size
            val scenePart = if (sceneCount == 1) "1 scene" else "$sceneCount scenes"
            val parts = buildList {
                if (audition.pieces.any { it.kind == PieceKind.Headshot }) add("headshot")
                if (audition.pieces.any { it.kind == PieceKind.Slate }) add("slate")
                add(scenePart)
            }
            PkgStep("Joined ${parts.joinToString(", ")}", PkgStepState.Done)
            PkgStep("Exported 1080p · $totalMb MB", PkgStepState.Done)
            // Tapping "Uploading" simulates a dropped connection (same dev hook as iOS).
            PkgStep("Uploading", PkgStepState.Active, detail = "$mb of $totalMb MB", onClick = onDrop)
            PkgStep("Delivered — once $dest confirms", PkgStepState.Todo, divider = false)
            Spacer(Modifier.height(12.dp))
            Text(
                "You can lock your phone. It only says Delivered once $dest confirms it has the file.",
                style = OT.type.footnote,
                color = c.labelTertiary,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
        ) {
            SecondaryButton("Cancel sending", onClick = onCancel)
        }
    }
}

private enum class PkgStepState { Done, Active, Todo }

@Composable
private fun PkgStep(
    title: String,
    state: PkgStepState,
    detail: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = OT.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    PkgStepState.Done -> Icon(Icons.Filled.CheckCircle, "Done", tint = c.success, modifier = Modifier.size(22.dp))
                    PkgStepState.Active -> Box(
                        Modifier.size(20.dp).clip(CircleShape).background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Upload, "In progress", tint = c.labelOnAccent, modifier = Modifier.size(14.dp)) }
                    PkgStepState.Todo -> Box(Modifier.size(19.dp).border(1.5.dp, c.labelTertiary, CircleShape))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = OT.type.body, color = if (state == PkgStepState.Todo) c.labelTertiary else c.labelPrimary)
                if (detail != null) Text(detail, style = OT.type.caption, color = c.labelTertiary)
            }
        }
        if (divider) OTDivider(startIndent = 34.dp)
    }
}

// MARK: - 20.4 Not sent yet

@Composable
private fun PkgSendFailed(
    audition: Audition,
    progress: Float,
    useMobileData: Boolean,
    onMobileData: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val c = OT.colors
    Column(Modifier.fillMaxSize()) {
        PkgTopBar(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back", onClick = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
        ) {
            Icon(Icons.Filled.WifiOff, null, tint = c.warning, modifier = Modifier.padding(top = 4.dp).size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text("Not sent yet", style = OT.type.largeTitle, color = c.labelPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                "You lost connection. Your tape is safe on this phone and sends on its own when you're back online.",
                style = OT.type.callout,
                color = c.labelSecondary,
            )
            Spacer(Modifier.height(16.dp))
            ScheduleRow(
                eyebrow = audition.due.otWeekdayCaps,
                value = "${audition.hoursLeft}h",
                title = "Due ${audition.due.otDue}",
                detail = "Plenty of time — it'll retry automatically",
                eyebrowColor = c.due,
                valueColor = c.due,
                showChevron = false,
            )
            OTDivider()
            val store = LocalAppStore.current
            val fileName = buildPackageFileName(audition, store.slate.name)
            val totalMb = calculatePackageMb(audition, store.takes)
            ScheduleRow(
                eyebrow = "FILE",
                value = "",
                title = fileName,
                detail = "$totalMb MB · waiting for a connection",
                showChevron = false,
            )
            OTDivider()
            ToggleRow(
                title = "Use mobile data",
                detail = "About $totalMb MB",
                checked = useMobileData,
                onCheckedChange = onMobileData,
                showDivider = false,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
        ) {
            PrimaryButton("Try again now", onClick = onRetry)
        }
    }
}

// MARK: - 21 Sent

@Composable
private fun PkgSent(a: Audition, onDone: () -> Unit) {
    val c = OT.colors
    val haptics = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    val confirmedAt = remember { LocalDateTime.now() }
    val link = "OneTake tape — ${a.project}"
    LaunchedEffect(Unit) {
        haptics.success()
        clipboard.setText(AnnotatedString(link))
    }
    val h = a.hoursLeft
    val earlyText = when {
        h > 1 -> "$h hours early."
        h == 1 -> "1 hour early."
        else -> "Right on time."
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(top = 40.dp, bottom = 16.dp),
        ) {
            OTCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).rotate(1.5f),
                radius = 18.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "DELIVERED · CONFIRMED ${confirmedAt.otWeekdayCaps} ${confirmedAt.otTime.uppercase()}",
                        style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = c.success,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.CheckCircle, "Delivered", tint = c.success, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                val store = LocalAppStore.current
                val fileName = buildPackageFileName(a, store.slate.name)
                Text(fileName, style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
                Spacer(Modifier.height(6.dp))
                val durationSecs = calculatePackageDuration(a, store.takes)
                val durationText = formatDuration(durationSecs)
                val sceneCount = a.scenes.size
                val scenePart = if (sceneCount == 1) "1 scene" else "$sceneCount scenes"
                val parts = buildList {
                    if (a.pieces.any { it.kind == PieceKind.Headshot }) add("headshot")
                    if (a.pieces.any { it.kind == PieceKind.Slate }) add("slate")
                    add(scenePart)
                }
                Text("$durationText · ${parts.joinToString(" · ")}", style = OT.type.footnote, color = c.labelSecondary)
            }

            Spacer(Modifier.height(40.dp))
            Text(
                "It’s with casting.",
                style = OT.type.largeTitle.copy(fontSize = 38.sp, lineHeight = 44.sp),
                color = c.labelPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(earlyText, style = OT.type.title.copy(fontSize = 22.sp, lineHeight = 28.sp), color = c.success)
                Text("Break a leg.", style = OT.type.headline, color = c.labelSecondary, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(Modifier.height(16.dp))
            PkgSentNote(Icons.Outlined.Link, "Private link copied · works for 30 days")
            Spacer(Modifier.height(6.dp))
            PkgSentNote(Icons.Outlined.Smartphone, "All your takes stay here if they ask for another")

            Spacer(Modifier.height(28.dp))
            PkgHeadshotFan(Modifier.fillMaxWidth().height(170.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PrimaryButton("Done", onClick = onDone)
            PlainButton("Copy link again", onClick = { clipboard.setText(AnnotatedString(link)); haptics.tick() })
        }
    }
}

@Composable
private fun PkgSentNote(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = OT.colors.labelTertiary, modifier = Modifier.size(16.dp))
        Text(text, style = OT.type.footnote, color = OT.colors.labelSecondary)
    }
}

/** Three take frames fanned out, like the Figma "Sent" illustration. Decorative. */
@Composable
private fun PkgHeadshotFan(modifier: Modifier = Modifier) {
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        PkgFanCard(Modifier.offset(x = 70.dp, y = 12.dp).rotate(8f), "right")
        PkgFanCard(Modifier.offset(x = 20.dp, y = 0.dp).rotate(3f), "front")
        PkgFanCard(Modifier.offset(x = (-50).dp, y = 8.dp).rotate(-8f), "left")
    }
}

@Composable
private fun PkgFanCard(modifier: Modifier, name: String) {
    Box(
        modifier
            .size(110.dp, 140.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(3.dp),
    ) {
        HeadshotImage(Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)), name = name)
    }
}
