package com.onetake.app.ui.theme

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeTest {
    @Test
    fun testSpotlightStageTypographyScaleDefinitions() {
        assertEquals(44.sp, OTType.heroStageTitle.fontSize)
        assertEquals(48.sp, OTType.heroStageTitle.lineHeight)
        assertEquals(FontWeight.SemiBold, OTType.heroStageTitle.fontWeight)
        assertEquals(FontStyle.Italic, OTType.heroStageTitle.fontStyle)

        assertEquals(52.sp, OTType.curtainHeroTitle.fontSize)
        assertEquals(56.sp, OTType.curtainHeroTitle.lineHeight)

        assertEquals(28.sp, OTType.activeDialogue.fontSize)
        assertEquals(36.sp, OTType.activeDialogue.lineHeight)

        assertEquals(20.sp, OTType.dimmedCue.fontSize)
        assertEquals(FontStyle.Italic, OTType.dimmedCue.fontStyle)

        assertEquals(11.sp, OTType.stageOverline.fontSize)
        assertEquals(FontWeight.SemiBold, OTType.stageOverline.fontWeight)
        assertEquals(0.18.em, OTType.stageOverline.letterSpacing)

        assertEquals(11.sp, OTType.navCaption.fontSize)
        assertEquals(0.16.em, OTType.navCaption.letterSpacing)

        assertEquals(14.sp, OTType.actionPill.fontSize)
        assertEquals(FontWeight.Medium, OTType.actionPill.fontWeight)
    }
}
