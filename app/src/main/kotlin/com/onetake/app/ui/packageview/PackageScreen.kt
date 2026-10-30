package com.onetake.app.ui.packageview

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.onetake.app.CameraFlow
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.data.MediaExporter
import com.onetake.app.data.TapeAnalysis
import com.onetake.app.data.TapeChecker
import com.onetake.app.models.Audition
import com.onetake.app.models.Destination
import com.onetake.app.models.PieceKind
import com.onetake.app.models.Take
import com.onetake.app.models.formatDuration
import com.onetake.app.models.otDue
import com.onetake.app.models.otWeekdayCaps
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
import com.onetake.app.ui.takes.TkPlayerCard
import com.onetake.app.ui.takes.TkPlayerState
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

private sealed interface PkgPiecePreview {
    data object Headshots : PkgPiecePreview
    data class Slate(val videoUri: String?) : PkgPiecePreview
    data class FullLength(val videoUri: String?) : PkgPiecePreview
    data class SceneTake(val sceneTitle: String, val sceneIndex: Int, val take: Take?) : PkgPiecePreview
    data object AudioInspection : PkgPiecePreview
    data object DeliverableDetails : PkgPiecePreview
}

private fun fileUriToFile(uriString: String?): File? =
    uriString?.let { runCatching { File(Uri.parse(it).path ?: return null) }.getOrNull() }

private fun exportedSizeLabel(a: Audition): String {
    val file = fileUriToFile(a.exportedFileUri)
    if (file == null || !file.exists()) return "Not exported yet"
    return "${(file.length() / 1_048_576L).coerceAtLeast(1)} MB"
}

