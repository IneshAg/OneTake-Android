package com.onetake.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptSearchEngineTest {

    private val engine = ScriptSearchEngine(emptyList())

    @Test
    fun search_emptyQuery_returnsEmpty() {
        val results = engine.search("   ", emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun search_matchesDynamicAuditionLines() {
        val dynamicLines = listOf(
            ScriptSearchLine(
                character = "Kabir",
                project = "The Silent Echo",
                scene = "Scene 1",
                text = "I saw the confidential file on the desk.",
                auditionId = "aud-1",
            ),
            ScriptSearchLine(
                character = "Priya",
                project = "The Silent Echo",
                scene = "Scene 1",
                text = "We cannot open it here.",
                auditionId = "aud-1",
            ),
        )

        val results = engine.search("confidential", dynamicLines)
        assertEquals(1, results.size)
        val match = results.first()
        assertEquals("Kabir", match.character)
        assertEquals("The Silent Echo", match.project)
        assertEquals("aud-1", match.auditionId)
        assertTrue(match.line.contains("confidential"))
        assertEquals("confidential", match.line.substring(match.highlightSpan.first, match.highlightSpan.last + 1))
    }

    @Test
    fun search_matchesPracticePieceLines() {
        val dynamicLines = listOf(
            ScriptSearchLine(
                character = "Hamlet",
                project = "Hamlet Soliloquy",
                scene = "Act 3 Scene 1",
                text = "To be, or not to be, that is the question.",
                practicePieceId = "prac-99",
            )
        )

        val results = engine.search("question", dynamicLines)
        assertEquals(1, results.size)
        val match = results.first()
        assertEquals("Hamlet", match.character)
        assertEquals("prac-99", match.practicePieceId)
    }

    @Test
    fun search_matchesCharacterName() {
        val dynamicLines = listOf(
            ScriptSearchLine(
                character = "Morpheus",
                project = "The Matrix",
                scene = "Scene 10",
                text = "Take the blue pill and wake up in your bed.",
                auditionId = "aud-matrix",
            )
        )

        val results = engine.search("Morpheus", dynamicLines)
        assertEquals(1, results.size)
        assertEquals("Morpheus", results.first().character)
    }

    @Test
    fun search_scopeCues_onlyReturnsReaderLines() {
        val lines = listOf(
            ScriptSearchLine(
                character = "Detective",
                project = "Murder Mystery",
                scene = "Scene 1",
                text = "Where were you at eight?",
                auditionId = "aud-1",
                isMine = false, // Reader cue!
            ),
            ScriptSearchLine(
                character = "Suspect",
                project = "Murder Mystery",
                scene = "Scene 1",
                text = "I was at home eating dinner.",
                auditionId = "aud-1",
                isMine = true, // Actor line
            ),
        )

        val cueResults = engine.search("at", lines, SearchScope.CUES)
        assertEquals(1, cueResults.size)
        assertEquals("Detective", cueResults.first().character)
        assertEquals(false, cueResults.first().isMine)

        val dialogueResults = engine.search("at", lines, SearchScope.DIALOGUE)
        assertEquals(2, dialogueResults.size)
    }

    @Test
    fun search_scopeCharacters_onlyMatchesCharacterName() {
        val lines = listOf(
            ScriptSearchLine(
                character = "Romeo",
                project = "R&J",
                scene = "Scene 1",
                text = "Juliet, arise fair sun.",
                isMine = true,
            ),
            ScriptSearchLine(
                character = "Juliet",
                project = "R&J",
                scene = "Scene 1",
                text = "O Romeo, Romeo, wherefore art thou Romeo?",
                isMine = true,
            ),
        )

        val charResults = engine.search("Romeo", lines, SearchScope.CHARACTERS)
        assertEquals(1, charResults.size)
        assertEquals("Romeo", charResults.first().character)
    }
}
