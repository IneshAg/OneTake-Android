package com.onetake.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.onetake.app.ui.theme.OT

object SpotlightStageGeometry {
    const val STAGE_LIGHT_BAR_WIDTH_DP = 46f
    const val STAGE_LIGHT_BAR_HEIGHT_DP = 5f
    const val PRIMARY_CTA_DIAMETER_DP = 68f
    const val PRIMARY_CTA_HALO_WIDTH_DP = 10f
    const val HAIRLINE_WIDTH_DP = 0.5f
    const val LIT_STAGE_WEIGHT = 0.68f
    const val IN_THE_WINGS_WEIGHT = 0.32f
}

@Composable
fun StageLightBar(modifier: Modifier = Modifier, isPulsing: Boolean = false) {
    val alphaAnim = if (isPulsing) {
        val infinite = rememberInfiniteTransition(label = "stagePulse")
        val a by infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pulseAlpha"
        )
        a
    } else 1f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glowing radiant halo behind the light bar
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(18.dp)
                .graphicsLayer { alpha = alphaAnim }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF6DF).copy(alpha = 0.45f),
                            Color(0xFFE9D2A8).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Luminous core light bar
        Box(
            modifier = Modifier
                .width(SpotlightStageGeometry.STAGE_LIGHT_BAR_WIDTH_DP.dp)
                .height(SpotlightStageGeometry.STAGE_LIGHT_BAR_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFE9D2A8),
                            Color(0xFFFFFDF7),
                            Color(0xFFE9D2A8)
                        )
                    )
                )
        )
    }
}

@Composable
fun PrimaryStageCta(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    caption: String? = null,
    isRecord: Boolean = false,
    onClick: UnitFunction = {},
) {
    val ctaColor = if (isRecord) OT.colors.recRed else OT.colors.stageLight
    val iconColor = if (isRecord) Color.White else Color(0xFF050505)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size((SpotlightStageGeometry.PRIMARY_CTA_DIAMETER_DP + SpotlightStageGeometry.PRIMARY_CTA_HALO_WIDTH_DP * 2).dp),
            contentAlignment = Alignment.Center
        ) {
            // Radiant Glowing Halo ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ctaColor.copy(alpha = 0.35f),
                                ctaColor.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Button core with luminous specular rim
            Box(
                modifier = Modifier
                    .size(SpotlightStageGeometry.PRIMARY_CTA_DIAMETER_DP.dp)
                    .clip(CircleShape)
                    .background(ctaColor)
                    .border(1.dp, Color.White.copy(alpha = 0.40f), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = caption, tint = iconColor, modifier = Modifier.size(26.dp))
            }
        }
        if (!caption.isNullOrBlank()) {
            Text(text = caption, style = OT.type.stageOverline, color = OT.colors.accentGold)
        }
    }
}

private typealias UnitFunction = () -> Unit

@Composable
fun SpotlightStageScaffold(
    modifier: Modifier = Modifier,
    topCaption: String? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    topTrailingContent: (@Composable () -> Unit)? = null,
    wingsContent: (@Composable ColumnScope.() -> Unit)? = null,
    showStageLight: Boolean = true,
    isStageLightPulsing: Boolean = false,
    stageContent: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OT.colors.bgCanvas)
            .statusBarsPadding()
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF2A2723), CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OT.colors.labelSecondary, modifier = Modifier.size(18.dp))
                    }
                } else if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF2A2723), CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = OT.colors.labelSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Center Column: accommodates StageLightBar, TopCaption, or both
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showStageLight) {
                    StageLightBar(isPulsing = isStageLightPulsing)
                }
                if (topCaption != null) {
                    Text(text = topCaption, style = OT.type.navCaption, color = OT.colors.labelSecondary)
                }
            }

            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                topTrailingContent?.invoke()
            }
        }

        // The Lit Stage (~68%) with ambient spotlight glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(SpotlightStageGeometry.LIT_STAGE_WEIGHT),
        ) {
            if (showStageLight) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFF8EB).copy(alpha = 0.12f),
                                    Color(0xFFE9D2A8).copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = stageContent
            )
        }

        // Hairline Divider & In the Wings (~32%)
        if (wingsContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SpotlightStageGeometry.HAIRLINE_WIDTH_DP.dp)
                    .background(OT.colors.borderMuted.copy(alpha = 0.5f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(SpotlightStageGeometry.IN_THE_WINGS_WEIGHT)
                    .background(OT.colors.surfaceStage)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                content = wingsContent
            )
        }
    }
}
