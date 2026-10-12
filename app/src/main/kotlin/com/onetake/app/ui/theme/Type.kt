package com.onetake.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale from the Figma text styles (SF Pro on iOS → system sans / Roboto Flex on Android).
 * Sizes and weights match iOS `Font.ot*` exactly. Large titles use slight negative tracking
 * like SF Pro Display so headlines read the same weight on both platforms.
 */
object OTType {
    private val f = FontFamily.Default
    val largeTitle = TextStyle(fontFamily = f, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val title = TextStyle(fontFamily = f, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em)
    val hero = TextStyle(fontFamily = f, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val headline = TextStyle(fontFamily = f, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = f, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontFamily = f, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val subhead = TextStyle(fontFamily = f, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val footnote = TextStyle(fontFamily = f, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = f, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** Section labels: always UPPERCASE, +0.4 tracking, labelTertiary. */
    val eyebrow = TextStyle(fontFamily = f, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.03.em)
    /** Big numbers (hours left, counters). Use with `fontFeatureSettings = "tnum"` already applied. */
    val number = TextStyle(fontFamily = f, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
}

/** Material slots mapped onto the same scale so stock components (Switch labels, dialogs) match. */
val MaterialTypography = Typography(
    displaySmall = OTType.largeTitle,
    headlineLarge = OTType.largeTitle,
    headlineMedium = OTType.title,
    headlineSmall = OTType.title.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = OTType.headline.copy(fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = OTType.headline,
    titleSmall = OTType.subhead,
    bodyLarge = OTType.body,
    bodyMedium = OTType.callout,
    bodySmall = OTType.footnote,
    labelLarge = OTType.headline.copy(fontSize = 15.sp),
    labelMedium = OTType.footnote.copy(fontWeight = FontWeight.Medium),
    labelSmall = OTType.caption.copy(fontWeight = FontWeight.Medium),
)
