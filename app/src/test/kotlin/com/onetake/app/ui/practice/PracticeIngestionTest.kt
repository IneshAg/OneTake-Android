package com.onetake.app.ui.practice

import android.app.Application
import com.onetake.app.AppStore
import com.onetake.app.data.ScriptParser
import com.onetake.app.models.PracticePiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeIngestionTest {
    @Test
    fun testCreatePracticePieceFromParsedScript() {
        val script = """
            SAM: I found the letter under the door.
            NINA: You weren't supposed to read it.
            SAM: It had my name on it.
        """.trimIndent()
        val characters = ScriptParser.extractCharacters(script)
        assertEquals(listOf("SAM", "NINA"), characters)

        val scene = ScriptParser.parse(script, myCharacter = "SAM", sceneTitle = "The Letter")
        val piece = PracticePiece(
            title = scene.title,
            detail = "SAM · 3 lines",
            linesLearned = 0,
            linesTotal = scene.lines.count { it.isMine },
            character = "SAM",
            scene = scene,
        )

        val store = AppStore(Application())
        store.addPractice(piece)

        assertEquals(1, store.practice.size)
        assertEquals("The Letter", store.practice[0].title)
        assertEquals("SAM", store.practice[0].character)
        assertEquals(2, store.practice[0].linesTotal)
        assertTrue(store.practice[0].scene!!.lines[0].isMine)
    }

    @Test
    fun testStoreHandlesMultiplePracticePieces() {
        val store = AppStore(Application())
        val p1 = PracticePiece(title = "Scene 1", detail = "Lead", linesLearned = 0, linesTotal = 5, character = "Alex")
        val p2 = PracticePiece(title = "Scene 2", detail = "Supporting", linesLearned = 0, linesTotal = 10, character = "Jordan")

        store.addPractice(p1)
        store.addPractice(p2)

        assertEquals(2, store.practice.size)
        assertEquals("Scene 2", store.practice[0].title) // prepended
        assertEquals("Scene 1", store.practice[1].title)
    }
}
