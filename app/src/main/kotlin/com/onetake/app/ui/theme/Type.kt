package com.onetake.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onetake.app.R

object OTType {
    val CormorantGaramond: FontFamily = try {
        FontFamily(
            Font(R.font.cormorant_garamond_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
            Font(R.font.cormorant_garamond_medium, FontWeight.Medium, FontStyle.Normal),
        )
    } catch (_: Throwable) {
        FontFamily.Serif
    }

    val HankenGrotesk: FontFamily = try {
        FontFamily(
            Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
            Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
            Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
        )
    } catch (_: Throwable) {
        FontFamily.Default
    }

    // Stage Typography
    val heroStageTitle = TextStyle(
        fontFamily = CormorantGaramond,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
    )
    val curtainHeroTitle = TextStyle(
        fontFamily = CormorantGaramond,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
    )
    val activeDialogue = TextStyle(
        fontFamily = CormorantGaramond,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Medium,
    )
    val dimmedCue = TextStyle(
        fontFamily = CormorantGaramond,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
    )
    val wingRowTitle = TextStyle(
        fontFamily = CormorantGaramond,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
    )
    val stageOverline = TextStyle(
        fontFamily = HankenGrotesk,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.18.em,
    )
    val navCaption = TextStyle(
        fontFamily = HankenGrotesk,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.16.em,
    )
    val actionPill = TextStyle(
        fontFamily = HankenGrotesk,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )

    // Baseline styles for compatibility
    val largeTitle = TextStyle(fontFamily = HankenGrotesk, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val title = TextStyle(fontFamily = HankenGrotesk, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em)
    val hero = TextStyle(fontFamily = CormorantGaramond, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    val headline = TextStyle(fontFamily = HankenGrotesk, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = HankenGrotesk, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontFamily = HankenGrotesk, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val subhead = TextStyle(fontFamily = HankenGrotesk, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val footnote = TextStyle(fontFamily = HankenGrotesk, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = HankenGrotesk, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val eyebrow = stageOverline
    val number = TextStyle(fontFamily = HankenGrotesk, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
}

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

val CormorantGaramond: FontFamily get() = OTType.CormorantGaramond
val HankenGrotesk: FontFamily get() = OTType.HankenGrotesk
