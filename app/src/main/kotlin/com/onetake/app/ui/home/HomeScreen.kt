package com.onetake.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppTab
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
import com.onetake.app.models.otTime
import com.onetake.app.models.otWeekdayCaps
import com.onetake.app.ui.components.CircleIconButton
import com.onetake.app.ui.components.HeadshotImage
import com.onetake.app.ui.components.OTDivider
import com.onetake.app.ui.components.PillButton
import com.onetake.app.ui.components.ReadinessDial
import com.onetake.app.ui.components.ScheduleRow
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.otCard
import com.onetake.app.ui.system.Haptics
import com.onetake.app.ui.system.rememberHaptics
import com.onetake.app.ui.theme.OT
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** H0 Home — the whole photo card is the button; one "Next:" line, readiness dial top-right. */
@Composable
fun HomeScreen() {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val haptics = rememberHaptics()
    val c = OT.colors
    val next = store.nextPriority
    val isEmpty = store.auditions.isEmpty()

    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = OT.dims.gutter)
                .padding(bottom = TabBarClearance + 24.dp),
        ) {
            HomeHeader(
                greetingPrefix = when {
                    isEmpty -> "Welcome"
                    next == null -> "All clear"
                    else -> homeGreeting()
                },
                firstName = store.slate.name.split(" ").firstOrNull().orEmpty(),
                onAdd = { nav.showAddAudition() },
            )

            when {
                next != null -> {
                    HomeNextPriorityCard(next, Modifier.padding(top = 18.dp)) { homeOpen(next, nav, haptics) }
                    HomePickUp(Modifier.padding(top = 26.dp))
                    HomeSchedule(Modifier.padding(top = 22.dp))
                }
                isEmpty -> {
                    HomeFirstRunCard(Modifier.padding(top = 18.dp)) { nav.showAddAudition() }
                    Column(Modifier.padding(top = 26.dp)) {
                        SectionLabel("Pick up where you left off")
                        ScheduleRow(
                            eyebrow = "PRACTICE",
                            value = "Any time",
                            title = "Practise a scene",
                            detail = "No deadline · nothing is ever sent",
                            onClick = { nav.push(Route.PracticeSetup) },
                        )
                    }
                    Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("Your schedule")
                        Text(
                            "Callbacks, shoots and due dates show here once you add an audition.",
                            style = OT.type.footnote,
                            color = c.labelTertiary,
                        )
                    }
                }
                else -> {
                    val lastSent = store.sent.maxByOrNull { it.sentDate ?: it.due }
                    HomeAllClearCard(lastSent, Modifier.padding(top = 18.dp)) { nav.push(Route.PracticeSetup) }
                    HomePickUp(Modifier.padding(top = 26.dp))
                    HomeSchedule(Modifier.padding(top = 22.dp))
                }
            }
        }
    }
}

// MARK: - Header

private val homeDateFmt = DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.US)

private fun homeGreeting(): String {
    val h = LocalDateTime.now().hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun HomeHeader(greetingPrefix: String, firstName: String, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(LocalDateTime.now().format(homeDateFmt))
            Text(
                if (firstName.isEmpty()) greetingPrefix else "$greetingPrefix, $firstName",
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = OT.colors.labelPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        CircleIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add an audition",
            onClick = onAdd,
            filled = true,
        )
    }
}

// MARK: - Sections

@Composable
private fun HomePickUp(modifier: Modifier = Modifier) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val practiceTarget = store.practice.firstOrNull()
    val auditionTarget = store.auditions.firstOrNull { it.status != AuditionStatus.Sent }

    if (practiceTarget == null && auditionTarget == null) return

    val title = practiceTarget?.title ?: "${auditionTarget?.project} · Scene 1"
    val detail = practiceTarget?.detail ?: "Audition · Ready to rehearse"
    val resume = {
        if (practiceTarget != null) nav.push(Route.RunLines(practiceTarget.id, 0))
        else if (auditionTarget != null) nav.push(Route.Prepare(auditionTarget.id, 0))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel("Pick up where you left off")
        ScheduleRow(
            eyebrow = "LINES",
            value = practiceTarget?.let { "${it.linesLearned} of ${it.linesTotal}" } ?: "Active",
            title = title,
            detail = detail,
            onClick = resume,
            trailing = { PillButton(text = "Resume", onClick = resume) },
        )
    }
}

