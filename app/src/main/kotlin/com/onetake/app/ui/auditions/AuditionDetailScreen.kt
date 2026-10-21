package com.onetake.app.ui.auditions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Navigator
import com.onetake.app.Route
import com.onetake.app.models.Audition
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.models.SampleData
import com.onetake.app.models.ScriptScene
import com.onetake.app.models.formatDuration
import com.onetake.app.models.otDayMonth
import com.onetake.app.models.otDue
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.Chevron
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.HeadshotImage
import com.onetake.app.ui.components.LargeTitle
import com.onetake.app.ui.components.OTCard
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.OTSwitch
import com.onetake.app.ui.components.PackageStepsBar
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.PushedScreen
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.SheetHeader
import com.onetake.app.ui.theme.OT
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 07 Audition detail (and 7.0 "just added · 0 of 4" when nothing is done yet). */
@Composable
fun AuditionDetailScreen(auditionId: String) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val a = store.audition(auditionId)

    if (a == null) {
        PushedScreen {
            Column(
                Modifier.fillMaxWidth().padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Delete, null, tint = OT.colors.labelTertiary, modifier = Modifier.size(40.dp))
                Text("Audition removed", style = OT.type.headline, color = OT.colors.labelPrimary)
            }
        }
        return
    }

    var showMenu by remember { mutableStateOf(false) }
    var showPackage by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showCallback by remember { mutableStateOf(false) }
    val c = OT.colors

    PushedScreen(
        trailing = {
            Box {
                CircleIconButton(Icons.Filled.MoreHoriz, "More actions", onClick = { showMenu = true })
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .widthIn(min = 250.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xF01A1A1E))
                        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, RoundedCornerShape(24.dp)),
                ) {
                    DetailMenuItem("Revised sides", "New pages from casting", Icons.Outlined.Description) { showMenu = false }
                    DetailMenuItem("Edit details", "Due date, role, what they want", Icons.Outlined.Edit) { showMenu = false }
                    DetailMenuItem("Start a callback", "Same role, next round", Icons.AutoMirrored.Filled.ArrowForward) {
                        showMenu = false
                        showCallback = true
                    }
                    OTDivider()
                    DetailMenuItem("Delete audition", null, Icons.Outlined.Delete, destructive = true) {
                        showMenu = false
                        showDelete = true
                    }
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            DetailHeader(a)
            DetailPackageSummary(a, onClick = { showPackage = true })
            DetailUpNext(a, nav)
            detailThenLine(a)?.let { then ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { showPackage = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Then", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(then, style = OT.type.footnote, color = c.labelSecondary, modifier = Modifier.weight(1f))
                    Chevron()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPackage) {
        DetailPackageSheet(a, onDismiss = { showPackage = false }, onOpen = { piece ->
            showPackage = false
            detailOpenPiece(nav, a, piece)
        })
    }
    if (showCallback) {
        DetailCallbackSheet(a, onDismiss = { showCallback = false })
    }
    if (showDelete) {
        DetailDeleteSheet(
            project = a.project,
            onDelete = {
                showDelete = false
                store.delete(a.id)
                nav.back()
            },
            onDismiss = { showDelete = false },
        )
    }
}

// MARK: - Header

@Composable
private fun DetailHeader(a: Audition) {
    val c = OT.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("${a.projectType} · reading for ${a.role}")
        Text(
            a.project,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            color = c.labelPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${a.hoursLeft}", style = OT.type.number, color = c.due, modifier = Modifier.alignByBaseline())
            Text("h left", style = OT.type.headline, color = c.due, modifier = Modifier.alignByBaseline())
            Text("·  Due ${a.due.otDue}", style = OT.type.callout, color = c.labelSecondary, modifier = Modifier.alignByBaseline())
        }
        if (a.asks.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                a.asks.take(3).forEach { ask ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailAskIcon(ask, c.labelSecondary, 15.dp)
                        Text(ask, style = OT.type.footnote, color = c.labelSecondary, maxLines = 1)
                    }
                }
                if (a.asks.size > 3) {
                    Text("+${a.asks.size - 3}", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                }
            }
        }
    }
}

@Composable
private fun DetailAskIcon(ask: String, tint: Color, size: Dp) {
    val icon: ImageVector = when (ask) {
        "Slate first" -> Icons.Outlined.Badge
        "Landscape" -> Icons.Outlined.Smartphone
        "Full length" -> Icons.Outlined.AccessibilityNew
        "Both profiles" -> Icons.Outlined.Portrait
        "Under 3 min" -> Icons.Outlined.Timer
        else -> Icons.AutoMirrored.Filled.Send
    }
    Icon(
        icon,
        null,
        tint = tint,
        modifier = Modifier.size(size).then(if (ask == "Landscape") Modifier.rotate(90f) else Modifier),
    )
}

