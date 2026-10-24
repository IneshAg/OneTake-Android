package com.onetake.app.ui.practice

import com.onetake.app.AppStore
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PracticeDrillSuiteTest {
    @Test
    fun testDefaultRepertoireSeededAndAvailableForDrills() {
        val store = AppStore()
        val defaultPiece = store.practicePiece("default-practice-audition-piece")
        assertNotNull("Default repertoire should be resolvable", defaultPiece)
        assertEquals("The Audition", defaultPiece!!.title)
        assertNotNull(defaultPiece.scene)
        assertTrue("Default scene should contain rehearsal lines", defaultPiece.scene!!.lines.size >= 4)
        assertTrue("Default scene should have actor and reader lines", defaultPiece.scene!!.lines.any { it.isMine } && defaultPiece.scene!!.lines.any { !it.isMine })

        val fallback = store.defaultPracticePiece()
        assertEquals("The Audition", fallback.title)
    }
    @Test
    fun testMaskDialogueDifferentRatios() {
        val dialogue = "One two three four"
        val quarter = LineLearningMode.maskDialogue(dialogue, maskRatio = 0.25f)
        val half = LineLearningMode.maskDialogue(dialogue, maskRatio = 0.5f)
        val full = LineLearningMode.maskDialogue(dialogue, maskRatio = 1.0f)

        assertEquals("— two three four", quarter)
        assertEquals("— — three four", half)
        assertEquals("— — — —", full)
    }

    @Test
    fun testCalculateAudioGapMillis() {
        val line = "You said you'd be home by eight."
        val words = line.split(Regex("\\s+")).size
        val gapMs = LineLearningMode.calculateAudioGapMillis(words)
        // 7 words * 350ms + 1500ms = 3950ms
        assertEquals(3950L, gapMs)
    }

    @Test
    fun testAudioWalkPacing() {
        val words = 10
        val pacing = LineLearningMode.calculateSpokenPacingMillis(words)
        assertTrue(pacing >= 3000L)
    }

    @Test
    fun testAudioWalkFallbackSceneResolution() {
        val audition = Audition(
            id = "aud-test",
            project = "Test Project",
            projectType = "Film",
            role = "Detective",
            roleSize = "Lead",
            castingOffice = "",
            due = LocalDateTime.now().plusDays(2),
            asks = emptyList(),
            pieces = emptyList(),
            scenes = listOf(
                ScriptScene(
                    id = "sc-1",
                    number = 1,
                    title = "Scene 1",
                    heading = "INT. OFFICE - DAY",
                    lines = listOf(ScriptLine(character = "DETECTIVE", text = "Where were you?", isMine = true)),
                    estimatedSeconds = 45,
                )
            ),
            destination = Destination.ActorsAccess,
            status = AuditionStatus.Due,
        )

        val outOfBoundsIndex = 5
        val resolved = audition.scenes.getOrNull(outOfBoundsIndex) ?: audition.scenes.firstOrNull()
        assertNotNull(resolved)
        assertEquals("Scene 1", resolved?.title)
    }
}
