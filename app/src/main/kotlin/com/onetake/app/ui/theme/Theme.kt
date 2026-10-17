package com.onetake.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

/**
 * Velvet tokens — names mirror the Figma variable collection ("bg/canvas" → bgCanvas)
 * and the iOS `OT` enum, so a value in Figma maps 1:1 to code on both platforms.
 */
@Immutable
data class OTColors(
    val bgCanvas: Color,
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
    /** Aubergine — the only accent colour. */
    val accent: Color,
    val accentSoft: Color,
    /** Amber means TIME only (countdowns, due dates). */
    val due: Color,
    val dueTrack: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val reader: Color,
    val scriptHighlight: Color,
    val scriptOnHighlight: Color,
    val isDark: Boolean,
)

private fun hex(v: Long, alpha: Float = 1f) = Color(0xFF000000 or v).copy(alpha = alpha)

val LightOT = OTColors(
    bgCanvas = hex(0xF6F4F7),
    surfaceCard = hex(0xFFFFFF),
    surfaceSunken = hex(0xEFEBF1),
    surfaceRaised = hex(0xFFFFFF),
    fillField = hex(0x1E1A22, 0.07f),
    fillQuiet = hex(0x1E1A22, 0.06f),
    separator = hex(0xE3DDE7),
    scrim = hex(0x120E15, 0.34f),
    labelPrimary = hex(0x1E1A22),
    labelSecondary = hex(0x5A5262),
    labelTertiary = hex(0x736A7B),
    labelOnAccent = hex(0xFFFFFF),
    accent = hex(0x5B3E73),
    accentSoft = hex(0x5B3E73, 0.10f),
    due = hex(0x8E6210),
    dueTrack = hex(0x8E6210, 0.12f),
    success = hex(0x3C7A5E),
    danger = hex(0xC2364A),
    warning = hex(0x8E6210),
    reader = hex(0x3C7A86),
    scriptHighlight = hex(0xF2E8D5),
    scriptOnHighlight = hex(0x1E1A22),
    isDark = false,
)

val DarkOT = OTColors(
    bgCanvas = hex(0x121014),
    surfaceCard = hex(0x1B181E),
    surfaceSunken = hex(0x252128),
    surfaceRaised = hex(0x252128),
    fillField = hex(0xF3EEF5, 0.10f),
    fillQuiet = hex(0xF3EEF5, 0.09f),
    separator = hex(0x322C37),
    scrim = hex(0x120E15, 0.6f),
    labelPrimary = hex(0xF3EEF5),
    labelSecondary = hex(0xB8AEBF),
    labelTertiary = hex(0x8A8092),
    labelOnAccent = hex(0x24152F),
    accent = hex(0xC8A8E0),
    accentSoft = hex(0xC8A8E0, 0.18f),
    due = hex(0xE2BE7A),
    dueTrack = hex(0xE2BE7A, 0.18f),
    success = hex(0x82C9A4),
    danger = hex(0xF2788A),
    warning = hex(0xE2BE7A),
    reader = hex(0x7DC0CB),
    scriptHighlight = hex(0x3A3122),
    scriptOnHighlight = hex(0xF7EEDC),
    isDark = true,
)

/** Studio appearance finishes for dark OLED mode. */
enum class StudioFinish {
    VELVET,
    OBSIDIAN,
}

val OledVelvetOT = DarkOT.copy(
    bgCanvas = Color(0xFF000000),
    surfaceCard = Color(0xFF0D0B10),
    surfaceSunken = Color(0xFF141118),
    surfaceRaised = Color(0xFF17131B),
    accent = Color(0xFFC8A8E0),
    accentSoft = Color(0x33C8A8E0),
    isDark = true,
)

val OledObsidianOT = DarkOT.copy(
    bgCanvas = Color(0xFF000000),
    surfaceCard = Color(0xFF0D0D10),
    surfaceSunken = Color(0xFF141416),
    surfaceRaised = Color(0xFF1A1A1E),
    accent = Color(0xFFFFFFFF),
    accentSoft = Color(0x33FFFFFF),
    labelSecondary = Color(0xFF86868B),
    due = Color(0xFFFFFFFF),
    isDark = true,
)

/** Camera screens are always dark, whatever the system theme. */
object CameraColors {
    val bg = hex(0x0C0B0D)
    val glass = hex(0x2A2730, 0.55f)
    val label = Color.White
    val label2 = Color.White.copy(alpha = 0.68f)
    val stroke = Color.White.copy(alpha = 0.18f)
    val recRed = hex(0xE0475A)
}

/** Layout constants (same numbers as iOS `OT`). */
object OTDims {
    val gutter = 16.dp
    val cardRadius = 24.dp
    val rowHeight = 66.dp
    val minTap = 48.dp // Android minimum touch target (iOS uses 44)
}

val LocalOT = staticCompositionLocalOf { LightOT }
val LocalStudioFinish = staticCompositionLocalOf { StudioFinish.VELVET }

/** Access tokens anywhere: `OT.colors.accent`, `OT.type.headline`. */
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    studioFinish: StudioFinish = LocalStudioFinish.current,
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) {
        when (studioFinish) {
            StudioFinish.VELVET -> OledVelvetOT
            StudioFinish.OBSIDIAN -> OledObsidianOT
        }
    } else {
        LightOT
    }
    val scheme = if (darkTheme) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
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
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !darkTheme
            ctl.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalOT provides c,
        LocalStudioFinish provides studioFinish,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTypography, content = content)
    }
}
