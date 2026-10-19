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
import androidx.compose.material.icons.filled.Visibility
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
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.practice.LineLearningMode
import com.onetake.app.ui.theme.OT

/**
 * Option 1: Sleek 2x2 Horizontal Drill Grid
 * Ultra-compact 68dp horizontal tiles with 38dp squircle icon on left.
 * Saves ~140dp of vertical space, placing the actor's repertoire above the fold.
 */
@Composable
fun CompactDrillGrid(
    onSelectMode: (LineLearningMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QUICK DRILLS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.sp,
            color = Color(0xFF86868B),
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
        )

        // Row 1: Partner (On-Cue) & First-Letter (Mnemonic)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrillTile(
                title = "Partner",
                sub = "On-cue voice",
                icon = Icons.Default.RecordVoiceOver,
                onClick = { onSelectMode(LineLearningMode.AUDIO_GAP) },
                modifier = Modifier.weight(1f),
            )
            DrillTile(
                title = "First-Letter",
                sub = "Mnemonic",
                icon = Icons.Default.TextFields,
                onClick = { onSelectMode(LineLearningMode.FIRST_LETTER) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Cold Read (Vanishing) & Audio Walk (Loop)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrillTile(
                title = "Cold Read",
                sub = "Vanishing lines",
                icon = Icons.Default.Visibility,
                onClick = { onSelectMode(LineLearningMode.MASK_AND_REVEAL) },
                modifier = Modifier.weight(1f),
            )
            DrillTile(
                title = "Audio Walk",
                sub = "Loop run",
                icon = Icons.Default.Headphones,
                onClick = { onSelectMode(LineLearningMode.HANDS_FREE_WALK) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DrillTile(
    title: String,
    sub: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = remember { RoundedCornerShape(18.dp) }
    val iconShape = remember { RoundedCornerShape(12.dp) }

    Row(
        modifier = modifier
            .height(68.dp)
            .clip(tileShape)
            .background(OT.colors.surfaceCard)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, tileShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Squircle Icon Badge
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(iconShape)
                .background(Color(0x1AFFFFFF))
                .border(0.5.dp, Color(0x22FFFFFF), iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OT.colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                fontSize = 12.sp,
                color = Color(0xFF86868B),
                lineHeight = 15.sp,
            )
        }
    }
}