/** Sum of the real circled takes' recorded durations — slate/full-length don't carry a stored
 *  duration of their own, so this only totals what's actually measurable. */
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
    var pendingZipToSave by remember { mutableStateOf<File?>(null) }

    val a = store.audition(auditionId)
    if (a == null) {
        CanvasBox {}
        return
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { destUri ->
        val sourceFile = pendingZipToSave
        if (destUri != null && sourceFile != null && sourceFile.exists()) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        haptics.success()
                        Toast.makeText(context, "Saved ${sourceFile.name} to device", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not save file: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        pendingZipToSave = null
    }

    /** Real package export: MediaExporter builds a valid .zip archive containing all deliverables
     *  and hands it directly to the system share sheet. */
    fun sendZip() {
        val takesForAudition = store.takes.filter { it.auditionId == a.id }
        val slate = store.slate
        val headshots = store.headshots
        phase = PkgPhase.Sending
        progress = 0f
        exportError = null
        exportJob = scope.launch {
            try {
                val file: File = withContext(Dispatchers.IO) {
                    MediaExporter.exportZipPackage(
                        context = context,
                        audition = a,
                        takes = takesForAudition,
                        slate = slate,
                        headshots = headshots,
                        slateAtStart = store.slateAtStart,
                        isCancelled = { !isActive },
                        onProgress = { p: Float -> progress = p },
                    )
                }
                store.setExportedFile(a.id, Uri.fromFile(file).toString())
                haptics.success()
                shareFile(context, file, "OneTake self-tape package — ${a.project} (${a.role})", "application/zip")
                phase = PkgPhase.Review
                confirmSent = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exportError = e.message ?: "Something went wrong creating your package."
                phase = PkgPhase.Failed
            }
        }
    }

    fun saveZipToDevice() {
        val takesForAudition = store.takes.filter { it.auditionId == a.id }
        val slate = store.slate
        val headshots = store.headshots
        phase = PkgPhase.Sending
        progress = 0f
        exportError = null
        exportJob = scope.launch {
            try {
                val file: File = withContext(Dispatchers.IO) {
                    MediaExporter.exportZipPackage(
                        context = context,
                        audition = a,
                        takes = takesForAudition,
                        slate = slate,
                        headshots = headshots,
                        slateAtStart = store.slateAtStart,
                        isCancelled = { !isActive },
                        onProgress = { p: Float -> progress = p },
                    )
                }
                store.setExportedFile(a.id, Uri.fromFile(file).toString())
                haptics.success()
                phase = PkgPhase.Review
                pendingZipToSave = file
                createDocLauncher.launch(file.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exportError = e.message ?: "Something went wrong preparing your package."
                phase = PkgPhase.Failed
            }
        }
    }

    fun exportAndPreview() {
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
                        includeSlate = store.slateAtStart,
                    ) { p -> scope.launch { progress = p } }
                }
                haptics.success()
                phase = PkgPhase.Review
                viewFile(context, file)
            } catch (e: CancellationException) {
                throw e
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
            PkgPhase.Review -> PkgReview(
                a = a,
                onSendZip = { sendZip() },
                onSaveZip = { saveZipToDevice() },
                onExportAndPreview = { exportAndPreview() },
            )
            PkgPhase.Sending -> PkgSendProgress(progress = progress, onCancel = { cancelExport() })
            PkgPhase.Failed -> PkgSendFailed(message = exportError, onBack = { cancelExport() }, onRetry = { sendZip() })
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
private fun PkgReview(
    a: Audition,
    onSendZip: () -> Unit,
    onSaveZip: () -> Unit,
    onExportAndPreview: () -> Unit,
) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val c = OT.colors
    var showDestination by remember { mutableStateOf(false) }
    var previewPiece by remember { mutableStateOf<PkgPiecePreview?>(null) }
    var showExportPrompt by remember { mutableStateOf(false) }

    val cardShape = remember { RoundedCornerShape(24.dp) }
    val zipPkgFileName = remember(a, store.slate.name) { MediaExporter.deliverableZipFileName(a, store.slate) }
    val auditionTakes = store.takes.filter { it.auditionId == a.id }

    val checks by produceState<List<TapeChecker.Check>?>(null, a, auditionTakes, store.headshots, store.slate, store.slateAtStart) {
        value = withContext(Dispatchers.IO) {
            TapeChecker.run(context.applicationContext, a, auditionTakes, store.headshots, store.slate, store.slateAtStart)
        }
    }
    val issuesCount = checks?.count { !it.passed } ?: 0
    val checkPassed = checks != null && issuesCount == 0
    val checkTitle = if (checks == null) "Tape check" else if (checkPassed) "Tape check" else "Tape check ($issuesCount to review)"
    val checkValue = if (checks == null) "Checking…" else if (checkPassed) "Passed" else "Review"
    val checkTint = if (checkPassed) c.success else c.warning
    val checkIcon = if (checkPassed) Icons.Filled.CheckCircle else Icons.Filled.Warning

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
                PkgTapeStrip(a, auditionTakes, onSelectPiece = { previewPiece = it })
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
                PkgRow(
                    icon = Icons.Outlined.Movie,
                    title = zipPkgFileName,
                    detail = "Self-tape package (.zip) · ${a.destination.label}",
                    value = "Manifest",
                    titleWeight = FontWeight.SemiBold,
                    titleSmall = true,
                    showDivider = true,
                    onClick = { previewPiece = PkgPiecePreview.DeliverableDetails },
                )
                PkgRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = a.destination.label,
                    value = sizeLabel,
                    onClick = { showDestination = true },
                    showDivider = true,
                )
                PkgRow(
                    icon = checkIcon,
                    iconTint = checkTint,
                    title = checkTitle,
                    value = checkValue,
                    onClick = { nav.push(Route.TapeCheck(a.id)) },
                    showDivider = true,
                )
                PkgRow(
                    icon = Icons.Filled.GraphicEq,
                    iconTint = c.success,
                    title = "Audio",
                    value = "Verify tracks",
                    onClick = { previewPiece = PkgPiecePreview.AudioInspection },
                    showDivider = true,
                )
                val headshotCount = store.headshots.size
                PkgRow(
                    icon = Icons.Filled.Person,
                    title = "Headshots",
                    detail = if (headshotCount > 0) "$headshotCount angles attached · 4:5 crop" else "No angles captured yet",
                    value = if (headshotCount > 0) "Review" else "Add",
                    onClick = { previewPiece = PkgPiecePreview.Headshots },
                    showDivider = true,
                )
                PkgRow(
                    icon = Icons.Outlined.Info,
                    title = "Spoken slate",
                    detail = store.slate.spoken.ifBlank { "Actor details on tape" },
                    value = if (store.slate.name.isNotBlank()) "Verified" else "Set name",
                    onClick = { previewPiece = PkgPiecePreview.Slate(a.slateVideoUri) },
                    showDivider = true,
                )
                val fullTapeFile = remember(a.id, store.slate.name) {
                    MediaExporter.existingFullTapeFile(context, a, store.slate)
                }
                if (fullTapeFile != null && fullTapeFile.exists()) {
                    PkgRow(
                        icon = Icons.Outlined.PlayArrow,
                        title = "Preview full tape",
                        detail = "Watch stitched deliverable in order",
                        value = "Play",
                        onClick = { viewFile(context, fullTapeFile) },
                        showDivider = false,
                    )
                } else {
                    PkgRow(
                        icon = Icons.Outlined.PlayArrow,
                        title = "Preview full tape",
                        detail = "Stitch and watch all clips before sending",
                        value = "Generate",
                        onClick = { showExportPrompt = true },
                        showDivider = false,
                    )
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
                "Send .zip package",
                onClick = onSendZip,
                icon = Icons.AutoMirrored.Filled.Send,
                iconTrailing = true,
            )
            SecondaryButton(
                "Save .zip to device",
                onClick = onSaveZip,
                icon = Icons.Filled.Upload,
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

    val preview = previewPiece
    if (preview != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val closeSheet: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { previewPiece = null }
        }

        ModalBottomSheet(
            onDismissRequest = { previewPiece = null },
            sheetState = sheetState,
            containerColor = c.surfaceCard,
        ) {
            when (preview) {
                is PkgPiecePreview.Headshots -> PkgHeadshotsSheet(
                    onClose = closeSheet,
                    onRetake = {
                        closeSheet()
                        nav.openCamera(CameraFlow.Headshot)
                    },
                )
                is PkgPiecePreview.Slate -> PkgClipSheet(
                    title = "Slate Preview",
                    videoUri = preview.videoUri,
                    detail = store.slate.spoken.ifBlank { "Spoken slate opening your tape." },
                    onShoot = {
                        closeSheet()
                        nav.openCamera(CameraFlow.Shoot(a.id, -1))
                    },
                    onClose = closeSheet,
                )
                is PkgPiecePreview.FullLength -> PkgClipSheet(
                    title = "Full Length Preview",
                    videoUri = preview.videoUri,
                    detail = "Head-to-toe full length shot requested by casting.",
                    onShoot = {
                        closeSheet()
                        nav.openCamera(CameraFlow.Shoot(a.id, -1))
                    },
                    onClose = closeSheet,
                )
                is PkgPiecePreview.SceneTake -> PkgSceneTakeSheet(
                    sceneTitle = preview.sceneTitle,
                    sceneIndex = preview.sceneIndex,
                    take = preview.take,
                    onChooseTakes = {
                        closeSheet()
                        nav.push(Route.Takes(a.id))
                    },
                    onShoot = {
                        closeSheet()
                        nav.openCamera(CameraFlow.Shoot(a.id, preview.sceneIndex))
                    },
                    onClose = closeSheet,
                )
                is PkgPiecePreview.AudioInspection -> PkgAudioSheet(
                    a = a,
                    takes = auditionTakes,
                    onClose = closeSheet,
                )
                is PkgPiecePreview.DeliverableDetails -> PkgDeliverableDetailsSheet(
                    a = a,
                    zipFileName = zipPkgFileName,
                    takes = auditionTakes,
                    onClose = closeSheet,
                )
            }
        }
    }

    if (showExportPrompt) {
        AlertDialog(
            onDismissRequest = { showExportPrompt = false },
            title = { Text("Generate tape preview?", style = OT.type.headline) },
            text = {
                Text(
                    "OneTake will stitch your recorded slate, full length and circled takes into a single 1080p MP4 file so you can watch your complete tape in order before sending.",
                    style = OT.type.callout,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportPrompt = false
                    onExportAndPreview()
                }) { Text("Generate preview", color = OT.colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPrompt = false }) { Text("Cancel", color = OT.colors.labelSecondary) }
            },
            containerColor = OT.colors.surfaceRaised,
        )
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
            if (onClick != null) {
                Chevron()
            }
        }
        if (showDivider) {
            HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
        }
    }
}

