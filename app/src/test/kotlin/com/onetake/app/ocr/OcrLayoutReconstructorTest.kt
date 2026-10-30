package com.onetake.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLayoutReconstructorTest {

    @Test
    fun `filters diagonal watermarks while keeping horizontal dialogue`() {
        val lines = listOf(
            OcrLineBox("INT. INTERROGATION ROOM - NIGHT", left = 120f, top = 100f, right = 680f, bottom = 128f, angleDegrees = 0.4f),
            // Diagonal watermark stamped across the page at -38 degrees
            OcrLineBox("CONFIDENTIAL - JANE DOE - DO NOT DISTRIBUTE", left = 180f, top = 420f, right = 880f, bottom = 460f, angleDegrees = -38.5f),
            OcrLineBox("MAYA", left = 420f, top = 210f, right = 520f, bottom = 238f, angleDegrees = 0.2f),
            OcrLineBox("Where were you at nine o'clock?", left = 260f, top = 246f, right = 720f, bottom = 274f, angleDegrees = -0.3f),
        )

        val text = OcrLayoutReconstructor.reconstructPage(lines, pageWidth = 1000, pageHeight = 1400)

        assertFalse("Diagonal watermark must be filtered out: $text", text.contains("DO NOT DISTRIBUTE"))
        assertTrue(text.contains("INT. INTERROGATION ROOM - NIGHT"))
        assertTrue(text.contains("MAYA\nWhere were you at nine o'clock?"))
    }

    @Test
    fun `pairs two-column breakdown table cells on the same visual row into Key Value lines`() {
        val lines = listOf(
            // ML Kit often returns entire left column first, then entire right column
            OcrLineBox("PROJECT", left = 80f, top = 120f, right = 240f, bottom = 146f),
            OcrLineBox("ROLE", left = 80f, top = 170f, right = 200f, bottom = 196f),
            OcrLineBox("CASTING DIRECTOR", left = 80f, top = 220f, right = 310f, bottom = 246f),
            OcrLineBox("SUBMISSION DEADLINE", left = 80f, top = 270f, right = 330f, bottom = 296f),
            OcrLineBox("Night Shift", left = 410f, top = 121f, right = 620f, bottom = 147f),
            OcrLineBox("Dr. Maya Reyes (Lead)", left = 410f, top = 171f, right = 740f, bottom = 197f),
            OcrLineBox("amble casting / Nina Hart", left = 410f, top = 221f, right = 760f, bottom = 247f),
            OcrLineBox("Friday Oct 30 at 5:00 PM", left = 410f, top = 271f, right = 750f, bottom = 297f),
        )

        val text = OcrLayoutReconstructor.reconstructPage(lines, pageWidth = 1000, pageHeight = 1400)

        assertTrue("Expected paired PROJECT row in:\n$text", text.contains("PROJECT: Night Shift"))
        assertTrue("Expected paired ROLE row in:\n$text", text.contains("ROLE: Dr. Maya Reyes (Lead)"))
        assertTrue("Expected paired CASTING DIRECTOR row in:\n$text", text.contains("CASTING DIRECTOR: amble casting / Nina Hart"))
        assertTrue("Expected paired SUBMISSION DEADLINE row in:\n$text", text.contains("SUBMISSION DEADLINE: Friday Oct 30 at 5:00 PM"))
    }

    @Test
    fun `inserts blank paragraph lines between dialogue and left-margin stage directions`() {
        val lines = listOf(
            OcrLineBox("MAYA", left = 410f, top = 200f, right = 500f, bottom = 226f),
            OcrLineBox("I told you not to call this number.", left = 260f, top = 232f, right = 720f, bottom = 258f),
            // Tight vertical spacing, OR normal paragraph gap, jumping out to left margin X=110 for action
            OcrLineBox("She slams the folder onto the desk and turns toward the window.", left = 110f, top = 274f, right = 880f, bottom = 300f),
            OcrLineBox("COLE", left = 410f, top = 344f, right = 495f, bottom = 370f),
            OcrLineBox("You didn't leave me a choice.", left = 260f, top = 376f, right = 680f, bottom = 402f),
        )

        val text = OcrLayoutReconstructor.reconstructPage(lines, pageWidth = 1000, pageHeight = 1400)

        // Blank line MUST separate Maya's dialogue from the action line so ScriptParser doesn't glue them together
        assertTrue(
            "Expected blank line before left-margin action in:\n$text",
            text.contains("I told you not to call this number.\n\nShe slams the folder"),
        )
        assertTrue(
            "Expected blank line before COLE cue in:\n$text",
            text.contains("turns toward the window.\n\nCOLE\nYou didn't leave me a choice."),
        )
    }

    @Test
    fun `strips repeated running headers, footers, and standalone margin page numbers across pages`() {
        val page1 = OcrLayoutReconstructor.PageInput(
            pageWidth = 1000,
            pageHeight = 1400,
            lines = listOf(
                OcrLineBox("NIGHT SHIFT - EPISODE 104 - BLUE REV.", left = 120f, top = 45f, right = 620f, bottom = 68f),
                OcrLineBox("12.", left = 860f, top = 45f, right = 900f, bottom = 68f),
                OcrLineBox("14", left = 80f, top = 140f, right = 110f, bottom = 166f),
                OcrLineBox("INT. HOSPITAL CORRIDOR - NIGHT", left = 150f, top = 140f, right = 640f, bottom = 166f),
                OcrLineBox("14", left = 870f, top = 140f, right = 905f, bottom = 166f),
                OcrLineBox("MAYA", left = 420f, top = 220f, right = 510f, bottom = 246f),
                OcrLineBox("Keep your voice down.", left = 260f, top = 252f, right = 580f, bottom = 278f),
                OcrLineBox("PROPERTY OF METRO PRODUCTIONS", left = 280f, top = 1335f, right = 720f, bottom = 1358f),
            ),
        )
        val page2 = OcrLayoutReconstructor.PageInput(
            pageWidth = 1000,
            pageHeight = 1400,
            lines = listOf(
                OcrLineBox("NIGHT SHIFT - EPISODE 104 - BLUE REV.", left = 120f, top = 45f, right = 620f, bottom = 68f),
                OcrLineBox("13.", left = 860f, top = 45f, right = 900f, bottom = 68f),
                OcrLineBox("COLE", left = 420f, top = 160f, right = 505f, bottom = 186f),
                OcrLineBox("Nobody's on this floor.", left = 260f, top = 192f, right = 600f, bottom = 218f),
                OcrLineBox("PROPERTY OF METRO PRODUCTIONS", left = 280f, top = 1335f, right = 720f, bottom = 1358f),
            ),
        )

        val doc = OcrLayoutReconstructor.reconstructDocument(listOf(page1, page2))

        assertFalse("Running header should be stripped: $doc", doc.contains("BLUE REV"))
        assertFalse("Running footer should be stripped: $doc", doc.contains("PROPERTY OF METRO"))
        assertFalse("Standalone page number 12. should be stripped: $doc", doc.contains("12."))
        assertTrue("Flanking scene numbers should be stripped from slugline: $doc", doc.contains("INT. HOSPITAL CORRIDOR - NIGHT"))
        assertFalse("Slugline should not keep trailing 14: $doc", doc.contains("NIGHT 14"))
    }

    @Test
    fun `stripRtfIfNeeded extracts plain screenplay text from RTF markup`() {
        val rtf = """{\rtf1\ansi\deff0{\fonttbl{\f0 Courier;}}\f0\fs24 INT. DINER - NIGHT\par\par MAYA\par Coffee, black.\par}"""
        val plain = OcrLayoutReconstructor.stripRtfIfNeeded(rtf)
        assertTrue("Expected INT. DINER - NIGHT in: $plain", plain.contains("INT. DINER - NIGHT"))
        assertTrue("Expected MAYA and Coffee, black. in: $plain", plain.contains("MAYA") && plain.contains("Coffee, black."))
        assertFalse("RTF tags must be removed: $plain", plain.contains("\\rtf1"))
    }
}
