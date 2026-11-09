package com.onetake.app.ui.practice

import android.app.Application
import com.onetake.app.AppStore
import com.onetake.app.models.PracticePiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PracticeDrillSuiteTest {
    @Test
    fun testPracticeStageActivePieceSelectionInStore() {
        val store = AppStore(Application())
        val p1 = PracticePiece(id = "p1", title = "Hamlet Monologue", detail = "Act 3 Sc 1", character = "Hamlet", linesLearned = 8, linesTotal = 24, lastPracticed = LocalDateTime.now().minusHours(1))
        val p2 = PracticePiece(id = "p2", title = "The Seagull", detail = "Act 1", character = "Konstantin", linesLearned = 3, linesTotal = 15, lastPracticed = LocalDateTime.now().minusDays(1))

        store.practice = listOf(p2, p1)
        val activePiece = store.activePracticePiece

        assertEquals("p1", activePiece?.id)
        assertEquals("Hamlet Monologue", activePiece?.title)
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
}