// MARK: - Tape strip

@Composable
private fun PkgTapeStrip(
    a: Audition,
    takes: List<Take>,
    onSelectPiece: (PkgPiecePreview) -> Unit,
) {
    val c = OT.colors
    val soft = c.accent.copy(alpha = 0.4f)

    data class Segment(
        val title: String,
        val detail: String,
        val color: Color,
        val weight: Float,
        val preview: PkgPiecePreview,
    )

    val segments = buildList {
        if (a.pieces.any { it.kind == PieceKind.Headshot }) {
            val done = a.pieces.firstOrNull { it.kind == PieceKind.Headshot }?.isDone == true
            add(Segment("Headshot", if (done) "Captured" else "Not yet", soft, 0.12f, PkgPiecePreview.Headshots))
        }
        if (a.pieces.any { it.kind == PieceKind.Slate }) {
            add(Segment("Slate", if (a.slateVideoUri != null) "Recorded" else "Not yet", soft, 0.12f, PkgPiecePreview.Slate(a.slateVideoUri)))
        }
        if (a.pieces.any { it.kind == PieceKind.FullLength }) {
            add(Segment("Full length", if (a.fullLengthVideoUri != null) "Recorded" else "Not yet", soft, 0.12f, PkgPiecePreview.FullLength(a.fullLengthVideoUri)))
        }
        a.scenes.forEachIndexed { i, scene ->
            val take = takes.firstOrNull { it.sceneIndex == i && it.isCircled } ?: takes.firstOrNull { it.sceneIndex == i }
            val detail = take?.let { "Take ${it.number} · ${formatDuration(it.durationSeconds)}" } ?: "Not shot yet"
            add(Segment(scene.title, detail, c.accent, 0.45f, PkgPiecePreview.SceneTake(scene.title, i, take)))
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
                Box(
                    Modifier
                        .weight(seg.weight)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(seg.color)
                        .clickable(role = Role.Button, onClickLabel = "Preview ${seg.title}") {
                            onSelectPiece(seg.preview)
                        },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { seg ->
                PkgLegend(
                    title = seg.title,
                    detail = seg.detail,
                    color = seg.color,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button, onClickLabel = "Preview ${seg.title}") {
                            onSelectPiece(seg.preview)
                        },
                )
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

// MARK: - Interactive Preview Sheets

@Composable
private fun PkgHeadshotsSheet(
    onClose: () -> Unit,
    onRetake: () -> Unit,
) {
    val store = LocalAppStore.current
    val c = OT.colors
    val angles = listOf("front" to "Front", "left" to "Left angle", "right" to "Right angle")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Attached Headshots", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, "Close", onClick = onClose)
        }
        Text(
            "Industry-standard 4:5 cropped photos attached to your deliverable package.",
            style = OT.type.callout,
            color = c.labelSecondary,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            angles.forEach { (key, label) ->
                val photoUri = store.headshot(key)
                val hasPhoto = photoUri != null
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surfaceCard)
                            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        HeadshotImage(Modifier.fillMaxSize(), name = key, uri = photoUri)
                    }
                    Text(label, style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary, maxLines = 1)
                    Text(if (hasPhoto) "Attached" else "Missing", style = OT.type.caption, color = if (hasPhoto) c.success else c.warning)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SecondaryButton("Retake headshots", onClick = onRetake)
        PrimaryButton("Done", onClick = onClose)
    }
}

@Composable
private fun PkgClipSheet(
    title: String,
    videoUri: String?,
    detail: String,
    onShoot: () -> Unit,
    onClose: () -> Unit,
) {
    val c = OT.colors
    var playing by remember(videoUri) { mutableStateOf(false) }
    val player = remember(videoUri) { TkPlayerState() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = OT.type.title, color = c.labelPrimary, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, "Close", onClick = onClose)
        }
        if (videoUri != null) {
            TkPlayerCard(
                Modifier.fillMaxWidth().height(220.dp),
                state = player,
                fileUri = videoUri,
                isPlaying = playing,
                onTogglePlay = { playing = !playing },
            )
            Text(detail, style = OT.type.callout, color = c.labelSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Record again", onClick = onShoot, modifier = Modifier.weight(1f))
                PrimaryButton("Looks good", onClick = onClose, modifier = Modifier.weight(1f))
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surfaceCard)
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Movie, null, tint = c.labelTertiary, modifier = Modifier.size(36.dp))
                    Text("Not recorded yet", style = OT.type.body, color = c.labelSecondary)
                }
            }
            Text(detail, style = OT.type.callout, color = c.labelSecondary)
            PrimaryButton("Record now", onClick = onShoot)
        }
    }
}

