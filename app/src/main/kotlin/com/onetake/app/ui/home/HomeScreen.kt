package com.onetake.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppViewModel
import com.onetake.app.models.Audition
import com.onetake.app.models.PieceKind
import com.onetake.app.models.SampleData
import com.onetake.app.models.ScheduleItem
import com.onetake.app.ui.components.ReadinessDial
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.theme.OneTakeTheme

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateToAudition: (String) -> Unit,
    onNavigateToShoot: (String) -> Unit,
    onNavigateToPackage: (String) -> Unit,
    onAddAudition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val nextPriority = viewModel.nextPriority

    Box(modifier = modifier.fillMaxSize().background(OneTakeTheme.colors.bgCanvas)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column {
                    Text(
                        text = "OneTake",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneTakeTheme.colors.labelPrimary
                    )
                    Text(
                        text = "Actor self-tape studio",
                        fontSize = 14.sp,
                        color = OneTakeTheme.colors.labelTertiary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(OneTakeTheme.colors.surfaceCard)
                        .clickable { onAddAudition() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Audition",
                        tint = OneTakeTheme.colors.labelPrimary
                    )
                }
            }

            // Priority Audition Card
            if (nextPriority != null) {
                PriorityAuditionHeroCard(
                    audition = nextPriority,
                    onClick = { onNavigateToAudition(nextPriority.id) },
                    onActionClick = {
                        val next = nextPriority.nextPiece
                        if (next == null) {
                            onNavigateToPackage(nextPriority.id)
                        } else {
                            onNavigateToShoot(nextPriority.id)
                        }
                    }
                )
            } else {
                FirstRunCard(onAdd = onAddAudition)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Schedule Section
            SectionLabel(text = "Upcoming Schedule")
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneTakeTheme.colors.surfaceCard,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SampleData.schedule.forEachIndexed { index, item ->
                        ScheduleRow(item = item)
                        if (index < SampleData.schedule.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(vertical = 8.dp)
                                    .background(OneTakeTheme.colors.separator)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp)) // padding for bottom bar
        }
    }
}

@Composable
fun PriorityAuditionHeroCard(
    audition: Audition,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OneTakeTheme.colors.accent,
                            OneTakeTheme.colors.accent.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val hours = audition.hoursLeft
                    val dueText = if (hours < 24) "Due in $hours h" else "Due in ${hours / 24} d"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = dueText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    ReadinessDial(
                        done = audition.readyCount,
                        total = audition.totalCount,
                        size = 44.dp,
                        strokeWidth = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = audition.project,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "${audition.projectType} · ${audition.role}",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onActionClick() }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Next: ${audition.nextAction}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleRow(item: ScheduleItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.width(90.dp)) {
            Text(
                text = item.whenDay,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OneTakeTheme.colors.accent
            )
            Text(
                text = item.time,
                fontSize = 13.sp,
                color = OneTakeTheme.colors.labelSecondary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OneTakeTheme.colors.labelPrimary
            )
            Text(
                text = item.detail,
                fontSize = 13.sp,
                color = OneTakeTheme.colors.labelTertiary
            )
        }
    }
}

@Composable
fun FirstRunCard(onAdd: () -> Unit) {
    Surface(
        onClick = onAdd,
        shape = RoundedCornerShape(24.dp),
        color = OneTakeTheme.colors.surfaceCard,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(OneTakeTheme.colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = OneTakeTheme.colors.labelOnAccent
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Add your first audition",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = OneTakeTheme.colors.labelPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Paste a casting notice or add sides. OneTake works out what they want.",
                fontSize = 14.sp,
                color = OneTakeTheme.colors.labelSecondary
            )
        }
    }
}
