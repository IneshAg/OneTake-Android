package com.onetake.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneSplitTest {
    @Test
    fun sluglinesStartNewScenes() {
        val sides = """
            INT. CAFÉ - DAY

            MAYA
            You're late.

            ARJUN
            I know.

            CUT TO:

            EXT. STREET - NIGHT

            ARJUN
            Wait!
        """.trimIndent()
        val scenes = ScriptParser.parseScenes(sides, myCharacter = "Arjun")
        assertEquals(listOf("Scene 1", "Scene 2"), scenes.map { it.title })
        assertEquals(listOf(1, 2), scenes.map { it.number })
        assertEquals("INT. CAFÉ - DAY", scenes[0].heading)
        assertEquals(2, scenes[0].lines.size)
        assertEquals(listOf("Wait!"), scenes[1].lines.map { it.text })
        assertTrue(scenes[1].lines.single().isMine)
    }

    @Test
    fun sceneHeadersStartNewScenes() {
        val sides = """
            SCENE 1
            MAYA: Hi.
            ARJUN: Hello.
            Scene 2:
            MAYA: Bye.
        """.trimIndent()
        val scenes = ScriptParser.parseScenes(sides, myCharacter = "Maya")
        assertEquals(2, scenes.size)
        assertEquals(listOf("Bye."), scenes[1].lines.map { it.text })
    }

    @Test
    fun withoutHeadingsItIsOneScene() {
        val scenes = ScriptParser.parseScenes("SAM: Coming?\nNINA: No.", myCharacter = "Nina")
        assertEquals(1, scenes.size)
        assertEquals(2, scenes[0].lines.size)
    }

    @Test
    fun dialogueThatLooksLikeAHeadingIsNot() {
        assertFalse(ScriptParser.isSceneHeading("Scene 2 was better."))
        assertFalse(ScriptParser.isSceneHeading("Int. was the word she used."))
        assertTrue(ScriptParser.isSceneHeading("SC. 14A — KITCHEN"))
        assertTrue(ScriptParser.isSceneHeading("INT/EXT CAR - MOVING"))
    }
}
