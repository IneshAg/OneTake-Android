package com.onetake.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptParserTest {
    private val screenplaySample = """
        INT. COFFEE SHOP - DAY

        ARJUN
        You said you'd be home by eight.

        NINA
        (sighs)
        I said I'd try. There's a difference.

        ARJUN
        That wasn't the deal.
    """.trimIndent()

    private val colonDialogueSample = """
        SAM: Are you coming tonight?
        NINA: Not if it rains.
        SAM: It won't.
    """.trimIndent()

    @Test
    fun testExtractCharactersFromScreenplay() {
        val characters = ScriptParser.extractCharacters(screenplaySample)
        assertEquals(listOf("ARJUN", "NINA"), characters)
    }

    @Test
    fun testExtractCharactersFromColonFormat() {
        val characters = ScriptParser.extractCharacters(colonDialogueSample)
        assertEquals(listOf("SAM", "NINA"), characters)
    }

    @Test
    fun testParseScreenplayAssignsIsMineCorrectly() {
        val scene = ScriptParser.parse(screenplaySample, myCharacter = "ARJUN", sceneTitle = "Coffee Shop")
        assertEquals(3, scene.lines.size)
        assertEquals("Coffee Shop", scene.title)

        // Line 1: Arjun
        assertEquals("ARJUN", scene.lines[0].character)
        assertEquals("You said you'd be home by eight.", scene.lines[0].text)
        assertTrue(scene.lines[0].isMine)

        // Line 2: Nina
        assertEquals("NINA", scene.lines[1].character)
        assertTrue(scene.lines[1].text.contains("I said I'd try"))
        assertFalse(scene.lines[1].isMine)

        // Line 3: Arjun
        assertEquals("ARJUN", scene.lines[2].character)
        assertTrue(scene.lines[2].isMine)
    }

    @Test
    fun testParseColonDialogueAssignsIsMineCorrectly() {
        val scene = ScriptParser.parse(colonDialogueSample, myCharacter = "NINA")
        assertEquals(3, scene.lines.size)
        assertFalse(scene.lines[0].isMine)
        assertTrue(scene.lines[1].isMine)
        assertFalse(scene.lines[2].isMine)
    }

    @Test
    fun testParseMonologueWhenNoCharactersDetected() {
        val monologue = "To be, or not to be, that is the question."
        val scene = ScriptParser.parse(monologue, myCharacter = "Hamlet")
        assertEquals(1, scene.lines.size)
        assertEquals("Hamlet", scene.lines[0].character)
        assertTrue(scene.lines[0].isMine)
        assertEquals(monologue, scene.lines[0].text)
    }

    @Test
    fun testDialogueStartingWithSlugWordsIsKept() {
        val scene = ScriptParser.parse("MAYA\nActually, I stayed.\n\nDEV\nScenery's nice.", myCharacter = "Maya Reyes")
        assertEquals(listOf("Actually, I stayed.", "Scenery's nice."), scene.lines.map { it.text })
        assertTrue("first-name cue matches full role name", scene.lines[0].isMine)
        assertFalse(scene.lines[1].isMine)
    }

    @Test
    fun testActionAfterBlankLineIsNotGluedToDialogue() {
        val scene = ScriptParser.parse("MAYA\nHi.\n\nShe crosses to the window.\n\nDEV\nHey.", myCharacter = "MAYA")
        assertEquals(listOf("Hi.", "Hey."), scene.lines.map { it.text })
    }

    @Test
    fun testClockTimesAndShortCuesAreNotCharacters() {
        assertTrue(ScriptParser.extractCharacters("Submit by Oct 30 at 10:00 AM").isEmpty())
        assertFalse(ScriptParser.isMyCue("AN", "Anaya"))
    }

    @Test
    fun `honorific prefixes match character first or last name without false-matching other doctors`() {
        assertTrue("Dr. Sarah Jenkins should match cue SARAH", ScriptParser.isMyCue("SARAH", "Dr. Sarah Jenkins"))
        assertTrue("Dr. Sarah Jenkins should match cue DR. JENKINS", ScriptParser.isMyCue("DR. JENKINS", "Dr. Sarah Jenkins"))
        assertTrue("Det. Cole should match cue COLE", ScriptParser.isMyCue("COLE", "Det. Cole"))
        assertFalse("Dr. Sarah Jenkins must NEVER match DR. PATEL", ScriptParser.isMyCue("DR. PATEL", "Dr. Sarah Jenkins"))
        assertTrue("Exact 2-letter role Bo should match cue BO", ScriptParser.isMyCue("BO", "Bo"))
    }

    @Test
    fun `page numbers transitions watermarks and shouted all-caps dialogue do not become characters`() {
        val trickySides = """
            24.
            CONFIDENTIAL
            BLUE REV. (10/29/26)

            14 INT. HOSPITAL CORRIDOR - NIGHT 14

            DR. SARAH JENKINS (V.O.)
            Keep moving!
            STOP!
            Don't open that door.

            COP #1
            Hands where I can see them!
            GET BACK!

            (CONTINUED)
            CONTINUED:
        """.trimIndent()

        val chars = ScriptParser.extractCharacters(trickySides)
        assertEquals(listOf("DR. SARAH JENKINS", "COP #1"), chars)

        val scene = ScriptParser.parse(trickySides, myCharacter = "Dr. Sarah Jenkins")
        assertEquals(2, scene.lines.size)
        assertTrue(scene.lines[0].isMine)
        assertEquals("Keep moving! STOP! Don't open that door.", scene.lines[0].text)
        assertFalse(scene.lines[1].isMine)
        assertEquals("COP #1", scene.lines[1].character)
        assertEquals("Hands where I can see them! GET BACK!", scene.lines[1].text)
    }

    @Test
    fun `colon format preserves wrapped multi-line dialogue turns`() {
        val wrappedColon = """
            MAYA: I waited outside the courthouse for two hours
            and nobody from your office bothered to show up.
            COLE: The hearing was moved to Thursday morning
            at the last minute.
            MAYA: Then pick up the phone next time.
        """.trimIndent()

        val scene = ScriptParser.parse(wrappedColon, myCharacter = "Maya")
        assertEquals(3, scene.lines.size)
        assertEquals(
            "I waited outside the courthouse for two hours and nobody from your office bothered to show up.",
            scene.lines[0].text,
        )
        assertEquals(
            "The hearing was moved to Thursday morning at the last minute.",
            scene.lines[1].text,
        )
    }
}
