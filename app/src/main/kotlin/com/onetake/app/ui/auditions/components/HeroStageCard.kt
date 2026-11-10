package com.onetake.app.ui.auditions.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.otRelative
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

/**
 * Spotlight Stage Monolithic Hero Card:
 * Dramatic theatrical staging with warm dark obsidian palette,
 * gold accent overline, large role title, and immediate CTA launcher.
 */
@Composable
fun HeroStageCard(
    slate: Audition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = remember { RoundedCornerShape(28.dp) }
    val dueText = slate.due.otRelative
    val isOverdue = slate.status != AuditionStatus.Sent && java.time.LocalDateTime.now().isAfter(slate.due)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(cardShape)
            .border(GLASS_EDGE_WIDTH, OT.colors.borderMuted.copy(alpha = 0.6f), cardShape)
            .background(OT.colors.surfaceStage)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Up next audition: ${slate.role} in ${slate.project}, $dueText"
            },
    ) {
        // Ambient background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F1D1A),
                            Color(0xFF141311),
                            Color(0xFF0F0E0C),
                        )
                    )
                )
        )

        // Top subtle light beam
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OT.colors.accentGold.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: Overline + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        slate.status == AuditionStatus.Sent -> "SENT"
                        isOverdue -> "OVERDUE · ACTION REQUIRED"
                        else -> "YOUR ONE THING TONIGHT"
                    },
                    style = OT.type.stageOverline,
                    color = if (isOverdue) OT.colors.recRed else OT.colors.accentGold,
                )

                Text(
                    text = if (slate.status == AuditionStatus.Sent) "Sent" else "Due $dueText",
                    style = OT.type.footnote,
                    color = OT.colors.labelTertiary,
                )
            }

            // Body: Role & Project Title
            Column {
                Text(
                    text = slate.role,
                    style = OT.type.heroStageTitle,
                    color = OT.colors.labelPrimary,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = slate.project,
                    style = OT.type.body.copy(fontWeight = FontWeight.Medium),
                    color = OT.colors.labelSecondary,
                    maxLines = 1,
                )
            }

            // Bottom Action Row: Next step + Arrow launch button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slate.nextAction.ifBlank { "Prepare Audition" },
                    style = OT.type.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = OT.colors.accentGold,
                )

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(OT.colors.stageLight)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = Color(0xFF050505),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
