package com.onetake.app.ui.auditions

import com.onetake.app.models.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NoticeParserTest {
    private val now = LocalDateTime.of(2026, 10, 24, 9, 0) // a Saturday

    @Test
    fun readsHeaderFieldsWithoutSpillingIntoTheNextLine() {
        val f = NoticeParser.parse("Project: Night Shift\nRole: Maya\nDue: Friday 5pm\nPlease slate first, landscape.", now)
        assertEquals("Night Shift", f.project)
        assertEquals("Maya", f.role)
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 0), f.due)
        assertEquals(listOf("Slate first", "Landscape"), f.asks)
    }

    @Test
    fun lowercaseWordsAfterInAreNotAProject() {
        val f = NoticeParser.parse("Self-tape request for MAYA in Night Shift. Tapes due in two days.", now)
        assertEquals("Night Shift", f.project)
        assertEquals("MAYA", f.role)
    }

    @Test
    fun noticeHeaderLinesAreNotScript() {
        val f = NoticeParser.parse("Project: Night Shift\nRole: Maya\n\nMAYA: You came back.\nDEV: I never left.", now)
        assertEquals("MAYA: You came back.\nDEV: I never left.", f.scriptText)
    }

    @Test
    fun parsesCommonDueDateForms() {
        assertEquals(LocalDateTime.of(2026, 10, 30, 10, 0), NoticeParser.parseDue("Submit by Oct 30 at 10:00 AM", now))
        assertEquals(LocalDateTime.of(2026, 10, 25, 12, 0), NoticeParser.parseDue("Send your tape by tomorrow noon.", now))
        assertEquals(LocalDateTime.of(2026, 10, 28, 10, 0), NoticeParser.parseDue("Shoot dates Nov 3-10.\nSelf tape due 10/28.", now))
        assertNull(NoticeParser.parseDue("Tapes due in two days.", now))
    }

    @Test
    fun picksDestinationAndSkipsSentenceAsProject() {
        val f = NoticeParser.parse("Hi! Please send your tape by email.", now)
        assertEquals("", f.project)
        assertEquals(Destination.EmailOrLink, f.destination)
        assertTrue(f.asks.isEmpty())
    }
}
