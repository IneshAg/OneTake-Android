package com.onetake.app.ui.auditions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onetake.app.CameraFlow
import com.onetake.app.LocalAppStore
import com.onetake.app.LocalNavigator
import com.onetake.app.Navigator
import com.onetake.app.Route
import com.onetake.app.TabBarClearance
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.PieceKind
import com.onetake.app.models.otDayMonth
import com.onetake.app.models.otDue
import com.onetake.app.models.otWeekdayCaps
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.HeadshotImage
import com.onetake.app.ui.components.LargeTitle
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PrimaryButton
import com.onetake.app.ui.components.ReadinessDashes
import com.onetake.app.ui.components.ScheduleRow
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.otCard
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.theme.OT
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// MARK: - 05 Auditions

private enum class AuditionsFilter(val label: String) {
    Active("Active"),
    DueThisWeek("Due this week"),
    Sent("Sent"),
    All("All"),
}

/** 05 Auditions — single column library with an "Active ▾" filter and swipe actions. */
@Composable
fun AuditionsScreen() {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val c = OT.colors
    var filter by rememberSaveable { mutableStateOf(AuditionsFilter.Active) }
    var pendingDelete by remember { mutableStateOf<Audition?>(null) }
    var openRowId by remember { mutableStateOf<String?>(null) }

    val visible: List<Audition> = when (filter) {
        AuditionsFilter.Active -> store.active + store.sent
        AuditionsFilter.DueThisWeek -> store.active.filter { it.due.isBefore(LocalDateTime.now().plusDays(7)) }
        AuditionsFilter.Sent -> store.sent
        AuditionsFilter.All -> store.auditions
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .statusBarsPadding(),
    ) {
        AuditionsHeader(
            filter = filter,
            onFilter = { filter = it },
            onAdd = { nav.showAddAudition() },
        )
        if (store.auditions.isEmpty()) {
            AuditionsEmptyState(onAdd = { nav.showAddAudition() })
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = OT.dims.gutter,
                    end = OT.dims.gutter,
                    top = 6.dp,
                    bottom = TabBarClearance + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.id }) { audition ->
                    AuditionsSwipeRow(
                        audition = audition,
                        isOpen = openRowId == audition.id,
                        onOpenChange = { open -> openRowId = if (open) audition.id else if (openRowId == audition.id) null else openRowId },
                        onClick = { nav.push(Route.AuditionDetail(audition.id)) },
                        onNext = { auditionsNext(audition, nav) },
                        onMarkSent = {
                            store.markSent(audition.id)
                            haptics.success()
                            openRowId = null
                        },
                        onDelete = {
                            pendingDelete = audition
                            openRowId = null
                        },
                    )
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = c.surfaceRaised,
            title = { Text("Delete “${toDelete.project}”?", style = OT.type.headline, color = c.labelPrimary) },
            text = {
                Text(
                    "Its slate, reader track and any takes are removed from this phone. Your headshots stay in Profile.",
                    style = OT.type.callout,
                    color = c.labelSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(toDelete.id)
                    haptics.warning()
                    pendingDelete = null
                }) { Text("Delete", style = OT.type.headline, color = c.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", style = OT.type.headline, color = c.accent)
                }
            },
        )
    }
}

private fun auditionsNext(audition: Audition, nav: Navigator) {
    val next = audition.nextPiece
    if (next == null) {
        nav.push(Route.Package(audition.id))
        return
    }
    when (next.kind) {
        PieceKind.Headshot -> nav.push(Route.Headshots)
        PieceKind.Slate, PieceKind.FullLength -> nav.openCamera(CameraFlow.Shoot(audition.id))
        PieceKind.Scene ->
            if (next.title == "Watch it once") nav.push(Route.Package(audition.id))
            else nav.openCamera(CameraFlow.Shoot(audition.id))
    }
}

@Composable
private fun AuditionsHeader(filter: AuditionsFilter, onFilter: (AuditionsFilter) -> Unit, onAdd: () -> Unit) {
    val c = OT.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OT.dims.gutter)
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Auditions", style = OT.type.largeTitle, color = c.labelPrimary, modifier = Modifier.semantics { heading() })
            Text("Your audition library", style = OT.type.footnote, color = c.labelTertiary)
        }
        Box {
            Row(
                Modifier
                    .minimumInteractiveComponentSize()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (c.isDark) c.surfaceRaised else c.surfaceCard)
                    .border(0.5.dp, c.separator, CircleShape)
                    .clickable(role = Role.Button) { menuOpen = true }
                    .semantics { contentDescription = "Show: ${filter.label}" }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text(filter.label, style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.labelPrimary, maxLines = 1)
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = c.labelSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                AuditionsFilter.entries.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f.label, style = OT.type.body, color = c.labelPrimary) },
                        onClick = {
                            onFilter(f)
                            menuOpen = false
                        },
                        trailingIcon = {
                            if (f == filter) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        CircleIconButton(icon = Icons.Filled.Add, contentDescription = "Add an audition", onClick = onAdd)
    }
}

