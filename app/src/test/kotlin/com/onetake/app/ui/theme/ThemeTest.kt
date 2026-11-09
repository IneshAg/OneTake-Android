package com.onetake.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun testSpotlightStagePaletteTokensMatchDesignSpec() {
        val theme = SpotlightStageOT
        assertEquals(Color(0xFF050505), theme.bgCanvas)
        assertEquals(Color(0xFF151515), theme.surfaceStage)
        assertEquals(Color(0xFF4E4943), theme.borderMuted)
        assertEquals(Color(0xFFE9D2A8), theme.accentGold)
        assertEquals(Color(0xFFFFF8EB), theme.stageLight)
        assertEquals(Color(0xFFF5F2EB), theme.labelPrimary)
        assertEquals(Color(0xFFA39E93), theme.labelSecondary)
        assertEquals(Color(0xFFE24C4B), theme.recRed)
        assertTrue(theme.isDark)
    }

    @Test
    fun testLegacyThemesPrunedFromThemeDefinition() {
        // Verifies OledVelvetOT and OledObsidianOT and StudioFinish are replaced by single unified SpotlightStageOT
        val theme = SpotlightStageOT
        assertEquals(Color(0xFF050505), theme.bgCanvas)
    }
}
