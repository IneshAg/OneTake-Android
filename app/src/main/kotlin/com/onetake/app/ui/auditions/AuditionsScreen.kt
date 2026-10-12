package com.onetake.app.ui.auditions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppViewModel
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.PieceKind
import com.onetake.app.ui.components.AuditionCard
import com.onetake.app.ui.theme.OneTakeTheme

enum class AuditionFilter(val label: String) {
    ALL("All"),
    DUE("Due"),
    READY("Ready"),
    SENT("Sent")
}

@Composable
fun AuditionsScreen(
    viewModel: AppViewModel,
    onNavigateToAudition: (String) -> Unit,
    onNavigateToShoot: (String) -> Unit,
    onNavigateToPackage: (String) -> Unit,
    onAddAudition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf(AuditionFilter.ALL) }
    var auditionToDelete by remember { mutableStateOf<Audition?>(null) }

    val filteredAuditions = when (selectedFilter) {
        AuditionFilter.ALL -> uiState.auditions
        AuditionFilter.DUE -> uiState.auditions.filter { it.status == AuditionStatus.DUE }
        AuditionFilter.READY -> uiState.auditions.filter { it.status == AuditionStatus.READY }
        AuditionFilter.SENT -> uiState.auditions.filter { it.status == AuditionStatus.SENT }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OneTakeTheme.colors.bgCanvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Auditions",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneTakeTheme.colors.labelPrimary
                )
                Text(
                    text = "${uiState.auditions.size} auditions in your library",
                    fontSize = 14.sp,
                    color = OneTakeTheme.colors.labelTertiary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Filter chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AuditionFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OneTakeTheme.colors.accent,
                                selectedLabelColor = OneTakeTheme.colors.labelOnAccent,
                                containerColor = OneTakeTheme.colors.surfaceCard,
                                labelColor = OneTakeTheme.colors.labelPrimary
                            )
                        )
                    }
                }
            }

            // List
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAuditions, key = { it.id }) { audition ->
                    AuditionCard(
                        audition = audition,
                        onClick = { onNavigateToAudition(audition.id) },
                        onNextActionClick = {
                            val next = audition.nextPiece
                            if (next == null || audition.status == AuditionStatus.READY) {
                                onNavigateToPackage(audition.id)
                            } else {
                                onNavigateToShoot(audition.id)
                            }
                        }
                    )
                }
            }
        }

        // FAB to add
        FloatingActionButton(
            onClick = onAddAudition,
            containerColor = OneTakeTheme.colors.accent,
            contentColor = OneTakeTheme.colors.labelOnAccent,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 90.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Audition")
        }

        // Delete Dialog
        auditionToDelete?.let { audition ->
            AlertDialog(
                onDismissRequest = { auditionToDelete = null },
                title = { Text("Delete “${audition.project}”?") },
                text = { Text("Its slate, takes, and reader tracks will be removed from this device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAudition(audition.id)
                            auditionToDelete = null
                        }
                    ) {
                        Text("Delete", color = OneTakeTheme.colors.danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { auditionToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
