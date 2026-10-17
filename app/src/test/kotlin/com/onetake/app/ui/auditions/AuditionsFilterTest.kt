package com.onetake.app.ui.auditions

import org.junit.Assert.assertEquals
import org.junit.Test

class AuditionsFilterTest {
    @Test
    fun testFilterLabelsMatchReferenceImage() {
        assertEquals("Active", AuditionFilter.ACTIVE.label)
        assertEquals("Due this week", AuditionFilter.DUE_THIS_WEEK.label)
        assertEquals("Sent", AuditionFilter.SENT.label)
        assertEquals("All", AuditionFilter.ALL.label)
    }

    @Test
    fun testDefaultFilterIsActive() {
        assertEquals(AuditionFilter.ACTIVE, AuditionFilter.default)
    }
}