// MARK: - Swipe row (05.2): drag left to reveal Mark sent / Delete

private val AuditionsActionWidth = 78.dp
private val AuditionsCardRadius = 20.dp

@Composable
private fun AuditionsSwipeRow(
    audition: Audition,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onNext: () -> Unit,
    onMarkSent: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = OT.colors
    val isSent = audition.status == AuditionStatus.Sent
    val actionCount = if (isSent) 1 else 2
    val revealPx = with(LocalDensity.current) { (AuditionsActionWidth * actionCount).toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Close when another row opens (or after an action).
    LaunchedEffect(isOpen) {
        if (!isOpen && offset.value != 0f) offset.animateTo(0f)
    }

    val shape = RoundedCornerShape(AuditionsCardRadius)
    Box(Modifier.fillMaxWidth()) {
        if (offset.value < -0.5f) {
            Row(
                Modifier
                    .matchParentSize()
                    .clip(shape),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!isSent) {
                    AuditionsSwipeAction(
                        label = "Mark sent",
                        icon = Icons.AutoMirrored.Filled.Send,
                        color = c.success,
                        onClick = onMarkSent,
                    )
                }
                AuditionsSwipeAction(
                    label = "Delete",
                    icon = Icons.Filled.Delete,
                    color = c.danger,
                    onClick = onDelete,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-revealPx, 0f)) }
                    },
                    onDragStopped = { velocity ->
                        val open = offset.value < -revealPx / 2 || velocity < -1200f
                        offset.animateTo(if (open) -revealPx else 0f)
                        onOpenChange(open)
                    },
                )
                .semantics {
                    customActions = buildList {
                        if (!isSent) add(CustomAccessibilityAction("Mark sent") { onMarkSent(); true })
                        add(CustomAccessibilityAction("Delete") { onDelete(); true })
                    }
                },
        ) {
            AuditionsCard(
                audition = audition,
                onClick = {
                    if (abs(offset.value) > 1f) {
                        scope.launch { offset.animateTo(0f) }
                        onOpenChange(false)
                    } else {
                        onClick()
                    }
                },
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun AuditionsSwipeAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .width(AuditionsActionWidth)
            .fillMaxHeight()
            .background(color)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
    }
}

// MARK: - Audition card (v2/Audition Card · Due / Ready / Sent)

/** iOS `.relative(presentation: .named)`: "tomorrow", "in 3 days", "in 5 hours", "2 days ago". */
private fun auditionsRelative(date: LocalDateTime): String {
    val now = LocalDateTime.now()
    val minutes = Duration.between(now, date).toMinutes()
    val future = minutes >= 0
    val m = abs(minutes)
    fun unit(n: Long, one: String) = if (n == 1L) "1 $one" else "$n ${one}s"
    val span = when {
        m < 1 -> return "now"
        m < 60 -> unit(m, "minute")
        m < 60 * 24 -> unit(m / 60, "hour")
        m < 60 * 24 * 2 -> return if (future) "tomorrow" else "yesterday"
        m < 60 * 24 * 7 -> unit(m / (60 * 24), "day")
        m < 60 * 24 * 14 -> return if (future) "next week" else "last week"
        else -> unit(m / (60 * 24 * 7), "week")
    }
    return if (future) "in $span" else "$span ago"
}

