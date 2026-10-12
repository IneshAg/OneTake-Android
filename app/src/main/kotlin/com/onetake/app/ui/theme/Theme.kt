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
import androidx.core.view.WindowCompat

@Immutable
data class OneTakeColors(
    val bgCanvas: Color,
    val surfaceCard: Color,
    val surfaceSunken: Color,
    val surfaceRaised: Color,
    val separator: Color,
    val scrim: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelOnAccent: Color,
    val accent: Color,
    val accentSoft: Color,
    val due: Color,
    val dueTrack: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val reader: Color,
    val cameraBg: Color = CameraBg,
    val cameraGlass: Color = CameraGlass,
    val cameraRecRed: Color = CameraRecRed,
    val cameraLabel: Color = CameraLabel,
    val cameraLabelSecondary: Color = CameraLabelSecondary,
    val isDark: Boolean
)

val LightOneTakeColors = OneTakeColors(
    bgCanvas = BgCanvasLight,
    surfaceCard = SurfaceCardLight,
    surfaceSunken = SurfaceSunkenLight,
    surfaceRaised = SurfaceRaisedLight,
    separator = SeparatorLight,
    scrim = ScrimLight,
    labelPrimary = LabelPrimaryLight,
    labelSecondary = LabelSecondaryLight,
    labelTertiary = LabelTertiaryLight,
    labelOnAccent = LabelOnAccentLight,
    accent = AccentLight,
    accentSoft = AccentSoftLight,
    due = DueLight,
    dueTrack = DueTrackLight,
    success = SuccessLight,
    danger = DangerLight,
    warning = WarningLight,
    reader = ReaderLight,
    isDark = false
)

val DarkOneTakeColors = OneTakeColors(
    bgCanvas = BgCanvasDark,
    surfaceCard = SurfaceCardDark,
    surfaceSunken = SurfaceSunkenDark,
    surfaceRaised = SurfaceRaisedDark,
    separator = SeparatorDark,
    scrim = ScrimDark,
    labelPrimary = LabelPrimaryDark,
    labelSecondary = LabelSecondaryDark,
    labelTertiary = LabelTertiaryDark,
    labelOnAccent = LabelOnAccentDark,
    accent = AccentDark,
    accentSoft = AccentSoftDark,
    due = DueDark,
    dueTrack = DueTrackDark,
    success = SuccessDark,
    danger = DangerDark,
    warning = WarningDark,
    reader = ReaderDark,
    isDark = true
)

val LocalOneTakeColors = staticCompositionLocalOf { LightOneTakeColors }

object OneTakeTheme {
    val colors: OneTakeColors
        @Composable
        get() = LocalOneTakeColors.current
}

private val LightMaterialColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = LabelOnAccentLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentLight,
    background = BgCanvasLight,
    onBackground = LabelPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = LabelPrimaryLight
)

private val DarkMaterialColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = LabelOnAccentDark,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
    background = BgCanvasDark,
    onBackground = LabelPrimaryDark,
    surface = SurfaceCardDark,
    onSurface = LabelPrimaryDark
)

@Composable
fun OneTakeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkOneTakeColors else LightOneTakeColors
    val materialScheme = if (darkTheme) DarkMaterialColorScheme else LightMaterialColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalOneTakeColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content
        )
    }
}
