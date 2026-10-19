package com.onetake.app.ui.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
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
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

/**
 * Top action bar for Practice:
 * Heavy editorial headline "Practice" paired with action icons:
 * [ + Import Script / Monologue ], [ Avatar ].
 */
@Composable
fun PracticeTopBar(
    onAddClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val circleShape = remember { CircleShape }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Screen Title: Apple Bold
        Text(
            text = "Practice",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.03).sp,
            color = Color.White,
        )

        // Actions cluster
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Add / Import Script Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(circleShape)
                    .background(Color(0x1AFFFFFF))
                    .border(
                        width = GLASS_EDGE_WIDTH,
                        color = GLASS_EDGE_COLOR,
                        shape = circleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onAddClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import Script or Practice Scene",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            // 2. Avatar Profile Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(circleShape)
                    .background(Color(0x1AFFFFFF))
                    .border(
                        width = GLASS_EDGE_WIDTH,
                        color = GLASS_EDGE_COLOR,
                        shape = circleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onAvatarClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Open Profile & Studio Finishes",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