@Composable
private fun AuditionsCard(audition: Audition, onClick: () -> Unit, onNext: () -> Unit) {
    val c = OT.colors
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "auditionCardPress")
    val isSent = audition.status == AuditionStatus.Sent

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .otCard(AuditionsCardRadius, c.surfaceCard)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HeadshotImage(
            Modifier
                .size(width = 88.dp, height = 120.dp)
                .clip(RoundedCornerShape(14.dp)),
            name = audition.headshotAsset,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    audition.project,
                    style = OT.type.headline,
                    color = c.labelPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isSent) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(c.success.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = c.success, modifier = Modifier.size(12.dp))
                        Text("Sent", style = OT.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.success)
                    }
                }
            }
            Text(
                "${audition.projectType} · ${audition.role}",
                style = OT.type.footnote,
                color = c.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSent) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = c.labelTertiary, modifier = Modifier.size(14.dp))
                    Text(
                        "${audition.sentDate?.otDayMonth ?: ""} · link active 22 days",
                        style = OT.type.footnote,
                        color = c.labelTertiary,
                        maxLines = 1,
                    )
                }
            } else {
                ReadinessDashes(done = audition.readyCount, total = audition.totalCount)
                if (audition.status == AuditionStatus.Ready) {
                    AuditionsStatusLine(
                        icon = Icons.Filled.CheckCircle,
                        text = "Ready to send · due ${auditionsRelative(audition.due)}",
                        color = c.success,
                    )
                } else {
                    AuditionsStatusLine(
                        icon = Icons.Filled.Schedule,
                        text = "Due ${auditionsRelative(audition.due)}",
                        color = c.due,
                    )
                }
                OTDivider(Modifier.padding(vertical = 2.dp))
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onNext),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(auditionsNextIcon(audition), contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                    Text(
                        "Next: ${audition.nextAction}",
                        style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                        color = c.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AuditionsStatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(text, style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun auditionsNextIcon(audition: Audition): ImageVector {
    val next = audition.nextPiece ?: return Icons.AutoMirrored.Filled.Send
    return when (next.kind) {
        PieceKind.Headshot -> Icons.Outlined.AccountBox
        else -> if (next.title == "Watch it once") Icons.AutoMirrored.Filled.Send else Icons.Filled.Videocam
    }
}

// MARK: - 05a Empty

@Composable
private fun AuditionsEmptyState(onAdd: () -> Unit) {
    val c = OT.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OT.dims.gutter)
            .padding(top = 24.dp, bottom = TabBarClearance + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AuditionsEmptyIllustration()
        Text("No auditions yet", style = OT.type.title, color = c.labelPrimary, modifier = Modifier.semantics { heading() })
        /**
        Text(
            "Share the notice to OneTake from Mail, Safari or WhatsApp, or add it here. OneTake builds your slate, headshot and scene checklist from it.",
            style = OT.type.callout,
            color = c.labelSecondary,
        )
        PrimaryButton(text = "Add your first audition", onClick = onAdd, icon = Icons.Filled.Add, iconTrailing = true)
        **/
    }
}

/** Stacked "notice" sheets with a clapper badge (Figma 05a). Decorative. */
@Composable
private fun AuditionsEmptyIllustration() {
    val c = OT.colors
    val sheet = if (c.isDark) c.surfaceRaised else c.surfaceCard
    Box(Modifier.size(width = 160.dp, height = 130.dp)) {
        Box(
            Modifier
                .offset(x = 0.dp, y = 14.dp)
                .size(width = 120.dp, height = 110.dp)
                .graphicsLayer { rotationZ = -6f }
                .clip(RoundedCornerShape(16.dp))
                .background(c.fillQuiet),
        )
        Column(
            Modifier
                .offset(x = 26.dp, y = 6.dp)
                .size(width = 104.dp, height = 112.dp)
                .otCard(16.dp, sheet)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(56.dp).height(6.dp).clip(CircleShape).background(c.fillField))
            Box(Modifier.width(76.dp).height(6.dp).clip(CircleShape).background(c.fillField))
            Box(Modifier.width(46.dp).height(6.dp).clip(CircleShape).background(c.fillField))
            Box(Modifier.width(66.dp).height(6.dp).clip(CircleShape).background(c.fillField))
        }
        Box(
            Modifier
                .offset(x = 108.dp, y = 88.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = c.labelOnAccent, modifier = Modifier.size(20.dp))
        }
    }
}

// MARK: - 05.1 Search (tab-bar search)

private enum class SearchScope(val label: String) {
    All("All"),
    DueSoon("Due soon"),
    Sent("Sent"),
    Scenes("Scenes"),
}

private data class SearchLineHit(
    val key: String,
    val auditionId: String,
    val sceneNumber: Int,
    val character: String,
    val text: String,
    val detail: String,
)

/** Tab root for the Search tab: auditions, roles and lines in your sides. */
@Composable
fun SearchScreen() {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val focus = LocalFocusManager.current
    val c = OT.colors
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(SearchScope.All) }
    var focused by remember { mutableStateOf(false) }

    val q = query.trim()
    val matches: (Audition) -> Boolean = {
        q.isEmpty() || it.project.contains(q, ignoreCase = true) || it.role.contains(q, ignoreCase = true)
    }
    val auditionHits: List<Audition> = when (scope) {
        SearchScope.All -> store.auditions.filter(matches)
        SearchScope.DueSoon -> store.active.filter(matches)
        SearchScope.Sent -> store.sent.filter(matches)
        SearchScope.Scenes -> emptyList()
    }
    val lineHits: List<SearchLineHit> = if (q.isEmpty() || scope == SearchScope.DueSoon || scope == SearchScope.Sent) {
        emptyList()
    } else {
        buildList {
            store.auditions.forEach { a ->
                a.scenes.forEach { s ->
                    s.lines.forEach { l ->
                        if (l.text.contains(q, ignoreCase = true)) {
                            add(SearchLineHit(a.id + l.id, a.id, s.number, l.character, l.text, "${a.project} · ${s.title}"))
                        }
                    }
                }
            }
        }
    }
    val showChrome = focused || query.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .statusBarsPadding(),
    ) {
        if (!showChrome) {
            LargeTitle("Search", Modifier.padding(horizontal = OT.dims.gutter).padding(top = 8.dp, bottom = 10.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OT.dims.gutter)
                .padding(top = if (showChrome) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(c.fillField)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = c.labelSecondary, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Auditions, roles, lines", style = OT.type.body, color = c.labelTertiary, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = OT.type.body.copy(color = c.labelPrimary),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .semantics { contentDescription = "Search auditions, roles, lines" },
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = c.labelTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { query = "" },
                    )
                }
            }
            if (showChrome) {
                Text(
                    "Cancel",
                    style = OT.type.body,
                    color = c.accent,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            query = ""
                            scope = SearchScope.All
                            focus.clearFocus()
                        }
                        .padding(horizontal = 4.dp),
                )
            }
        }
        if (showChrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OT.dims.gutter)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchScope.entries.forEach { s ->
                    SearchScopeChip(s.label, selected = s == scope) { scope = s }
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OT.dims.gutter,
                end = OT.dims.gutter,
                top = 12.dp,
                bottom = TabBarClearance + 16.dp,
            ),
        ) {
            if (scope != SearchScope.Scenes) {
                item(key = "h-auditions") { SectionLabel("Auditions", Modifier.padding(start = 4.dp, bottom = 2.dp)) }
                if (auditionHits.isEmpty()) {
                    item(key = "none-auditions") {
                        Text(
                            if (q.isEmpty()) "No auditions yet." else "No auditions match “$q”.",
                            style = OT.type.footnote,
                            color = c.labelTertiary,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                }
                items(auditionHits.size, key = { "a-" + auditionHits[it].id }) { i ->
                    val a = auditionHits[i]
                    val sent = a.status == AuditionStatus.Sent
                    Column {
                    ScheduleRow(
                        eyebrow = if (sent) "SENT" else a.due.otWeekdayCaps,
                        value = if (sent) a.sentDate?.otDayMonth.orEmpty() else "${a.hoursLeft}h",
                        title = a.project,
                        detail = if (sent) "${a.role} · sent ${a.sentDate?.otDayMonth.orEmpty()}" else "${a.role} · due ${a.due.otDue}",
                        eyebrowColor = if (sent) c.labelTertiary else c.due,
                        valueColor = if (sent) c.labelPrimary else c.due,
                        onClick = { nav.push(Route.AuditionDetail(a.id)) },
                    )
                    if (i != auditionHits.lastIndex) OTDivider()
                    }
                }
            }
            if (q.isNotEmpty() && (scope == SearchScope.All || scope == SearchScope.Scenes)) {
                item(key = "h-sides") {
                    SectionLabel(
                        "In your sides · ${lineHits.size}",
                        Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
                    )
                }
                if (lineHits.isEmpty()) {
                    item(key = "none-sides") {
                        Text(
                            "No lines match “$q”.",
                            style = OT.type.footnote,
                            color = c.labelTertiary,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                }
                items(lineHits.size, key = { "l-" + lineHits[it].key }) { i ->
                    val hit = lineHits[i]
                    Column {
                    ScheduleRow(
                        eyebrow = "SC ${hit.sceneNumber}",
                        value = hit.character.lowercase().replaceFirstChar { it.uppercase() },
                        title = "“${hit.text}”",
                        detail = hit.detail,
                        onClick = { nav.push(Route.Prepare(hit.auditionId, hit.sceneNumber - 1)) },
                    )
                    if (i != lineHits.lastIndex) OTDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = OT.colors
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .height(34.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else if (c.isDark) c.surfaceRaised else c.surfaceCard)
            .then(if (selected) Modifier else Modifier.border(0.5.dp, c.separator, CircleShape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = OT.type.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) c.labelOnAccent else c.labelPrimary,
            maxLines = 1,
        )
    }
}
