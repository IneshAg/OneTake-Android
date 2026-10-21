package com.onetake.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeDrillSuiteTest {
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
