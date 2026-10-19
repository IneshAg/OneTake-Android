package com.onetake.app.ui.components.navbar

import com.onetake.app.AppTab
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun testAppTabOnlyContainsAuditionsPracticeAndSearch() {
        val tabs = AppTab.values().map { it.name }
        assertEquals(listOf("Auditions", "Practice", "Search"), tabs)
    }

    @Test
    fun testPrimaryTabsContainsExactlyAuditionsAndPractice() {
        assertEquals(listOf(AppTab.Auditions, AppTab.Practice), primaryTabs.map { it.tab })
    }

    @Test
    fun testPrimaryTabsUseExpectedSFSymbols() {
        assertEquals("movieclapper.fill", primaryTabs[0].icon.name)
        assertEquals("theatermasks.fill", primaryTabs[1].icon.name)
    }

    @Test
    fun testSFSymbolsDefinitions() {
        assertEquals("movieclapper.fill", com.onetake.app.ui.components.SFSymbols.MovieClapperFill.name)
        assertEquals("theatermasks.fill", com.onetake.app.ui.components.SFSymbols.TheaterMasksFill.name)
        assertEquals("film", com.onetake.app.ui.components.SFSymbols.Film.name)
    }
}