// MARK: - Package summary

@Composable
private fun DetailPackageSummary(a: Audition, onClick: () -> Unit) {
    val c = OT.colors
    val containerShape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(c.surfaceCard)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, containerShape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Package")
            Spacer(Modifier.weight(1f))
            Text("All pieces", style = OT.type.footnote, color = c.accent)
            Chevron(Modifier.size(16.dp), tint = c.accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${a.readyCount} of ${a.totalCount} ready", style = OT.type.headline, color = c.labelPrimary, modifier = Modifier.alignByBaseline())
            a.nextPiece?.let { next ->
                Text(
                    if (a.readyCount == 0) "· ${next.title} first" else "· ${next.title} next",
                    style = OT.type.callout,
                    color = c.labelSecondary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        PackageStepsBar(a.pieces)
    }
}

// MARK: - Up next panel

@Composable
private fun DetailUpNext(a: Audition, nav: Navigator) {
    val store = LocalAppStore.current
    val c = OT.colors
    val next = a.nextPiece
    val cardShape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(c.surfaceCard)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            when {
                next?.kind == PieceKind.Headshot -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Up next · piece ${a.pieces.indexOfFirst { it.id == next?.id } + 1} of ${a.totalCount}", Modifier.weight(1f))
                        Text("≈ 2 min", style = OT.type.footnote, color = c.labelTertiary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailCardTitle("Your headshot")
                        Text("FRONT · LEFT · RIGHT", style = OT.type.caption, color = c.labelTertiary)
                    }
                    DetailQuoteLine("Casting sees this first. A window and a plain wall is all you need.")
                    PrimaryButton("Take your headshot", onClick = { nav.push(Route.Headshots) })
                }
                next?.kind == PieceKind.Slate || next?.kind == PieceKind.FullLength -> {
                    SectionLabel("Up next · ${next?.title.orEmpty()}")
                    DetailCardTitle(if (next?.kind == PieceKind.Slate) "Record the slate" else "Full-length shot")
                    DetailQuoteLine(store.slate.spoken)
                    PrimaryButton("Set up the camera", onClick = { nav.push(Route.ShootMode(a.id)) })
                }
                next != null && a.scenes.isNotEmpty() && !next.title.startsWith("Watch") -> {
                    val sceneIndex = detailSceneIndex(a, next)
                    val scene = a.scenes[sceneIndex]
                    val mine = scene.lines.count { it.isMine }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Up next · scene ${scene.number} of ${a.scenes.size}", Modifier.weight(1f))
                        Text("≈ ${formatDuration(scene.estimatedSeconds)} · $mine lines", style = OT.type.footnote, color = c.labelTertiary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailCardTitle(detailSceneName(scene, next))
                        Text(scene.heading, style = OT.type.caption, color = c.labelTertiary)
                    }
                    scene.lines.firstOrNull { it.isMine }?.let { DetailQuoteLine(it.text) }
                    PrimaryButton("Prepare scene ${scene.number}", onClick = { nav.push(Route.Prepare(a.id, sceneIndex)) })
                }
                else -> {
                    SectionLabel("Everything's in")
                    DetailCardTitle("Watch it once, then send")
                    PrimaryButton("Review the package", onClick = { nav.push(Route.Package(a.id)) })
                }
            }
        }
}

@Composable
private fun DetailCardTitle(text: String) {
    Text(text, style = OT.type.title, color = OT.colors.labelPrimary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun DetailQuoteLine(text: String) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(OT.colors.accent)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = OT.type.headline, color = OT.colors.labelPrimary, modifier = Modifier.weight(1f))
    }
}

/** Index into `a.scenes` for a scene piece (n-th scene piece → n-th scene), clamped. */
private fun detailSceneIndex(a: Audition, piece: Piece): Int {
    if (a.scenes.isEmpty()) return 0
    val n = a.pieces.filter { it.kind == PieceKind.Scene }.indexOfFirst { it.id == piece.id }
    return n.coerceIn(0, a.scenes.size - 1)
}

