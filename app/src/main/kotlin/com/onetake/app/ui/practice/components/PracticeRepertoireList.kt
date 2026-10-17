package com.onetake.app.ui.practice.components

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
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.models.PracticePiece
import com.onetake.app.ui.components.Chevron
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

/**
 * Inset Roster: Saved Scripts & Assigned Voice Partners
 */
@Composable
fun PracticeRepertoireList(
    pieces: List<PracticePiece>,
    onPieceClick: (PracticePiece) -> Unit,
    onAddScriptClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerShape = remember { RoundedCornerShape(24.dp) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Text(
            text = "YOUR SCRIPTS & SCENE PARTNERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.sp,
            color = Color(0xFF86868B),
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
        )

        // Inset Grouped Roster Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(containerShape)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, containerShape)
                .background(OT.colors.surfaceCard),
        ) {
            if (pieces.isEmpty()) {
                // Empty invitation row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onAddScriptClick,
                        )
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = OT.colors.accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Import Your First Script",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Drop a PDF or paste dialogue sides to assign AI reader",
                                fontSize = 13.sp,
                                color = Color(0xFF86868B),
                            )
                        }
                    }

                    Chevron(tint = Color(0xFF86868B))
                }
            } else {
                pieces.forEachIndexed { index, piece ->
                    RepertoireRow(
                        piece = piece,
                        onClick = { onPieceClick(piece) },
                    )

                    if (index < pieces.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color(0x14FFFFFF),
                            modifier = Modifier.padding(start = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepertoireRow(
    piece: PracticePiece,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = piece.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${piece.detail} · ${piece.linesLearned} of ${piece.linesTotal} lines off-book",
                fontSize = 13.sp,
                color = Color(0xFF86868B),
                lineHeight = 17.sp,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Chevron(tint = Color(0xFF86868B))
    }
}
