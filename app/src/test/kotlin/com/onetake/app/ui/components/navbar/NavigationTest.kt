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

    @Test
    fun testRouteNewAuditionDefinition() {
        assertEquals("newAudition", com.onetake.app.Route.NewAudition.path)
    }

    @Test
    fun testLegacyInterstitialRoutesArePruned() {
        val routeClassNames = com.onetake.app.Route::class.sealedSubclasses.map { it.simpleName }
        org.junit.Assert.assertFalse("ShootMode must be pruned", routeClassNames.contains("ShootMode"))
        org.junit.Assert.assertFalse("ShootSetup must be pruned", routeClassNames.contains("ShootSetup"))
        org.junit.Assert.assertFalse("TapeCheck must be pruned", routeClassNames.contains("TapeCheck"))
    }

    @Test
    fun testSearchOrbAvailabilityAcrossTabs() {
        org.junit.Assert.assertFalse("Auditions tab with no auditions must hide search orb",
            LiquidGlassNavBarDefaults.isSearchAvailable(AppTab.Auditions, hasAuditions = false, hasPractice = true))
        org.junit.Assert.assertTrue("Auditions tab with active auditions must show search orb",
            LiquidGlassNavBarDefaults.isSearchAvailable(AppTab.Auditions, hasAuditions = true, hasPractice = false))
        org.junit.Assert.assertFalse("Practice tab with no practice pieces must hide search orb",
            LiquidGlassNavBarDefaults.isSearchAvailable(AppTab.Practice, hasAuditions = true, hasPractice = false))
        org.junit.Assert.assertTrue("Practice tab with practice pieces must show search orb",
            LiquidGlassNavBarDefaults.isSearchAvailable(AppTab.Practice, hasAuditions = false, hasPractice = true))
        org.junit.Assert.assertTrue("Search tab must always show search orb",
            LiquidGlassNavBarDefaults.isSearchAvailable(AppTab.Search, hasAuditions = false, hasPractice = false))
    }
}
