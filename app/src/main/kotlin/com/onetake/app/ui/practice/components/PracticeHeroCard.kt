package com.onetake.app.ui.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

/**
 * System 2 Practice Monolith: Exactly ONE Hero Stage Card
 * Height 285dp, 30dp continuous G2 squircle corner geometry,
 * full-bleed cinematic imagery, bottom gradient scrim, and pure typographic hierarchy.
 *
 * Dedicated to the active Scene Partner (ActOnCue "On Cue" perform mode).
 */
@Composable
fun PracticeHeroCard(
    title: String,
    subtitle: String,
    eyebrow: String = "SCENE PARTNER · ON CUE",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = remember { RoundedCornerShape(30.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(285.dp)
            .clip(cardShape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, cardShape)
            .background(OT.colors.surfaceCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Active scene partner: $title, $subtitle"
            },
    ) {
        // Fallback or Image Backdrop (Stage Lighting Scrim)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF261838),
                            Color(0xFF140D1E),
                            Color(0xFF000000),
                        )
                    )
                )
        )

        // Gradient Scrim for maximum typographic clarity
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.25f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.82f),
                        1.0f to Color.Black,
                    )
                )
        )

        // Monolithic Typographic Content & Action Trigger
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp)
        ) {
            Text(
                text = eyebrow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                color = OT.colors.accent,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.03).sp,
                color = Color.White,
                lineHeight = 36.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFA1A1A6),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Play Badge Pill
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x26FFFFFF))
                    .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Start Scene Partner",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}
