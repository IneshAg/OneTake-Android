package com.onetake.app.ui.takes

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppViewModel
import com.onetake.app.models.Take
import com.onetake.app.ui.components.OtPrimaryButton
import com.onetake.app.ui.components.OtSecondaryButton
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.theme.OneTakeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakesScreen(
    auditionId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onGoAgain: () -> Unit,
    onTapeCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val takes = uiState.takes
    val selectedTake = takes.firstOrNull { it.isCircled } ?: takes.firstOrNull()
    var activeTakeId by remember(takes) { mutableStateOf(selectedTake?.id ?: "") }
    val currentTake = takes.firstOrNull { it.id == activeTakeId } ?: selectedTake

    var sliderPos by remember { mutableFloatStateOf(0.35f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneTakeTheme.colors.bgCanvas)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Your takes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = OneTakeTheme.colors.labelPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OneTakeTheme.colors.labelPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Trim",
                        tint = OneTakeTheme.colors.labelPrimary
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.ViewColumn,
                        contentDescription = "Compare",
                        tint = OneTakeTheme.colors.labelPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = OneTakeTheme.colors.bgCanvas
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Video Player Card Mock
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Take pill overlay
                    currentTake?.let { t ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Take ${t.number} · ${t.durationSeconds / 60}:${String.format("%02d", t.durationSeconds % 60)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Playback Scrubber
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPos,
                    onValueChange = { sliderPos = it },
                    colors = SliderDefaults.colors(
                        thumbColor = OneTakeTheme.colors.accent,
                        activeTrackColor = OneTakeTheme.colors.accent
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text("0:00", fontSize = 12.sp, color = OneTakeTheme.colors.labelTertiary)
                    Text("your lines · tap to jump", fontSize = 12.sp, color = OneTakeTheme.colors.accent)
                    Text(
                        "${(currentTake?.durationSeconds ?: 90) / 60}:${String.format("%02d", (currentTake?.durationSeconds ?: 90) % 60)}",
                        fontSize = 12.sp,
                        color = OneTakeTheme.colors.labelTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Takes Thumbnails Row
            SectionLabel(text = "Choose circled take (${takes.size} recorded)")
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(takes) { take ->
                    val isSelected = take.id == (currentTake?.id ?: "")
                    TakeThumbnailCard(
                        take = take,
                        isSelected = isSelected,
                        onClick = { activeTakeId = take.id },
                        onCircle = { viewModel.circleTake(take.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notes to Self
            SectionLabel(text = "Notes to self (never sent)")
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = OneTakeTheme.colors.surfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    currentTake?.notes?.forEach { note ->
                        Text(
                            text = "• $note",
                            fontSize = 14.sp,
                            color = OneTakeTheme.colors.labelPrimary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    if (currentTake?.notes.isNullOrEmpty()) {
                        Text(
                            text = "No notes recorded for this take",
                            fontSize = 13.sp,
                            color = OneTakeTheme.colors.labelTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Bottom Action Bar
        Surface(
            color = OneTakeTheme.colors.bgCanvas,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OtSecondaryButton(
                    text = "Go again",
                    onClick = onGoAgain,
                    modifier = Modifier.width(130.dp)
                )

                OtPrimaryButton(
                    text = "Send take ${takes.firstOrNull { it.isCircled }?.number ?: 1}",
                    onClick = onTapeCheck,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TakeThumbnailCard(
    take: Take,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCircle: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = OneTakeTheme.colors.surfaceCard,
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .width(120.dp)
            .then(
                if (isSelected) Modifier.border(2.dp, OneTakeTheme.colors.accent, RoundedCornerShape(14.dp))
                else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Mock thumbnail preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OneTakeTheme.colors.surfaceSunken),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = OneTakeTheme.colors.labelTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Take ${take.number}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneTakeTheme.colors.labelPrimary
                )

                IconButton(
                    onClick = onCircle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (take.isCircled) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Circle Take",
                        tint = if (take.isCircled) OneTakeTheme.colors.due else OneTakeTheme.colors.labelTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "${take.durationSeconds / 60}:${String.format("%02d", take.durationSeconds % 60)}",
                fontSize = 12.sp,
                color = OneTakeTheme.colors.labelTertiary
            )
        }
    }
}
