package com.onetake.app.ui.auditions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Route
import com.onetake.app.data.ScriptParser
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.ScriptScene
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.system.clipboardText
import com.onetake.app.ui.system.fileProviderUri
import com.onetake.app.ui.theme.OT
import java.io.File
import java.time.LocalDateTime

enum class AddAuditionSource {
    Paste,
    Scan,
    Photo,
    PdfOrFile,
    Share,
    Type,
}

internal fun addReviewTitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "Audition details"
    else -> "Here's what they want"
}

internal fun addReviewSubtitle(source: AddAuditionSource, asksCount: Int): String {
    val count = asksCount + 1
    val items = if (count == 1) "1 item" else "$count items"
    return when (source) {
        AddAuditionSource.Type -> "Set up your role, deadline, and deliverables, then save."
        AddAuditionSource.Scan -> "$items found from your scan. Tap any line to edit, then save."
        AddAuditionSource.PdfOrFile -> "$items found in the PDF. Tap any line to edit, then save."
        AddAuditionSource.Photo -> "$items found in the image. Tap any line to edit, then save."
        AddAuditionSource.Paste, AddAuditionSource.Share -> "$items found in the notice. Tap any line to edit, then save."
    }
}

internal fun addAsksSectionTitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "What to prepare"
    else -> "They asked for"
}

internal fun addEditHeaderTitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "What to prepare"
    else -> "What they want"
}

internal fun addEditSubtitle(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "Customize each deliverable and step. Only what's listed here becomes a step in your audition."
    AddAuditionSource.Scan -> "OneTake read this from your scan. It can miss things — add, remove or change any step. Only what's here becomes a step."
    AddAuditionSource.PdfOrFile -> "OneTake read this from the PDF. It can miss things — add, remove or change any step. Only what's here becomes a step."
    AddAuditionSource.Photo -> "OneTake read this from the image. It can miss things — add, remove or change any step. Only what's here becomes a step."
    AddAuditionSource.Paste, AddAuditionSource.Share -> "OneTake read this from the notice. It can miss things — add, remove or change any step. Only what's here becomes a step."
}

internal fun addEditAddButtonLabel(source: AddAuditionSource): String = when (source) {
    AddAuditionSource.Type -> "Add a deliverable"
    else -> "Add something they asked for"
}

/** Rule-based and ScriptParser-backed casting notice & sides parser. */
internal data class AddDraft(
    val project: String = "",
    val role: String = "",
    val projectType: String = "Feature film",
    val roleSize: String = "Lead",
    val castingOffice: String = "",
    val notes: String = "",
    val due: LocalDateTime? = null,
    val scenes: Int = 1,
    val asks: List<String> = emptyList(),
    val destination: Destination = Destination.ActorsAccess,
    val rawScriptText: String = "",
    val source: AddAuditionSource = AddAuditionSource.Paste,
    val kind: DocumentKind = DocumentKind.CastingNotice,
    val detectedCharacters: List<String> = emptyList(),
    val allAvailableRoles: List<String> = emptyList(),
) {
    fun makeAudition(hasHeadshot: Boolean = false, hasProfiles: Boolean = false): Audition {
        val effectiveRole = role
            .ifBlank { if (detectedCharacters.size == 1) detectedCharacters.first() else "" }
            .ifBlank { "Role" }

        val finalScenes: List<ScriptScene> = if (scenes <= 0) {
            emptyList()
        } else {
            val extractedChars = if (rawScriptText.isNotBlank()) ScriptParser.extractCharacters(rawScriptText) else emptyList()
            val canParseScript = rawScriptText.isNotBlank() && (
                extractedChars.isNotEmpty() ||
                    kind == DocumentKind.SidesOnly ||
                    source == AddAuditionSource.Type
                )
            val parsedScenes = if (!canParseScript) {
                emptyList()
            } else {
                ScriptParser.parseScenes(rawScriptText, myCharacter = effectiveRole)
                    .filter { it.lines.isNotEmpty() }
            }
            parsedScenes.ifEmpty {
                (1..scenes).map { ScriptScene.placeholder(it, effectiveRole) }
            }
        }

        val needsProfiles = "Both profiles" in asks
        val pieces = mutableListOf(
            Piece(
                kind = PieceKind.Headshot,
                title = "Headshot",
                detail = if (needsProfiles) "Front + profiles" else "Front",
                isDone = if (needsProfiles) (hasHeadshot && hasProfiles) else hasHeadshot,
            ),
            Piece(kind = PieceKind.Slate, title = "Slate", detail = "Name, height, city", isDone = false),
        )
        if ("Full length" in asks) {
            pieces.add(Piece(kind = PieceKind.FullLength, title = "Full length", detail = "Head to feet", isDone = false))
        }
        for (scene in finalScenes) {
            pieces.add(Piece(kind = PieceKind.Scene, title = scene.title, detail = "", isDone = false))
        }

        return Audition(
            project = project.ifBlank { "Untitled audition" },
            projectType = projectType.ifBlank { "Feature film" },
            role = effectiveRole,
            roleSize = roleSize.ifBlank { "Lead" },
            castingOffice = castingOffice.trim(),
            notes = notes.trim(),
            due = due ?: LocalDateTime.now().plusDays(3),
            asks = asks,
            pieces = pieces,
            scenes = finalScenes,
            destination = destination,
            status = AuditionStatus.Due,
        )
    }
}

