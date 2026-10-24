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
    fun testMaskWithRevealedIndices() {
        val text = "You said you would be home by eight"
        val revealed = setOf(0, 2)
        val result = LineLearningMode.formatMaskedWithReveals(text, revealed)
        assertEquals("You said you would — home — eight", result)
    }
}
