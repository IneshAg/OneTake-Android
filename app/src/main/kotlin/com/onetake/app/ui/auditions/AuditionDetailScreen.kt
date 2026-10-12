package com.onetake.app.ui.auditions

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppViewModel
import com.onetake.app.models.Audition
import com.onetake.app.models.Piece
import com.onetake.app.models.PieceKind
import com.onetake.app.ui.components.OtPrimaryButton
import com.onetake.app.ui.components.OtSecondaryButton
import com.onetake.app.ui.components.ReadinessDial
import com.onetake.app.ui.components.SectionLabel
import com.onetake.app.ui.components.StatusBadge
import com.onetake.app.ui.theme.OneTakeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditionDetailScreen(
    auditionId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToShoot: (String) -> Unit,
    onNavigateToTakes: (String) -> Unit,
    onNavigateToPackage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val audition = uiState.auditions.firstOrNull { it.id == auditionId }

    if (audition == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Audition not found")
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OneTakeTheme.colors.bgCanvas)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = audition.project,
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = OneTakeTheme.colors.bgCanvas
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Summary Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneTakeTheme.colors.surfaceCard,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            StatusBadge(audition = audition)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${audition.role} (${audition.roleSize})",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneTakeTheme.colors.labelPrimary
                            )
                            Text(
                                text = "${audition.projectType} · ${audition.castingOffice}",
                                fontSize = 14.sp,
                                color = OneTakeTheme.colors.labelSecondary
                            )
                        }

                        ReadinessDial(
                            done = audition.readyCount,
                            total = audition.totalCount,
                            size = 54.dp,
                            strokeWidth = 6.dp
                        )
                    }

                    if (audition.asks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            audition.asks.take(4).forEach { ask ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(OneTakeTheme.colors.surfaceSunken)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = ask,
                                        fontSize = 11.sp,
                                        color = OneTakeTheme.colors.labelSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Pieces Section
            SectionLabel(text = "Required Pieces (${audition.readyCount}/${audition.totalCount})")
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OneTakeTheme.colors.surfaceCard,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    audition.pieces.forEachIndexed { index, piece ->
                        PieceRow(
                            piece = piece,
                            onToggle = {
                                viewModel.markDone(audition.id, piece.kind, piece.title)
                            },
                            onShoot = { onNavigateToShoot(audition.id) }
                        )
                        if (index < audition.pieces.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(vertical = 6.dp)
                                    .background(OneTakeTheme.colors.separator)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Destination Card
            SectionLabel(text = "Submission Destination")
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = OneTakeTheme.colors.surfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = audition.destination.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OneTakeTheme.colors.labelPrimary
                    )
                    Text(
                        text = audition.destination.spec,
                        fontSize = 13.sp,
                        color = OneTakeTheme.colors.labelTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Action Buttons
            OtPrimaryButton(
                text = "Shoot tape",
                onClick = { onNavigateToShoot(audition.id) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OtSecondaryButton(
                    text = "Review takes",
                    onClick = { onNavigateToTakes(audition.id) },
                    modifier = Modifier.weight(1f)
                )

                OtSecondaryButton(
                    text = "Package & send",
                    onClick = { onNavigateToPackage(audition.id) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun PieceRow(
    piece: Piece,
    onToggle: () -> Unit,
    onShoot: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (piece.isDone) OneTakeTheme.colors.success
                    else OneTakeTheme.colors.surfaceSunken
                ),
            contentAlignment = Alignment.Center
        ) {
            if (piece.isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = piece.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = OneTakeTheme.colors.labelPrimary
            )
            if (piece.detail.isNotEmpty()) {
                Text(
                    text = piece.detail,
                    fontSize = 13.sp,
                    color = OneTakeTheme.colors.labelSecondary
                )
            }
        }

        if (!piece.isDone) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(OneTakeTheme.colors.accentSoft)
                    .clickable { onShoot() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Shoot",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneTakeTheme.colors.accent
                )
            }
        }
    }
}