/** Notice → draft. The rules live in [NoticeParser] (pure, unit-tested). */
internal fun addParseNotice(text: String): AddDraft {
    val f = NoticeParser.parse(text)
    val autoRole = f.role.ifEmpty {
        if (f.detectedCharacters.size == 1) f.detectedCharacters.first() else ""
    }
    return AddDraft(
        project = f.project,
        role = autoRole,
        projectType = f.projectType.ifBlank { "Feature film" },
        roleSize = f.roleSize.ifBlank { "Lead" },
        castingOffice = f.castingOffice,
        notes = f.notes,
        due = f.due,
        scenes = maxOf(1, f.sceneCount),
        asks = f.asks,
        destination = f.destination,
        rawScriptText = f.scriptText,
        kind = f.kind,
        detectedCharacters = f.detectedCharacters,
        allAvailableRoles = f.allAvailableRoles,
    )
}

internal fun addDraftFromNoticeFields(f: NoticeFields, source: AddAuditionSource = AddAuditionSource.Paste): AddDraft {
    val autoRole = f.role.ifEmpty {
        if (f.allAvailableRoles.size == 1) f.allAvailableRoles.first()
        else if (f.detectedCharacters.size == 1) f.detectedCharacters.first() else ""
    }
    return AddDraft(
        project = f.project,
        role = autoRole,
        projectType = f.projectType.ifBlank { "Feature film" },
        roleSize = f.roleSize.ifBlank { "Lead" },
        castingOffice = f.castingOffice,
        notes = f.notes,
        due = f.due,
        scenes = maxOf(1, f.sceneCount),
        asks = f.asks,
        destination = f.destination,
        rawScriptText = f.scriptText,
        source = source,
        kind = f.kind,
        detectedCharacters = f.detectedCharacters,
        allAvailableRoles = f.allAvailableRoles,
    )
}

