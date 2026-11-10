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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.models.Audition
import com.onetake.app.models.otRelative
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

/**
 * Spotlight Stage Inset Roster:
 * Quiet, elegant rows of upcoming auditions in the wings.
 */
@Composable
fun InsetRosterList(
    slates: List<Audition>,
    onSlateClick: (Audition) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slates.isEmpty()) return

    val containerShape = remember { RoundedCornerShape(24.dp) }
    val thumbShape = remember { RoundedCornerShape(14.dp) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "IN THE WINGS",
            style = OT.type.stageOverline,
            color = OT.colors.labelSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(containerShape)
                .border(GLASS_EDGE_WIDTH, OT.colors.borderMuted.copy(alpha = 0.5f), containerShape)
                .background(OT.colors.surfaceStage),
        ) {
            slates.forEachIndexed { index, slate ->
                val dueText = slate.due.otRelative

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                        ) {
                            onSlateClick(slate)
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        // 44dp squircle thumbnail
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(thumbShape)
                                .background(Color(0xFF1E1C18))
                                .border(0.5.dp, OT.colors.borderMuted.copy(alpha = 0.5f), thumbShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = OT.colors.accentGold,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = slate.role,
                                style = OT.type.wingRowTitle,
                                color = OT.colors.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = slate.project,
                                style = OT.type.footnote,
                                color = OT.colors.labelSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (dueText.isNotEmpty()) {
                        Text(
                            text = dueText,
                            style = OT.type.footnote.copy(fontWeight = FontWeight.Medium),
                            color = OT.colors.due,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (index < slates.size - 1) {
                    HorizontalDivider(
                        color = OT.colors.borderMuted.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}
