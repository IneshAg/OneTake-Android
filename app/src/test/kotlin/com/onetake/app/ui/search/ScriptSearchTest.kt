package com.onetake.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptSearchTest {
    private val engine = ScriptSearchEngine(
        sampleScripts = listOf(
            ScriptSearchLine(character = "Arjun", project = "The Last Summer", scene = "Scene 3", text = "I never made that promise to you, Elena. You know the train leaves at dawn."),
            ScriptSearchLine(character = "Detective Rao", project = "Northbound", scene = "Scene 1", text = "A detective doesn't give promises, councilman. We collect facts.")
        )
    )

    @Test
    fun testSearchFindsDialogueMatchesAcrossScripts() {
        val results = engine.search("promise")
        assertEquals(2, results.size)
        assertEquals("Arjun", results[0].character)
        assertEquals("The Last Summer", results[0].project)
        assertTrue(results[0].line.contains("promise", ignoreCase = true))
    }

    @Test
    fun testEmptyQueryReturnsEmptyList() {
        val results = engine.search("")
        assertTrue(results.isEmpty())
    }
}
