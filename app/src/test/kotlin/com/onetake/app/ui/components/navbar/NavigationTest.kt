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
}