/** "Apartment Confrontation" — from the piece detail when it has one, else the known sample scene names. */
private fun detailSceneName(scene: ScriptScene, piece: Piece? = null): String {
    val fromPiece = piece?.detail?.substringBefore(" · ")?.trim().orEmpty()
    if (fromPiece.isNotEmpty() && !fromPiece.startsWith("not ") && !fromPiece.startsWith("any ")) return fromPiece
    return when (scene.heading) {
        SampleData.apartment.heading -> "Apartment Confrontation"
        SampleData.goodbye.heading -> "Goodbye"
        else -> scene.title
    }
}

private fun detailThenLine(a: Audition): String? {
    val remaining = a.pieces.filter { !it.isDone }.drop(1).map { it.title }
    if (remaining.isEmpty()) return null
    return remaining.joinToString(" · ") + if (a.readyCount == 0) " — any order after that" else " — any order is fine"
}

private fun detailOpenPiece(nav: Navigator, a: Audition, piece: Piece) {
    when (piece.kind) {
        PieceKind.Headshot -> nav.push(Route.Headshots)
        PieceKind.Slate, PieceKind.FullLength -> nav.push(Route.ShootMode(a.id))
        PieceKind.Scene -> nav.push(Route.Prepare(a.id, detailSceneIndex(a, piece)))
    }
}

// MARK: - 07a ··· menu

@Composable
private fun DetailMenuItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val c = OT.colors
    val fg = if (destructive) c.danger else c.labelPrimary
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(title, style = OT.type.body, color = fg)
                if (subtitle != null) Text(subtitle, style = OT.type.footnote, color = c.labelTertiary)
            }
        },
        onClick = onClick,
        trailingIcon = { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)) },
    )
}

// MARK: - 07b Delete confirmation (action sheet)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailDeleteSheet(project: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val c = OT.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(0.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceCard)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Delete “$project”?", style = OT.type.headline, color = c.labelPrimary, textAlign = TextAlign.Center)
                    Text(
                        "Its slate, reader track and any takes are removed from this phone. Your headshots stay in Profile.",
                        style = OT.type.footnote,
                        color = c.labelTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
                OTDivider()
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { Text("Delete", style = OT.type.body.copy(fontWeight = FontWeight.Medium), color = c.danger) }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceCard)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", style = OT.type.headline, color = c.accent) }
        }
    }
}

// MARK: - 7 · L2 Package sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPackageSheet(a: Audition, onDismiss: () -> Unit, onOpen: (Piece) -> Unit) {
    val c = OT.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surfaceCard,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Package", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.semantics { heading() })
                    Text("In tape order · ${a.readyCount} of ${a.totalCount} ready", style = OT.type.footnote, color = c.labelTertiary)
                }
                CircleIconButton(Icons.Filled.Close, "Close", onClick = onDismiss)
            }
            Column {
                val nextId = a.nextPiece?.id
                a.pieces.forEachIndexed { i, piece ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onOpen(piece) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailPieceBadge(a, piece, isNext = piece.id == nextId)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${i + 1}  ${piece.title}", style = OT.type.headline, color = c.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                piece.detail.ifEmpty { "not shot yet" },
                                style = OT.type.footnote,
                                color = c.labelTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (piece.isDone) {
                            Icon(Icons.Filled.CheckCircle, "Ready", tint = c.accent, modifier = Modifier.size(22.dp))
                        } else if (piece.id == nextId) {
                            Text("Next", style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                        }
                        Chevron()
                    }
                    if (i < a.pieces.lastIndex) OTDivider(startIndent = 56.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Info, null, tint = c.labelTertiary, modifier = Modifier.size(16.dp))
                Text("Each piece opens its own screen.", style = OT.type.footnote, color = c.labelTertiary)
            }
        }
    }
}

