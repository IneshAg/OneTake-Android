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
 * System 2: Inset Roster List
 * Grouped container (24dp rounded squircle), displaying upcoming slates in uniform, quiet rows.
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
            text = "UPCOMING SLATES",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.sp,
            color = Color(0xFF86868B),
            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(containerShape)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, containerShape)
                .background(OT.colors.surfaceCard),
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
                        // 48dp squircle thumbnail
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(thumbShape)
                                .background(Color(0xFF1E1A24))
                                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, thumbShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = OT.colors.accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = slate.role,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = slate.project,
                                fontSize = 13.sp,
                                color = Color(0xFF86868B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (dueText.isNotEmpty()) {
                        Text(
                            text = dueText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = OT.colors.due,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (index < slates.size - 1) {
                    HorizontalDivider(
                        color = Color(0x14FFFFFF),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}