/**
 * 06 Add an audition capture sheet.
 * Presents only capture source options (Paste, Photo, Scan, File, Type).
 * Once a source is selected, the bottom sheet dismisses immediately and
 * navigates to [Route.NewAudition] full-screen page so vertical scrolling
 * cannot accidentally dismiss or discard entered audition progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAuditionSheet(onDismiss: () -> Unit) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingScanFile by remember { mutableStateOf<File?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            store.startNewAudition(
                draft = AddDraft(source = AddAuditionSource.Photo),
                notice = "",
                uris = uris,
            )
            onDismiss()
            nav.push(Route.NewAudition)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = pendingScanFile
        pendingScanFile = null
        if (captured && file != null && file.exists()) {
            val scanUri = fileProviderUri(context, file)
            store.startNewAudition(
                draft = AddDraft(source = AddAuditionSource.Scan),
                notice = "",
                uris = listOf(scanUri),
            )
            onDismiss()
            nav.push(Route.NewAudition)
        } else {
            runCatching { file?.delete() }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            store.startNewAudition(
                draft = AddDraft(source = AddAuditionSource.PdfOrFile),
                notice = "",
                uris = listOf(uri),
            )
            onDismiss()
            nav.push(Route.NewAudition)
        }
    }

    val pickPhoto: () -> Unit = {
        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val scan: () -> Unit = {
        try {
            val tempFile = File(context.cacheDir, "ocr-scan-${System.currentTimeMillis()}.jpg")
            pendingScanFile = tempFile
            scanLauncher.launch(fileProviderUri(context, tempFile))
        } catch (e: Exception) {
            pendingScanFile = null
            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OT.colors.bgCanvas,
    ) {
        AddCaptureStep(
            onClose = onDismiss,
            onPaste = {
                val text = runCatching { clipboardText(context) }.getOrNull().orEmpty()
                val draft = AddDraft(source = AddAuditionSource.Paste)
                store.startNewAudition(draft = draft, notice = text)
                onDismiss()
                nav.push(Route.NewAudition)
            },
            onPhoto = pickPhoto,
            onScan = scan,
            onFile = { fileLauncher.launch(arrayOf("application/pdf", "text/plain", "application/rtf", "text/rtf", "image/*")) },
            onType = {
                store.startNewAudition(draft = AddDraft(source = AddAuditionSource.Type), notice = "")
                onDismiss()
                nav.push(Route.NewAudition)
            },
        )
    }
}

@Composable
internal fun ModelDownloadStatusPill(
    status: ModelStatus,
    modifier: Modifier = Modifier,
) {
    val downloading = status as? ModelStatus.Downloading ?: return
    val c = OT.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.accentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            progress = { (downloading.progressPercent / 100f).coerceIn(0f, 1f) },
            strokeWidth = 2.5.dp,
            color = c.accent,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "Downloading On-Device AI Engine (${downloading.progressPercent}%) — using fast parser for now",
                style = OT.type.headline.copy(fontSize = 13.sp),
                color = c.labelPrimary,
            )
            Text(
                text = "${downloading.downloadedMb} MB / ${downloading.totalMb} MB",
                style = OT.type.caption,
                color = c.labelSecondary,
            )
        }
    }
}

// MARK: - 06 capture-first sheet

@Composable
private fun AddCaptureStep(
    onClose: () -> Unit,
    onPaste: () -> Unit,
    onPhoto: () -> Unit,
    onScan: () -> Unit,
    onFile: () -> Unit,
    onType: () -> Unit,
) {
    val c = OT.colors
    val context = LocalContext.current
    val downloadStatus by ModelDownloadManager.getInstance(context).status.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OT.dims.gutter)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Add an audition",
                style = OT.type.title,
                color = c.labelPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            CircleIconButton(Icons.Filled.Close, "Close", onClick = onClose)
        }

        if (downloadStatus is ModelStatus.Downloading) {
            ModelDownloadStatusPill(status = downloadStatus)
        }

        // The clipboard is only read when Paste is tapped.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.accentSoft)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.ContentPaste, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
                Column {
                    Text("Copied the notice?", style = OT.type.headline, color = c.labelPrimary)
                    Text("Paste the email or message — OneTake reads it here", style = OT.type.footnote, color = c.labelTertiary)
                }
            }
            PrimaryButton("Paste and set up", onClick = onPaste)
        }

        SectionLabel("Or bring it in")
        Row(Modifier.fillMaxWidth()) {
            AddWay(Icons.Outlined.Image, "Photo", onPhoto, Modifier.weight(1f))
            AddWay(Icons.Outlined.DocumentScanner, "Scan", onScan, Modifier.weight(1f))
            AddWay(Icons.Outlined.Folder, "File", onFile, Modifier.weight(1f))
            AddWay(Icons.Outlined.Keyboard, "Type it", onType, Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Share, null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text("Faster: in Mail or WhatsApp, Share → OneTake", style = OT.type.footnote, color = c.accent)
            }
        }
    }
}

@Composable
private fun AddWay(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OT.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(c.fillField), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.labelPrimary, modifier = Modifier.size(24.dp))
        }
        Text(title, style = OT.type.footnote, color = c.labelSecondary)
    }
}