@Composable
private fun PkgSceneTakeSheet(
    sceneTitle: String,
    sceneIndex: Int,
    take: Take?,
    onChooseTakes: () -> Unit,
    onShoot: () -> Unit,
    onClose: () -> Unit,
) {
    val c = OT.colors
    var playing by remember(take?.fileUri) { mutableStateOf(false) }
    val player = remember(take?.fileUri) { TkPlayerState() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sceneTitle, style = OT.type.title, color = c.labelPrimary)
                if (take != null) {
                    Text(
                        "Take ${take.number} · ${formatDuration(take.durationSeconds)}" + if (take.isCircled) " · Circled take" else " · Selected take",
                        style = OT.type.caption,
                        color = c.accent,
                    )
                }
            }
            CircleIconButton(Icons.Filled.Close, "Close", onClick = onClose)
        }
        if (take?.fileUri != null) {
            TkPlayerCard(
                Modifier.fillMaxWidth().height(220.dp),
                state = player,
                fileUri = take.fileUri,
                isPlaying = playing,
                onTogglePlay = { playing = !playing },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Choose different take", onClick = onChooseTakes, modifier = Modifier.weight(1.2f))
                PrimaryButton("Keep this take", onClick = onClose, modifier = Modifier.weight(1f))
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surfaceCard)
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Movie, null, tint = c.labelTertiary, modifier = Modifier.size(36.dp))
                    Text("No take recorded for this scene yet", style = OT.type.body, color = c.labelSecondary)
                }
            }
            PrimaryButton("Shoot scene now", onClick = onShoot)
        }
    }
}

