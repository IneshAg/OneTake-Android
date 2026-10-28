package com.onetake.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Test

class LineLearningModeTest {
    @Test
    fun testLineLearningModesMatchActOnCueFeatures() {
        assertEquals("First-Letter Prompts", LineLearningMode.FIRST_LETTER.title)
        assertEquals("Mask & Reveal Lines", LineLearningMode.MASK_AND_REVEAL.title)
        assertEquals("Audio Gap Rehearsal", LineLearningMode.AUDIO_GAP.title)
        assertEquals("Hands-Free Audio Walk", LineLearningMode.HANDS_FREE_WALK.title)
    }

    @Test
    fun testFirstLetterMnemonicTransformation() {
        val dialogue = "To be or not to be, that is the question."
        val mnemonic = LineLearningMode.toFirstLetters(dialogue)
        assertEquals("T b o n t b t i t q", mnemonic)
    }

    @Test
    fun testMaskDialogueHidesWordsProgressively() {
        val dialogue = "I never made that promise to you"
        val masked = LineLearningMode.maskDialogue(dialogue, maskRatio = 1.0f)
        assertEquals("— — — — — — —", masked)
    }

    @Test
    fun testFormatMaskedWithReveals() {
        val dialogue = "I never made that promise"
        val pairs = LineLearningMode.formatMaskedWithReveals(dialogue, setOf(0))
        // index 0 was revealed ("I", true)
        assertEquals("I", pairs[0].first)
        assertEquals(true, pairs[0].second)
        // index 1 was odd, so unmasked ("never", false)
        assertEquals("never", pairs[1].first)
        assertEquals(false, pairs[1].second)
        // index 2 was even and unrevealed, so masked ("—", false)
        assertEquals("—", pairs[2].first)
        assertEquals(false, pairs[2].second)
    }

    @Test
    fun testAudioGapCalculation() {
        val gapMs = LineLearningMode.calculateAudioGapMillis(wordCount = 10)
        // 10 * 350 + 1500 = 5000ms
        assertEquals(5000L, gapMs)
    }
}
