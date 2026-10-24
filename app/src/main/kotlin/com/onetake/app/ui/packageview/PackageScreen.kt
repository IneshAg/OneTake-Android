package com.onetake.app.ui.packageview

import android.net.Uri
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppTab
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.data.MediaExporter
import com.onetake.app.models.Audition
import com.onetake.app.models.Destination
import com.onetake.app.models.PieceKind
import com.onetake.app.models.Take
import com.onetake.app.models.formatDuration
import com.onetake.app.models.otDue
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
import com.onetake.app.ui.components.SecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.system.shareFile
import com.onetake.app.ui.system.viewFile
import com.onetake.app.ui.theme.OT
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - 20 Package · Ready to send (+ 20.1 destination, 20.3 exporting, 20.4 failed, 21 sent)
//
// There's no casting-site API this app can submit a tape to directly, so "sending" is real:
// MediaExporter actually composes the slate, full-length and circled takes into one .mp4 on disk,
// and handing it off happens through the real Android share sheet (another app, Drive, Bluetooth,
// mail — whatever's installed) — not a simulated upload that claims a destination confirmed
// receipt it never saw.

private enum class PkgPhase { Review, Sending, Failed, Sent }

private fun fileUriToFile(uriString: String?): File? =
    uriString?.let { runCatching { File(Uri.parse(it).path ?: return null) }.getOrNull() }

private fun exportedSizeLabel(a: Audition): String {
    val file = fileUriToFile(a.exportedFileUri)
    if (file == null || !file.exists()) return "Not exported yet"
    return "${(file.length() / 1_048_576L).coerceAtLeast(1)} MB"
}

/** Sum of the real circled takes' recorded durations — slate/full-length don't carry a stored
 *  duration of their own, so (mirroring iOS) this only totals what's actually measurable. */
private fun totalDurationLabel(a: Audition, takes: List<Take>): String {
    val circled = takes.filter { it.auditionId == a.id && it.isCircled }
    if (circled.isEmpty()) return "—"
    return formatDuration(circled.sumOf { it.durationSeconds })
}

