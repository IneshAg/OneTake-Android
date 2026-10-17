package com.onetake.app.ui.components.navbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetake.app.AppTab
import com.onetake.app.ui.components.GLASS_EDGE_COLOR
import com.onetake.app.ui.components.GLASS_EDGE_WIDTH
import com.onetake.app.ui.components.GlassSpring
import com.onetake.app.ui.components.LocalLiquidGlassEnabled
import com.onetake.app.ui.components.PILL_INSET
import com.onetake.app.ui.components.SQUASH
import com.onetake.app.ui.components.STRETCH
import com.onetake.app.ui.components.TAB_ICON_LABEL_GAP
import com.onetake.app.ui.components.floatingBarInsets
import com.onetake.app.ui.components.glassContentColor
import com.onetake.app.ui.components.glassIndicatorColor
import com.onetake.app.ui.components.isGlassSupported
import com.onetake.app.ui.components.liquidGlass
import com.onetake.app.ui.haptics.Haptic
import com.onetake.app.ui.haptics.rememberHaptics
import com.onetake.app.ui.theme.OT
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class NavTabItem(val tab: AppTab, val icon: ImageVector)

internal val primaryTabs = listOf(
    NavTabItem(AppTab.Auditions, Icons.Filled.Movie),
    NavTabItem(AppTab.Practice, Icons.Filled.TheaterComedy),
)

private fun Modifier.pillPlacement(center: () -> Offset, size: () -> Size): Modifier =
    layout { measurable, constraints ->
        val s = size()
        val w = s.width.roundToInt().coerceAtLeast(1)
        val h = s.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.minWidth, constraints.minHeight) {
            val c = center()
            placeable.place((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt())
        }
    }

@Composable
private fun LiquidSelectionPill(
    color: Color,
    useGlass: Boolean,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    Box(
        modifier = modifier
            .clip(pillShape)
            .background(
                if (useGlass) {
                    color.copy(alpha = (0.16f + 0.10f * motionIntensity).coerceAtMost(0.35f))
                } else {
                    color
                }
            )
            .then(
                if (useGlass) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = (0.12f + 0.10f * motionIntensity).coerceAtMost(0.30f)),
                                    Color.Transparent,
                                )
                            )
                        )
                        .border(
                            width = 0.75.dp,
                            color = Color.White.copy(alpha = (0.18f + 0.14f * motionIntensity).coerceAtMost(0.40f)),
                            shape = pillShape,
                        )
                } else {
                    Modifier
                }
            )
    )
}

/**
 * Ultra-Fluid Liquid Glass Navigation Bar:
 * - Grouped pill holding the primary 4 tabs on the left.
 * - Standalone circular Search button on the right.
 * - Real-time backdrop blur and specular highlights via liquidGlass.
 * - Compose-native spring with organic liquid stretch & squash and zero latency.
 * - Single persistent selection surface: zero pop or visual hitch on landing.
 * - Smooth alpha/scale bloom between primary tabs and standalone Search button.
 */
