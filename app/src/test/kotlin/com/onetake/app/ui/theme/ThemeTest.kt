package com.onetake.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun testOledVelvetThemeHasPitchBlackCanvasAndLavenderAccent() {
        val theme = OledVelvetOT
        assertEquals(Color(0xFF000000), theme.bgCanvas)
        assertEquals(Color(0xFFC8A8E0), theme.accent)
        assertTrue(theme.isDark)
    }

    @Test
    fun testOledObsidianThemeHasPitchBlackCanvasAndWhiteAccent() {
        val theme = OledObsidianOT
        assertEquals(Color(0xFF000000), theme.bgCanvas)
        assertEquals(Color(0xFFFFFFFF), theme.accent)
        assertTrue(theme.isDark)
    }

    @Test
    fun testStudioFinishEnumValues() {
        assertEquals(listOf(StudioFinish.VELVET, StudioFinish.OBSIDIAN), StudioFinish.values().toList())
    }
}
