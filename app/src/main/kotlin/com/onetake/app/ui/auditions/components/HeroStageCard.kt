package com.onetake.app.ui.auditions.components

import com.onetake.app.models.AuditionStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.onetake.app.models.otRelative
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.theme.OT

/**
 * System 2: Exactly ONE Hero Stage Card
 * 290dp height, 30dp continuous G2 squircle corner geometry,
 * full-bleed cinematic imagery, bottom gradient scrim, and pure typographic hierarchy.
 */
@Composable
fun HeroStageCard(
    slate: Audition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = remember { RoundedCornerShape(30.dp) }
    val dueText = slate.due.otRelative

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
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
                contentDescription = "Up next audition: ${slate.role} in ${slate.project}, $dueText"
            },
    ) {
        // Fallback or Image Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF221E2A),
                            Color(0xFF0F0D14),
                            Color(0xFF000000),
                        )
                    )
                )
        )

        // Gradient Scrim for maximum typographic contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.25f to Color.Transparent,
                        0.75f to Color.Black.copy(alpha = 0.82f),
                        1.0f to Color.Black,
                    )
                )
        )

        // Monolithic Typographic Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp)
        ) {
            Text(
                text = if (slate.status == AuditionStatus.Sent) "SENT" else "UP NEXT · IMMEDIATE FOCUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
                color = OT.colors.accent,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = slate.role,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.03).sp,
                color = Color.White,
                lineHeight = 36.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${slate.project} · $dueText",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFA1A1A6),
            )
        }
    }
}
