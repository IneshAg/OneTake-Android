package com.onetake.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class OTColors(
    val bgCanvas: Color,
    val surfaceStage: Color,
    val surfaceCard: Color,
    val surfaceSunken: Color,
    val surfaceRaised: Color,
    val fillField: Color,
    val fillQuiet: Color,
    val separator: Color,
    val scrim: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelOnAccent: Color,
    val accent: Color,
    val accentSoft: Color,
    val borderMuted: Color,
    val accentGold: Color,
    val stageLight: Color,
    val recRed: Color,
    val due: Color,
    val dueTrack: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val reader: Color,
    val scriptHighlight: Color,
    val scriptOnHighlight: Color,
    val isDark: Boolean = true,
)

val SpotlightStageOT = OTColors(
    bgCanvas = Color(0xFF050505),
    surfaceStage = Color(0xFF151515),
    surfaceCard = Color(0xFF151515),
    surfaceSunken = Color(0xFF0A0A0A),
    surfaceRaised = Color(0xFF1F1F1F),
    fillField = Color(0xFFF5F2EB).copy(alpha = 0.08f),
    fillQuiet = Color(0xFFF5F2EB).copy(alpha = 0.05f),
    separator = Color(0xFF4E4943),
    scrim = Color(0xFF050505).copy(alpha = 0.70f),
    labelPrimary = Color(0xFFF5F2EB),
    labelSecondary = Color(0xFFA39E93),
    labelTertiary = Color(0xFF4E4943),
    labelOnAccent = Color(0xFF050505),
    accent = Color(0xFFE9D2A8),
    accentSoft = Color(0xFFE9D2A8).copy(alpha = 0.18f),
    borderMuted = Color(0xFF4E4943),
    accentGold = Color(0xFFE9D2A8),
    stageLight = Color(0xFFFFF8EB),
    recRed = Color(0xFFE24C4B),
    due = Color(0xFFE9D2A8),
    dueTrack = Color(0xFFE9D2A8).copy(alpha = 0.15f),
    success = Color(0xFF82C9A4),
    danger = Color(0xFFE24C4B),
    warning = Color(0xFFE9D2A8),
    reader = Color(0xFF7DC0CB),
    scriptHighlight = Color(0xFF3A3122),
    scriptOnHighlight = Color(0xFFF5F2EB),
    isDark = true,
)

// Backward-compatible aliases for existing screens until pruned in Task 1.4
val DarkOT = SpotlightStageOT
val LightOT = SpotlightStageOT
val OledVelvetOT = SpotlightStageOT
val OledObsidianOT = SpotlightStageOT

enum class StudioFinish {
    VELVET,
    OBSIDIAN,
}

object CameraColors {
    val bg = Color(0xFF050505)
    val glass = Color(0xFF2A2730).copy(alpha = 0.55f)
    val label = Color.White
    val label2 = Color.White.copy(alpha = 0.68f)
    val stroke = Color.White.copy(alpha = 0.18f)
    val recRed = Color(0xFFE24C4B)
}

object OTDims {
    val gutter = 16.dp
    val cardRadius = 24.dp
    val rowHeight = 66.dp
    val minTap = 48.dp
}

val LocalOT = staticCompositionLocalOf { SpotlightStageOT }
val LocalStudioFinish = staticCompositionLocalOf { StudioFinish.VELVET }

object OT {
    val colors: OTColors
        @Composable get() = LocalOT.current
    val finish: StudioFinish
        @Composable get() = LocalStudioFinish.current
    val type = OTType
    val dims = OTDims
    val camera = CameraColors
}

@Composable
fun OneTakeTheme(
    darkTheme: Boolean = true,
    studioFinish: StudioFinish = StudioFinish.VELVET,
    content: @Composable () -> Unit,
) {
    val c = SpotlightStageOT
    val scheme = darkColorScheme(
        primary = c.accent, onPrimary = c.labelOnAccent,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
        secondary = c.accent, onSecondary = c.labelOnAccent,
        background = c.bgCanvas, onBackground = c.labelPrimary,
        surface = c.surfaceCard, onSurface = c.labelPrimary,
        surfaceVariant = c.surfaceSunken, onSurfaceVariant = c.labelSecondary,
        surfaceContainer = c.surfaceCard, surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerLow = c.bgCanvas,
        outline = c.separator, outlineVariant = c.separator,
        error = c.danger,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = false
            ctl.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(
        LocalOT provides c,
        LocalStudioFinish provides studioFinish,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTypography, content = content)
    }
}
