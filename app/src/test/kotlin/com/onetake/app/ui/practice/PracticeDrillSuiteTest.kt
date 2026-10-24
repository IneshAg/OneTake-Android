package com.onetake.app.ui.practice

import com.onetake.app.AppStore
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.Destination
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PracticeDrillSuiteTest {
    @Test
    fun testPracticeStoreStartsEmptyAndResolvesExplicitPieces() {
        val store = AppStore()
        assertTrue("Store practice list must start empty without hardcoded scripts", store.practice.isEmpty())
        assertNull(store.practicePiece("any-id"))
    }

    @Test
    fun testPracticeTakeSavingAndNumbering() {
        val store = AppStore()
        val pieceId = "test-practice-piece-1"
        val initialTakes = store.takes.filter { it.sceneId == pieceId }.size
        assertEquals(0, initialTakes)

        val newTake = com.onetake.app.models.Take(
            number = initialTakes + 1,
            durationSeconds = 42,
            recordedAt = LocalDateTime.now(),
            isCircled = false,
            notes = listOf("Practice rehearsal"),
            sceneId = pieceId,
        )
        store.addTake(newTake)
        assertEquals(1, store.takes.filter { it.sceneId == pieceId }.size)
        assertEquals(1, newTake.number)

        val secondTake = com.onetake.app.models.Take(
            number = store.takes.count { it.sceneId == pieceId } + 1,
            durationSeconds = 38,
            recordedAt = LocalDateTime.now(),
            isCircled = true,
            notes = listOf("Great pacing"),
            sceneId = pieceId,
        )
        store.addTake(secondTake)
        assertEquals(2, store.takes.filter { it.sceneId == pieceId }.size)
        assertEquals(2, secondTake.number)
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