@Composable
fun PackageScreen(auditionId: String) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(PkgPhase.Review) }
    var progress by remember { mutableFloatStateOf(0f) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var confirmSent by remember { mutableStateOf(false) }

    val a = store.audition(auditionId)
    if (a == null) {
        CanvasBox {}
        return
    }

    /** Real export, not a simulated upload: MediaExporter actually composes the recorded files,
     *  and only once that succeeds does the share sheet open so the real file can go somewhere. */
    fun send() {
        val takesForAudition = store.takes.filter { it.auditionId == a.id }
        val slate = store.slate
        phase = PkgPhase.Sending
        progress = 0f
        exportError = null
        exportJob = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    MediaExporter.export(
                        context, a, takesForAudition, slate,
                        isCancelled = { !isActive },
                    ) { p -> scope.launch { progress = p } }
                }
                store.setExportedFile(a.id, Uri.fromFile(file).toString())
                haptics.success()
                shareFile(context, file, "OneTake self-tape — ${a.project} (${a.role})")
                // The share sheet can't tell us whether anything was actually sent, so ask once
                // the actor is back rather than marking it Sent the moment the sheet opens.
                phase = PkgPhase.Review
                confirmSent = true
            } catch (e: CancellationException) {
                throw e // Cancel goes back to Review (cancelExport) — not to "Couldn't export".
            } catch (e: Exception) {
                exportError = e.message ?: "Something went wrong composing your tape."
                phase = PkgPhase.Failed
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        phase = PkgPhase.Review
    }

    val done: () -> Unit = { nav.resetTo(AppTab.Auditions) }

    BackHandler(enabled = phase != PkgPhase.Review) {
        when (phase) {
            PkgPhase.Sending, PkgPhase.Failed -> cancelExport()
            PkgPhase.Sent -> done()
            PkgPhase.Review -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(OT.colors.bgCanvas)) {
        when (phase) {
            PkgPhase.Review -> PkgReview(a, onSend = { send() })
            PkgPhase.Sending -> PkgSendProgress(progress = progress, onCancel = { cancelExport() })
            PkgPhase.Failed -> PkgSendFailed(message = exportError, onBack = { cancelExport() }, onRetry = { send() })
            PkgPhase.Sent -> PkgSent(a, onDone = done)
        }
    }

    if (confirmSent) {
        AlertDialog(
            onDismissRequest = { confirmSent = false },
            title = { Text("Did your tape go out?", style = OT.type.headline) },
            text = {
                Text(
                    "Mark ${a.project} as sent once you've delivered it. If you backed out of the share sheet, you can send it again from here.",
                    style = OT.type.callout,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSent = false
                    store.markSent(a.id)
                    phase = PkgPhase.Sent
                }) { Text("Yes, it's sent", color = OT.colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSent = false }) { Text("Not yet", color = OT.colors.labelPrimary) }
            },
            containerColor = OT.colors.surfaceRaised,
        )
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
    val pkgFileName = remember(a, store.slate.name) { MediaExporter.deliverableFileName(a, store.slate) }
    val auditionTakes = store.takes.filter { it.auditionId == a.id }

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

            val durationText = totalDurationLabel(a, auditionTakes)
            val sizeLabel = exportedSizeLabel(a)

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
                        "ON THE TAPE, IN ORDER",
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
                PkgTapeStrip(a, auditionTakes)
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
                PkgRow(icon = Icons.AutoMirrored.Filled.Send, title = a.destination.label, value = sizeLabel, onClick = { showDestination = true }, showDivider = true)
                PkgRow(icon = Icons.Filled.CheckCircle, iconTint = c.success, title = "Tape check", value = "Review", onClick = { nav.push(Route.TapeCheck(a.id)) }, showDivider = true)
                PkgRow(icon = Icons.Filled.GraphicEq, iconTint = c.success, title = "Audio", value = "From your takes", showDivider = true)
                val exportedFile = fileUriToFile(a.exportedFileUri)
                if (exportedFile != null && exportedFile.exists()) {
                    PkgRow(
                        icon = Icons.Outlined.PlayArrow,
                        title = "Preview the whole tape",
                        detail = "What's actually in the exported file",
                        onClick = { viewFile(context, exportedFile) },
                        showDivider = false,
                    )
                } else {
                    PkgRow(icon = Icons.Outlined.PlayArrow, title = "Preview the whole tape", detail = "Available once you've exported it", showDivider = false)
                }
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
                onClick = onSend,
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
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showDestination = false }
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

    data class Segment(val title: String, val detail: String, val color: Color, val weight: Float)

    val segments = buildList {
        if (a.pieces.any { it.kind == PieceKind.Headshot }) {
            val done = a.pieces.firstOrNull { it.kind == PieceKind.Headshot }?.isDone == true
            add(Segment("Headshot", if (done) "Captured" else "Not yet", soft, 0.1f))
        }
        if (a.pieces.any { it.kind == PieceKind.Slate }) {
            add(Segment("Slate", if (a.slateVideoUri != null) "Recorded" else "Not yet", soft, 0.12f))
        }
        if (a.pieces.any { it.kind == PieceKind.FullLength }) {
            add(Segment("Full length", if (a.fullLengthVideoUri != null) "Recorded" else "Not yet", soft, 0.12f))
        }
        a.scenes.forEachIndexed { i, scene ->
            val take = takes.firstOrNull { it.sceneIndex == i && it.isCircled } ?: takes.firstOrNull { it.sceneIndex == i }
            val detail = take?.let { "Take ${it.number} · ${formatDuration(it.durationSeconds)}" } ?: "Not shot yet"
            add(Segment(scene.title, detail, c.accent, 0.45f))
        }
    }

    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Tape order: " + segments.joinToString(", ") { "${it.title}, ${it.detail}" }
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            segments.forEach { seg ->
                Box(Modifier.weight(seg.weight).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(seg.color))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { seg -> PkgLegend(seg.title, seg.detail, seg.color, Modifier.weight(1f)) }
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
            "We match the file name each site asks for. Every destination hands off through your share sheet — there's no direct upload API.",
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

// MARK: - 20.3 Exporting (real MediaExporter progress — no simulated upload)

@Composable
private fun PkgSendProgress(progress: Float, onCancel: () -> Unit) {
    val c = OT.colors
    val pct = (progress * 100).toInt()
    Column(Modifier.fillMaxSize()) {
        PkgTopBar(icon = Icons.Filled.ExpandMore, label = "Cancel", onClick = onCancel)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("Packaging your tape", Modifier.padding(top = 4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text("$pct", style = OT.type.number.copy(fontSize = 72.sp, lineHeight = 80.sp), color = c.labelPrimary)
                Text("%", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
            }
            Text("Joining your slate, full length and circled takes into one file.", style = OT.type.footnote, color = c.labelTertiary)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(c.fillQuiet)
                    .semantics { contentDescription = "$pct percent composed" },
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(c.accent))
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel("Steps")
            Spacer(Modifier.height(4.dp))
            PkgStep("Found your recorded pieces", PkgStepState.Done)
            PkgStep("Composing one file", if (progress < 1f) PkgStepState.Active else PkgStepState.Done, detail = "$pct%")
            PkgStep("Done — opens your share sheet", if (progress >= 1f) PkgStepState.Done else PkgStepState.Todo, divider = false)
            Spacer(Modifier.height(12.dp))
            Text(
                "Exported locally — nothing leaves this phone until you choose where to send it next.",
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
            SecondaryButton("Cancel", onClick = onCancel)
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

// MARK: - 20.4 Couldn't export

@Composable
private fun PkgSendFailed(message: String?, onBack: () -> Unit, onRetry: () -> Unit) {
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
            Icon(Icons.Filled.Warning, null, tint = c.warning, modifier = Modifier.padding(top = 4.dp).size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text("Couldn't package it", style = OT.type.largeTitle, color = c.labelPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                message ?: "Something went wrong composing your tape. Your takes are untouched on this phone — this only affects the combined file.",
                style = OT.type.callout,
                color = c.labelSecondary,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
        ) {
            PrimaryButton("Try again", onClick = onRetry)
        }
    }
}

// MARK: - 21 Sent — handed off through the real share sheet, not a fabricated server confirmation

@Composable
private fun PkgSent(a: Audition, onDone: () -> Unit) {
    val c = OT.colors
    val haptics = rememberHaptics()
    val sharedAt = remember { LocalDateTime.now() }
    LaunchedEffect(Unit) { haptics.success() }
    val h = a.hoursLeft
    val earlyText = when {
        h > 1 -> "$h hours early."
        h == 1 -> "1 hour early."
        else -> "Right on time."
    }
    val sceneCount = a.scenes.size
    val scenePart = if (sceneCount == 1) "1 scene" else "$sceneCount scenes"

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
                        "SHARED · ${sharedAt.otWeekdayCaps} ${sharedAt.let { "%02d:%02d".format(it.hour, it.minute) }}",
                        style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = c.success,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.CheckCircle, "Shared", tint = c.success, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                val store = LocalAppStore.current
                val fileName = MediaExporter.deliverableFileName(a, store.slate)
                Text(fileName, style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
                Spacer(Modifier.height(6.dp))
                Text("Headshot · slate · $scenePart", style = OT.type.footnote, color = c.labelSecondary)
            }

            Spacer(Modifier.height(40.dp))
            Text(
                "It’s on its way.",
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
            PkgSentNote(Icons.Outlined.Share, "Handed off through your share sheet to ${a.destination.label}")
            Spacer(Modifier.height(6.dp))
            PkgSentNote(Icons.Outlined.Movie, "All your takes stay here if they ask for another")

            Spacer(Modifier.height(28.dp))
            PkgHeadshotFan(Modifier.fillMaxWidth().height(170.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OT.dims.gutter, vertical = 8.dp),
        ) {
            PrimaryButton("Done", onClick = onDone)
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
