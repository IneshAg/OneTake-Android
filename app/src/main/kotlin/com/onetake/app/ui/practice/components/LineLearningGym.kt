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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.ui.components.Chevron
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.practice.LineLearningMode
import com.onetake.app.ui.theme.OT

/**
 * Inset Gym Roster: ActOnCue-inspired Line Learning & Memorization Modes
 * - First-Letter Prompts (Initial letter mnemonics)
 * - Mask & Reveal Lines (Active recall word shield)
 * - Audio Gap Rehearsal (Silent speech gaps)
 * - Hands-Free Audio Walk (Background loop)
 */
@Composable
fun LineLearningGym(
    onSelectMode: (LineLearningMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerShape = remember { RoundedCornerShape(24.dp) }
    val modes = remember {
        listOf(
            Triple(LineLearningMode.FIRST_LETTER, Icons.Default.TextFields, "First-Letter Prompts"),
            Triple(LineLearningMode.MASK_AND_REVEAL, Icons.Default.VisibilityOff, "Mask & Reveal Lines"),
            Triple(LineLearningMode.AUDIO_GAP, Icons.Default.RecordVoiceOver, "Audio Gap Rehearsal"),
            Triple(LineLearningMode.HANDS_FREE_WALK, Icons.Default.Headphones, "Hands-Free Audio Walk"),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Text(
            text = "LINE LEARNING MODES",
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
            modes.forEachIndexed { index, (mode, icon, _) ->
                LineLearningRow(
                    mode = mode,
                    icon = icon,
                    onClick = { onSelectMode(mode) },
                )

                if (index < modes.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color(0x14FFFFFF),
                        modifier = Modifier.padding(start = 74.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LineLearningRow(
    mode: LineLearningMode,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconShape = remember { RoundedCornerShape(14.dp) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Squircle Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(iconShape)
                    .background(Color(0x1AFFFFFF))
                    .border(0.5.dp, Color(0x22FFFFFF), iconShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = OT.colors.accent,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = mode.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mode.subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF86868B),
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Chevron(tint = Color(0xFF86868B))
    }
}