@Composable
private fun PkgAudioSheet(
    a: Audition,
    takes: List<Take>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val c = OT.colors

    data class AudioPiece(val label: String, val uri: String?, val sound: TapeAnalysis.Sound?)

    val piecesState by produceState<List<AudioPiece>?>(null, a, takes) {
        value = withContext(Dispatchers.IO) {
            val list = mutableListOf<AudioPiece>()
            if (a.slateVideoUri != null) {
                val snd = TapeAnalysis.sound(context.applicationContext, a.slateVideoUri)
                list.add(AudioPiece("Slate", a.slateVideoUri, snd))
            }
            if (a.fullLengthVideoUri != null) {
                val snd = TapeAnalysis.sound(context.applicationContext, a.fullLengthVideoUri)
                list.add(AudioPiece("Full length", a.fullLengthVideoUri, snd))
            }
            a.scenes.forEachIndexed { i, scene ->
                val chosen = takes.firstOrNull { it.sceneIndex == i && it.isCircled } ?: takes.firstOrNull { it.sceneIndex == i }
                if (chosen?.fileUri != null) {
                    val snd = TapeAnalysis.sound(context.applicationContext, chosen.fileUri)
                    list.add(AudioPiece("${scene.title} · Take ${chosen.number}", chosen.fileUri, snd))
                }
            }
            list
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Audio Verification", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, "Close", onClick = onClose)
        }
        Text(
            "Sound tracks, microphone levels, and distortion analysis across your recorded pieces.",
            style = OT.type.callout,
            color = c.labelSecondary,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surfaceCard)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, RoundedCornerShape(18.dp))
                .padding(vertical = 6.dp),
        ) {
            val pieces = piecesState
            if (pieces == null) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.GraphicEq, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Text("Analyzing audio tracks…", style = OT.type.footnote, color = c.labelSecondary)
                }
            } else if (pieces.isEmpty()) {
                Text("No recordings with audio tracks found yet.", style = OT.type.footnote, color = c.labelTertiary, modifier = Modifier.padding(16.dp))
            } else {
                for ((idx, p) in pieces.withIndex()) {
                    val isDistorted = p.sound?.clippedAtMs != null
                    val isQuiet = p.sound != null && p.sound.loudestDb < -32
                    val isSilent = p.sound != null && p.sound.loudestDb < -50
                    val hasNoSound = p.sound == null
                    val statusText = when {
                        hasNoSound -> "No audio track detected"
                        isSilent -> "Silent track (no audio detected)"
                        isDistorted -> "Distortion detected"
                        isQuiet -> "Quiet voice (-${(-p.sound.loudestDb).toInt()} dBFS)"
                        else -> "Clear level (-${(-p.sound.loudestDb).toInt()} dBFS)"
                    }
                    val statusTint = when {
                        hasNoSound || isSilent -> c.warning
                        isDistorted || isQuiet -> c.warning
                        else -> c.success
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.GraphicEq, null, tint = statusTint, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.label, style = OT.type.body, color = c.labelPrimary)
                            Text(statusText, style = OT.type.caption, color = statusTint)
                        }
                    }
                    if (idx < pieces.lastIndex) HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))
                }
            }
        }
        PrimaryButton("Done", onClick = onClose)
    }
}

