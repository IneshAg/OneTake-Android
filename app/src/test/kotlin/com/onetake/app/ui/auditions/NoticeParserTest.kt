package com.onetake.app.ui.auditions

import com.onetake.app.models.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("Pure email notice without sides must have empty scriptText", "", f.scriptText)
        assertEquals(DocumentKind.CastingNotice, f.kind)
    }

    @Test
    fun `classifies pure screenplay sides as SidesOnly without hallucinating project due or asks from dialogue`() {
        val pureSides = """
            14 INT. APARTMENT 4B - DAY 14

            MAYA
            He's crashing in Room 4B until Sunday. She sat in the sun and didn't blink.
            Let's wipe the slate clean before we head to London.

            COLE
            You really think that's going to work?
        """.trimIndent()

        val f = NoticeParser.parse(pureSides, now)

        assertEquals(DocumentKind.SidesOnly, f.kind)
        assertEquals("Dialogue 'in Room 4B' or 'to London' must NEVER become project title", "", f.project)
        assertNull("Dialogue 'Sunday' / 'sat' / 'sun' must NEVER become a due date", f.due)
        assertTrue("Dialogue 'wipe the slate clean' must NEVER trigger 'Slate first' ask: ${f.asks}", f.asks.isEmpty())
        assertEquals(listOf("MAYA", "COLE"), f.detectedCharacters)
        assertTrue("Script text should keep the sides intact", f.scriptText.contains("wipe the slate clean"))
    }

    @Test
    fun `parses multi-line uppercase breakdown labels and extracts roleSize projectType castingOffice and notes`() {
        val breakdown = """
            PROJECT
            Star Trek: Legacy
            TYPE
            TV Series
            ROLE
            Dr. Sarah Jenkins (Guest Star)
            CASTING DIRECTOR
            Hart / Miller Casting
            SUBMISSION DEADLINE
            Friday, Oct 30 at 5:30 PM
            NOTES
            Keep the delivery grounded and understated. Do not slate on this tape.
            SUBMIT VIA
            Actors Access / Eco Cast
        """.trimIndent()

        val f = NoticeParser.parse(breakdown, now)

        assertEquals(DocumentKind.CastingNotice, f.kind)
        assertEquals("Star Trek: Legacy", f.project)
        assertEquals("TV series", f.projectType)
        assertEquals("Dr. Sarah Jenkins", f.role)
        assertEquals("Guest star", f.roleSize)
        assertEquals("Hart / Miller Casting", f.castingOffice)
        assertEquals(LocalDateTime.of(2026, 10, 30, 17, 30), f.due)
        assertFalse("'Do not slate' must negate 'Slate first': ${f.asks}", "Slate first" in f.asks)
        assertTrue(f.notes.contains("grounded and understated"))
        assertEquals("Pure breakdown without dialogue must have empty scriptText", "", f.scriptText)
    }

    @Test
    fun `separates combined email notice and sides cleanly into NoticeWithSides`() {
        val combined = """
            From: Sarah Hart <sarah@hartcasting.com>
            Subject: Self-Tape Request - Role of DET. COLE in THE GLASS HARBOR

            Hi! Please submit your self-tape via Spotlight by tomorrow at 6:00 PM.
            Shoot in landscape, include a full-length shot and slate first.

            INT. DOCKSIDE WAREHOUSE - NIGHT

            DET. COLE
            Drop the flashlight and step where I can see you.

            MARCUS
            I came here alone, Detective.
        """.trimIndent()

        val f = NoticeParser.parse(combined, now)

        assertEquals(DocumentKind.NoticeWithSides, f.kind)
        assertEquals("THE GLASS HARBOR", f.project)
        assertEquals("DET. COLE", f.role)
        assertEquals(Destination.Spotlight, f.destination)
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), f.due)
        assertTrue("Slate first" in f.asks)
        assertTrue("Landscape" in f.asks)
        assertTrue("Full length" in f.asks)
        assertFalse("Email From: header must not leak into scriptText: ${f.scriptText}", f.scriptText.contains("hartcasting.com"))
        assertFalse("Email greeting must not leak into scriptText: ${f.scriptText}", f.scriptText.contains("Please submit your self-tape"))
        assertTrue("Sides dialogue must remain in scriptText", f.scriptText.contains("Drop the flashlight"))
    }
}
