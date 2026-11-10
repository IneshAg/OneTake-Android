package com.onetake.app.ui.components.navbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppTab
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.GlassSpring
import com.onetake.app.ui.components.GlassSpringDp
import com.onetake.app.ui.components.SQUASH
import com.onetake.app.ui.components.STRETCH
import com.onetake.app.ui.components.SFSymbols
import com.onetake.app.ui.components.floatingBarInsets
import com.onetake.app.ui.components.liquidGlass
import com.onetake.app.ui.haptics.Haptic
import com.onetake.app.ui.haptics.rememberHaptics
import kotlin.math.abs

internal data class NavTabItem(val tab: AppTab, val icon: ImageVector)

internal val primaryTabs = listOf(
    NavTabItem(AppTab.Auditions, SFSymbols.MovieClapperFill),
    NavTabItem(AppTab.Practice, SFSymbols.TheaterMasksFill),
)

object LiquidGlassNavBarDefaults {
    fun isSearchAvailable(tab: AppTab, hasAuditions: Boolean, hasPractice: Boolean): Boolean {
        return when (tab) {
            AppTab.Auditions -> hasAuditions
            AppTab.Practice -> hasPractice
            AppTab.Search -> true
        }
    }
}

/**
 * ColorOS 17 Inspired Ultra-Fluid Liquid Glass Navigation Bar:
 * - When Search is available: 2-tab pill docked on the bottom-left, detached circular Search orb on bottom-right.
 * - When Search is absent (e.g. empty practice/audition): 2-tab pill smoothly centers horizontally.
 * - Dark smoky translucent acrylic glass with specular highlights.
 * - Active tab features signature ColorOS 17 white capsule behind icon with dark glyph and pure white label.
 * - Floating action orb in soft pastel periwinkle with dark search glyph.
 */
@Composable
fun LiquidGlassNavBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    isSearchAvailable: Boolean = true,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val circleShape = remember { CircleShape }
    val haptics = rememberHaptics()

    val barBg = Color(0xF218181B) // ColorOS 17 dark smoky acrylic/glass
    val edgeColor = Color(0x2EFFFFFF) // 0.5dp fine specular highlight

    val primaryIndex = primaryTabs.indexOfFirst { it.tab == selected }
    val isSearchSelected = selected == AppTab.Search
    val hasPrimarySelection = primaryIndex >= 0

    val currentOnSelect by rememberUpdatedState(onSelect)

    var lastValidIndex by remember { mutableIntStateOf(if (primaryIndex >= 0) primaryIndex else 0) }

    // Spring-driven continuous tab position (0f .. 1f)
    val positionAnim = remember { Animatable(lastValidIndex.toFloat()) }

    LaunchedEffect(primaryIndex) {
        if (primaryIndex >= 0) {
            lastValidIndex = primaryIndex
            if (positionAnim.targetValue != primaryIndex.toFloat()) {
                positionAnim.animateTo(
                    targetValue = primaryIndex.toFloat(),
                    animationSpec = GlassSpring,
                )
            }
        }
    }

    // Dynamic stretch and squash derived from spring velocity
    val currentVelocity = positionAnim.velocity
    val speed = abs(currentVelocity)
    val stretch = (speed * 0.024f).coerceIn(0f, STRETCH)
    val squash = stretch * SQUASH

    val pillWidth = 204.dp
    val pillHeight = 58.dp
    val orbSize = 56.dp

    // Smooth coordination between Left-docked and Centered positions
    val orbAlpha by animateFloatAsState(
        targetValue = if (isSearchAvailable) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "orbAlpha"
    )
    val orbScale by animateFloatAsState(
        targetValue = if (isSearchAvailable) 1f else 0.4f,
        animationSpec = GlassSpring,
        label = "orbScale"
    )
    val orbTranslateX by animateDpAsState(
        targetValue = if (isSearchAvailable) 0.dp else 40.dp,
        animationSpec = GlassSpringDp,
        label = "orbTranslateX"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .height(pillHeight),
    ) {
        val totalWidth = maxWidth
        val targetPillOffset = if (isSearchAvailable) {
            0.dp
        } else {
            (totalWidth - pillWidth) / 2
        }

        val pillOffsetX by animateDpAsState(
            targetValue = targetPillOffset,
            animationSpec = GlassSpringDp,
            label = "pillOffsetX"
        )

        // ── 1. ColorOS 17 Grouped 2-Tab Pill ──────────────────────────────────────
        Box(
            modifier = Modifier
                .offset(x = pillOffsetX)
                .width(pillWidth)
                .height(pillHeight)
                .clip(pillShape)
                .liquidGlass(pillShape, fallbackColor = barBg)
                .border(GLASS_EDGE_WIDTH, edgeColor, pillShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            val innerTabWidth = (pillWidth - 12.dp) / 2
            val indicatorWidth = 54.dp
            val indicatorHeight = 28.dp

            val currentPos = positionAnim.value
            val indicatorOffsetX = innerTabWidth * currentPos + (innerTabWidth - indicatorWidth) / 2

            // Active tab white pill capsule behind the icon (ColorOS 17 signature)
            if (hasPrimarySelection) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffsetX, y = 1.dp)
                        .width(indicatorWidth * (1f + stretch))
                        .height(indicatorHeight * (1f - squash))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                )
            }

            // Interactive Tab Items (Icon + Label)
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primaryTabs.forEachIndexed { index, item ->
                    val isSel = hasPrimarySelection && index == (if (positionAnim.value < 0.5f) 0 else 1)
                    val iconTint by animateColorAsState(
                        targetValue = if (isSel) Color(0xFF141416) else Color(0xFF8E8E95),
                        animationSpec = tween(160),
                        label = "tabIconTint"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (isSel) Color.White else Color(0xFF8E8E95),
                        animationSpec = tween(160),
                        label = "tabLabelColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) {
                                if (primaryIndex != index) {
                                    haptics.play(Haptic.Select)
                                    currentOnSelect(item.tab)
                                }
                            }
                            .semantics {
                                contentDescription = item.tab.label
                                this.selected = isSel
                                this.role = Role.Tab
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.tab.label,
                            color = labelColor,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ── 2. ColorOS 17 Floating Action Orb (Right Edge with Glowing Aura) ──
        if (orbAlpha > 0.01f) {
            val orbBg = Color(0xFFB4BCE8) // ColorOS 17 signature light pastel periwinkle
            val orbIconTint = Color(0xFF141416)

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(orbSize + 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Soft Radiant Glowing Aura
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = orbTranslateX.toPx()
                            scaleX = orbScale
                            scaleY = orbScale
                            alpha = orbAlpha
                        }
                        .clip(circleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFC7CFF8).copy(alpha = 0.50f),
                                    Color(0xFFB4BCE8).copy(alpha = 0.18f),
                                    Color.Transparent,
                                )
                            )
                        )
                )

                // Orb Core
                Box(
                    modifier = Modifier
                        .size(orbSize)
                        .graphicsLayer {
                            translationX = orbTranslateX.toPx()
                            scaleX = orbScale
                            scaleY = orbScale
                            alpha = orbAlpha
                        }
                        .clip(circleShape)
                        .background(orbBg)
                        .border(1.dp, Color.White.copy(alpha = 0.50f), circleShape)
                        .clickable(
                            enabled = isSearchAvailable,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            haptics.play(Haptic.Select)
                            currentOnSelect(AppTab.Search)
                        }
                        .semantics {
                            contentDescription = "Search"
                            this.selected = isSearchSelected
                            this.role = Role.Tab
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = orbIconTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