@Composable
private fun HomeSchedule(modifier: Modifier = Modifier) {
    val store = LocalAppStore.current
    val nav = LocalNavigator.current
    val items = store.active
    if (items.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel("Your schedule")
        items.forEachIndexed { i, audition ->
            ScheduleRow(
                eyebrow = audition.due.otWeekdayCaps,
                value = audition.due.otTime,
                title = audition.project,
                detail = "Due ${audition.due.otDue} · ${audition.role}",
                onClick = { nav.push(Route.AuditionDetail(audition.id)) },
            )
            if (i != items.lastIndex) OTDivider()
        }
    }
}

// MARK: - Routing — the card opens whatever the next piece needs

private fun homeOpen(audition: Audition, nav: Navigator, haptics: Haptics) {
    haptics.impact()
    val next = audition.nextPiece
    if (next == null) {
        nav.push(Route.Package(audition.id))
        return
    }
    when (next.kind) {
        PieceKind.Headshot -> nav.push(Route.Headshots)
        PieceKind.Slate, PieceKind.FullLength -> nav.push(Route.ShootSetup(audition.id))
        PieceKind.Scene ->
            if (next.title == "Watch it once") nav.push(Route.Package(audition.id))
            else nav.push(Route.Prepare(audition.id, 0))
    }
}

// MARK: - Next priority card

/** Amber reads well on the dark photo scrim (dark-mode `due` token). Amber = time only. */
private val HomeDueOnPhoto = Color(0xFFE2BE7A)

private fun homeDueText(a: Audition): String {
    val h = a.hoursLeft
    return when {
        h < 24 -> "Due in $h h · ${a.due.otTime}"
        h < 48 -> "Due tomorrow, ${a.due.otTime}"
        else -> "Due ${a.due.otDue}"
    }
}

@Composable
private fun homePressScale(src: MutableInteractionSource): Float {
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "homeCardPress")
    return scale
}

@Composable
private fun HomeNextPriorityCard(audition: Audition, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val scale = homePressScale(src)
    val shape = RoundedCornerShape(OT.dims.cardRadius)
    Box(
        modifier
            .fillMaxWidth()
            .height(360.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color(0xFF1F1F1F))
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
    ) {
        HeadshotImage(Modifier.fillMaxSize(), name = audition.headshotAsset)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.85f)),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = HomeDueOnPhoto, modifier = Modifier.size(14.dp))
                    Text(
                        homeDueText(audition),
                        style = OT.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(6.dp),
                ) {
                    ReadinessDial(done = audition.readyCount, total = audition.totalCount, onDark = true)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("NEXT PRIORITY", style = OT.type.eyebrow, color = Color.White.copy(alpha = 0.8f))
            Text(
                audition.project,
                style = OT.type.hero,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "${audition.projectType} · ${audition.role}",
                style = OT.type.callout,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Next: ${audition.nextAction}",
                    style = OT.type.headline,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// MARK: - First run (H0a) and all clear (H0b)

@Composable
private fun HomeInfoCard(
    modifier: Modifier,
    onClick: () -> Unit,
    badge: @Composable () -> Unit,
    title: String,
    body: String,
    nextLine: String,
) {
    val c = OT.colors
    val src = remember { MutableInteractionSource() }
    val scale = homePressScale(src)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .otCard(OT.dims.cardRadius, c.surfaceCard)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        badge()
        Text(title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = c.labelPrimary)
        Text(body, style = OT.type.callout, color = c.labelSecondary)
        OTDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nextLine, style = OT.type.headline, color = c.labelPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.labelTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HomeFirstRunCard(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val c = OT.colors
    HomeInfoCard(
        modifier = modifier,
        onClick = onAdd,
        badge = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = c.labelOnAccent, modifier = Modifier.size(24.dp))
            }
        },
        title = "Add your first audition",
        body = "Paste a casting notice, scan the sides or forward the email. OneTake works out what they want.",
        nextLine = "Next: Paste a casting notice",
    )
}

@Composable
private fun HomeAllClearCard(lastSent: Audition?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = OT.colors
    val sentDate = lastSent?.sentDate
    HomeInfoCard(
        modifier = modifier,
        onClick = onClick,
        badge = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.success), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
        title = "Nothing due this week",
        body = if (lastSent != null && sentDate != null) {
            "${lastSent.project} was sent ${sentDate.otDayMonth} · link active 22 days"
        } else {
            "Every audition is sent. Add a new one from +."
        },
        nextLine = "Next: Run a scene to stay sharp",
    )
}