@Composable
fun LiquidGlassNavBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val circleShape = remember { CircleShape }
    val c = OT.colors
    val haptics = rememberHaptics()
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val coroutineScope = rememberCoroutineScope()

    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val barBg = if (c.isDark) c.surfaceRaised else c.surfaceCard
    val contentColor = if (useGlass) glassContentColor() else c.labelPrimary
    val indicatorColor = if (useGlass) glassIndicatorColor().copy(alpha = 0.16f) else c.fillField

    val primaryIndex = primaryTabs.indexOfFirst { it.tab == selected }
    val isSearchSelected = selected == AppTab.Search
    val hasPrimarySelection = primaryIndex >= 0

    val currentOnSelect by rememberUpdatedState(onSelect)

    var lastValidIndex by remember { mutableIntStateOf(if (primaryIndex >= 0) primaryIndex else 0) }

    // Spring-driven continuous tab position (in tab-unit float coords, e.g. 0.0f .. 3.0f)
    val positionAnim = remember { Animatable(lastValidIndex.toFloat()) }

    // Smooth coordinated transitions when entering / exiting to standalone Search
    val pillAlpha by animateFloatAsState(
        targetValue = if (hasPrimarySelection) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "pillAlpha",
    )
    val pillScale by animateFloatAsState(
        targetValue = if (hasPrimarySelection) 1f else 0.82f,
        animationSpec = GlassSpring,
        label = "pillScale",
    )

    // Synchronize programmatic tab changes (e.g. deep link or back)
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

    // Dynamic stretch, squash, and motion intensity derived mathematically from spring velocity
    val currentVelocity = positionAnim.velocity
    val speed = abs(currentVelocity)
    val stretch = (speed * 0.024f).coerceIn(0f, STRETCH)
    val squash = stretch * SQUASH
    val motionIntensity = (speed * 0.12f).coerceIn(0f, 1f)

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val tabCount = primaryTabs.size
    val tabWidthPx = if (rowSize.width > 0 && tabCount > 0) rowSize.width.toFloat() / tabCount else 0f
    val rowHeightPx = rowSize.height.toFloat()

    val pillWidthPx = tabWidthPx * (1f + stretch) * pillScale
    val pillHeightPx = rowHeightPx * (1f - squash) * pillScale

    val currentPos = positionAnim.value
    val pillCenterXPx = if (isRtl) {
        rowSize.width - (currentPos + 0.5f) * tabWidthPx
    } else {
        (currentPos + 0.5f) * tabWidthPx
    }
    val pillCenterYPx = rowHeightPx / 2f

    var lastHapticTab by remember { mutableIntStateOf(lastValidIndex) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(floatingBarInsets)
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 1. Grouped Primary Tabs Pill ──────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(pillShape)
                    .liquidGlass(pillShape, fallbackColor = barBg)
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, pillShape)
                    .padding(PILL_INSET),
            ) {
                // Persistent fluid selection indicator (middle layer, behind icons)
                if (pillAlpha > 0f && tabWidthPx > 0f && rowHeightPx > 0f) {
                    LiquidSelectionPill(
                        color = indicatorColor,
                        useGlass = useGlass,
                        motionIntensity = motionIntensity,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = pillAlpha
                            }
                            .pillPlacement(
                                center = { Offset(pillCenterXPx, pillCenterYPx) },
                                size = { Size(pillWidthPx, pillHeightPx) },
                            ),
                    )
                }

                // Interactive tab items (top layer)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { rowSize = it }
                        .pointerInput(tabCount, tabWidthPx, isRtl) {
                            if (tabWidthPx <= 0f) return@pointerInput
                            val direction = if (isRtl) -1f else 1f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    coroutineScope.launch { positionAnim.stop() }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        val target = if (primaryIndex >= 0) primaryIndex else lastValidIndex
                                        positionAnim.animateTo(target.toFloat(), GlassSpring)
                                    }
                                },
                                onDragEnd = {
                                    val nearestTab = positionAnim.value.roundToInt().coerceIn(0, primaryTabs.lastIndex)
                                    coroutineScope.launch {
                                        positionAnim.animateTo(nearestTab.toFloat(), GlassSpring)
                                    }
                                    if (primaryTabs[nearestTab].tab != selected) {
                                        haptics.play(Haptic.Select)
                                        currentOnSelect(primaryTabs[nearestTab].tab)
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val deltaTabs = (dragAmount * direction) / tabWidthPx
                                    val newPos = (positionAnim.value + deltaTabs).coerceIn(-0.15f, primaryTabs.lastIndex + 0.15f)
                                    coroutineScope.launch { positionAnim.snapTo(newPos) }
                                    val approxTab = newPos.roundToInt().coerceIn(0, primaryTabs.lastIndex)
                                    if (approxTab != lastHapticTab) {
                                        haptics.play(Haptic.Tick)
                                        lastHapticTab = approxTab
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    primaryTabs.forEachIndexed { index, item ->
                        val isSel = item.tab == selected
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSel) 1.08f else 1f,
                            animationSpec = GlassSpring,
                            label = "tabScale_${item.tab.name}",
                        )
                        val tint by animateColorAsState(
                            targetValue = when {
                                isSel -> c.accent
                                useGlass -> contentColor.copy(alpha = 0.75f)
                                else -> c.labelSecondary
                            },
                            animationSpec = tween(150),
                            label = "tabTint_${item.tab.name}",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pillShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                ) {
                                    if (!isSel) {
                                        haptics.play(Haptic.Select)
                                        lastValidIndex = index
                                        // Zero-latency launch on touch
                                        coroutineScope.launch {
                                            positionAnim.animateTo(
                                                targetValue = index.toFloat(),
                                                animationSpec = GlassSpring,
                                            )
                                        }
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
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Spacer(Modifier.height(TAB_ICON_LABEL_GAP))
                            Text(
                                text = item.tab.label,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Standalone Search Circular Button ──────────────────────────────
        val searchScale by animateFloatAsState(
            targetValue = if (isSearchSelected) 1.08f else 1f,
            animationSpec = GlassSpring,
            label = "searchScale",
        )
        val searchTint by animateColorAsState(
            targetValue = when {
                isSearchSelected -> c.accent
                useGlass -> contentColor.copy(alpha = 0.85f)
                else -> c.labelSecondary
            },
            animationSpec = tween(180),
            label = "searchTint",
        )
        val searchBgAlpha by animateFloatAsState(
            targetValue = if (isSearchSelected) 1f else 0f,
            animationSpec = tween(180),
            label = "searchBgAlpha",
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(circleShape)
                .liquidGlass(circleShape, fallbackColor = if (isSearchSelected) c.fillField else barBg)
                .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, circleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                ) {
                    if (!isSearchSelected) {
                        haptics.play(Haptic.Select)
                        currentOnSelect(AppTab.Search)
                    }
                }
                .semantics {
                    contentDescription = "Search"
                    this.selected = isSearchSelected
                    this.role = Role.Tab
                },
            contentAlignment = Alignment.Center,
        ) {
            if (searchBgAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .graphicsLayer { alpha = searchBgAlpha }
                        .clip(circleShape)
                        .background(indicatorColor)
                        .then(
                            if (useGlass) {
                                Modifier.border(0.75.dp, Color.White.copy(alpha = 0.20f), circleShape)
                            } else {
                                Modifier
                            }
                        ),
                )
            }
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = searchTint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = searchScale
                        scaleY = searchScale
                    },
            )
        }
    }
}