@Composable
private fun DetailPieceBadge(a: Audition, piece: Piece, isNext: Boolean) {
    val c = OT.colors
    val mod = Modifier.size(44.dp)
    when {
        piece.isDone && piece.kind == PieceKind.Headshot ->
            HeadshotImage(mod.clip(RoundedCornerShape(10.dp)), name = a.headshotAsset)
        piece.isDone -> Box(mod.clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = c.labelOnAccent, modifier = Modifier.size(22.dp))
        }
        isNext -> Box(
            mod.clip(CircleShape).background(c.accent.copy(alpha = 0.35f)).border(1.5.dp, c.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(piece.kind.icon, null, tint = c.accent, modifier = Modifier.size(22.dp)) }
        else -> Box(
            mod.clip(CircleShape).border(1.dp, c.separator, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(piece.kind.icon, null, tint = c.labelTertiary, modifier = Modifier.size(22.dp)) }
    }
}

// MARK: - 7c Start a callback

private val detailChipDate = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailCallbackSheet(a: Audition, onDismiss: () -> Unit) {
    val c = OT.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }
    val suggested = remember { today.plusDays(5) }
    var choice by remember { mutableIntStateOf(1) } // 0 tomorrow · 1 suggested · 2 other
    var otherDate by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var selectedScenes by remember { mutableStateOf(a.scenes.map { it.id }.toSet()) }
    var notes by remember { mutableStateOf("") }
    var bringReader by remember { mutableStateOf(true) }
    val readerName = remember(a.id) {
        a.scenes.flatMap { it.lines }.firstOrNull { !it.isMine }?.character
            ?.lowercase()?.replaceFirstChar { it.titlecase(Locale.US) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.bgCanvas,
    ) {
        Column(Modifier.fillMaxWidth()) {
            SheetHeader("Callback", leading = "Cancel", onLeading = onDismiss)
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OT.dims.gutter),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LargeTitle("Start a callback", subtitle = "Same role, same reader. Your first tape stays exactly as you sent it.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.project, style = OT.type.headline, color = c.labelPrimary)
                        Text(
                            "${a.role} · first round" + (a.sentDate?.let { " sent ${it.otDayMonth}" } ?: ""),
                            style = OT.type.footnote,
                            color = c.labelTertiary,
                        )
                    }
                    Text(
                        "Round 2",
                        style = OT.type.footnote.copy(fontWeight = FontWeight.Medium),
                        color = c.accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.accentSoft).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                SectionLabel("When is it due?", Modifier.padding(top = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailChip("Tomorrow", choice == 0) { choice = 0 }
                    DetailChip(suggested.format(detailChipDate), choice == 1) { choice = 1 }
                    DetailChip(otherDate?.format(detailChipDate) ?: "Other date", choice == 2) { showPicker = true }
                }
                Row {
                    Text("By 10:00 AM · ", style = OT.type.footnote, color = c.due)
                    Text("change time", style = OT.type.footnote, color = c.accent)
                }
                OTDivider()

                SectionLabel("Scenes to tape")
                a.scenes.forEach { scene ->
                    val on = scene.id in selectedScenes
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { selectedScenes = if (on) selectedScenes - scene.id else selectedScenes + scene.id },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (on) {
                            Icon(Icons.Filled.CheckCircle, "Selected", tint = c.accent, modifier = Modifier.size(24.dp))
                        } else {
                            Box(Modifier.size(24.dp).padding(2.dp).border(1.5.dp, c.labelTertiary, CircleShape))
                        }
                        Column {
                            Text("${scene.number} · ${detailSceneName(scene)}", style = OT.type.body, color = c.labelPrimary)
                            Text("Same pages · ≈ ${formatDuration(scene.estimatedSeconds)}", style = OT.type.footnote, color = c.labelTertiary)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(24.dp).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    }
                    Text("New pages for the callback", style = OT.type.body, color = c.accent)
                }
                OTDivider()

                SectionLabel("Notes from casting")
                BasicTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    textStyle = OT.type.body.copy(color = c.labelPrimary),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.fillField)
                                .padding(14.dp),
                        ) {
                            if (notes.isEmpty()) {
                                Text("e.g. “Less angry, more hurt. Slower on the last line.”", style = OT.type.body, color = c.labelTertiary)
                            }
                            inner()
                        }
                    },
                )
                Text("Shown on Prepare, so you see the adjustment before every take.", style = OT.type.footnote, color = c.labelTertiary)
                OTDivider()

                Row(
                    Modifier.fillMaxWidth().clickable { bringReader = !bringReader }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Bring over")
                        Text(if (readerName != null) "Reader track · $readerName" else "Reader track", style = OT.type.body, color = c.labelPrimary)
                    }
                    OTSwitch(bringReader, { bringReader = it })
                }
                Spacer(Modifier.height(4.dp))
            }
            PrimaryButton(
                "Create callback",
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = OT.dims.gutter, vertical = 8.dp),
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (otherDate ?: suggested).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        otherDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        choice = 2
                    }
                    showPicker = false
                }) { Text("Done", color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel", color = c.labelSecondary) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun DetailChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OT.colors
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else c.surfaceCard)
            .then(if (selected) Modifier else Modifier.border(0.5.dp, c.separator, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) c.labelOnAccent else c.labelPrimary,
            maxLines = 1,
        )
    }
}