@Composable
private fun PkgDeliverableDetailsSheet(
    a: Audition,
    zipFileName: String,
    takes: List<Take>,
    onClose: () -> Unit,
) {
    val store = LocalAppStore.current
    val context = LocalContext.current
    val c = OT.colors
    val cardShape = RoundedCornerShape(18.dp)
    val totalDuration = totalDurationLabel(a, takes)

    val manifestEntries = remember(a, takes, store.headshots, store.slate) {
        MediaExporter.inspectPackageManifest(
            context = context,
            audition = a,
            takes = takes,
            slate = store.slate,
            headshots = store.headshots,
        )
    }

    val totalBytes = manifestEntries.filter { it.isReady }.sumOf { it.sizeBytes }
    val packageSizeLabel = if (totalBytes > 0) "${(totalBytes / 1_048_576L).coerceAtLeast(1)} MB uncompressed" else "Pending recordings"

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Deliverable Package", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, "Close", onClick = onClose)
        }
        Text(
            "All audition media files packaged into a single industry-standard .zip archive matching casting specifications.",
            style = OT.type.callout,
            color = c.labelSecondary,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.surfaceCard)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ARCHIVE FILE NAME (.ZIP)", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                Text(zipFileName, style = OT.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
            }
            HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("DESTINATION SPECIFICATION", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                Text("${a.destination.label} · ${a.destination.spec}", style = OT.type.footnote, color = c.labelSecondary)
            }
            HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("PACKAGE SIZE", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    Text(packageSizeLabel, style = OT.type.footnote, color = c.labelPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("DURATION", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    Text(totalDuration, style = OT.type.footnote, color = c.labelPrimary)
                }
            }
        }

        Text("MANIFEST ENTRIES", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.labelSecondary)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.surfaceCard)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape)
                .padding(vertical = 6.dp),
        ) {
            for ((idx, item) in manifestEntries.withIndex()) {
                val sizeStr = if (item.isReady) "${(item.sizeBytes / 1024L)} KB" else "Not recorded yet"
                val tint = if (item.isReady) c.success else c.labelTertiary
                val icon = if (item.isReady) Icons.Filled.CheckCircle else Icons.Outlined.Info
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(item.name, style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.kind} · $sizeStr", style = OT.type.caption, color = if (item.isReady) c.labelSecondary else c.warning)
                    }
                }
                if (idx < manifestEntries.lastIndex) {
                    HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(start = 44.dp))
                }
            }
        }

        PrimaryButton("Done", onClick = onClose)
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
                message ?: "Something went wrong composing your tape. Your recorded takes are safe — this only affects the export file.",
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
