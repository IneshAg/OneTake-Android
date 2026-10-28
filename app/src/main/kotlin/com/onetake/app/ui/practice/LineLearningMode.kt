package com.onetake.app.ui.practice

/**
 * Line learning and active memorization modes inspired by cognitive rehearsal techniques
 * (such as those in ActOnCue):
 * - First-Letter Prompts: converts words to their first letters to trigger memory retrieval.
 * - Mask & Reveal Lines: progressively shields dialogue to test off-book confidence.
 * - Audio Gap Rehearsal: scene partner delivers cues, leaving silence for actor delivery.
 * - Hands-Free Audio Walk: loops the scene audio continuously for passive memorization.
 */
enum class LineLearningMode(
    val title: String,
    val subtitle: String,
) {
    FIRST_LETTER(
        title = "First-Letter Prompts",
        subtitle = "Replaces lines with initial letters to trigger recall",
    ),
    MASK_AND_REVEAL(
        title = "Mask & Reveal Lines",
        subtitle = "Hides words progressively · Tap to peek on hesitation",
    ),
    AUDIO_GAP(
        title = "Audio Gap Rehearsal",
        subtitle = "Partner delivers cues then leaves silence for your line",
    ),
    HANDS_FREE_WALK(
        title = "Hands-Free Audio Walk",
        subtitle = "Listen to entire scene on repeat while on the go",
    );

    companion object {
        /**
         * Converts dialogue into initial letter mnemonic tokens:
         * "To be or not to be, that is the question." -> "T b o n t b t i t q"
         */
        fun toFirstLetters(text: String): String {
            if (text.isBlank()) return ""
            return text.trim()
                .split(Regex("\\s+"))
                .mapNotNull { token ->
                    token.firstOrNull { it.isLetterOrDigit() }?.toString()
                }
                .joinToString(" ")
        }

        /**
         * Masks dialogue words with em-dashes according to [maskRatio] (0.0f = full text, 1.0f = all masked).
         */
        fun maskDialogue(text: String, maskRatio: Float = 1.0f): String {
            if (text.isBlank()) return ""
            val words = text.trim().split(Regex("\\s+"))
            val countToMask = (words.size * maskRatio.coerceIn(0f, 1f)).toInt()
            return words.mapIndexed { index, word ->
                if (index < countToMask) "—" else word
            }.joinToString(" ")
        }

        /**
         * Masks dialogue with progressive word shielding and tap-to-reveal tokens.
         */
        fun formatMaskedWithReveals(text: String, revealedWordIndices: Set<Int>): List<Pair<String, Boolean>> {
            if (text.isBlank()) return emptyList()
            val words = text.trim().split(Regex("\\s+"))
            return words.mapIndexed { index, word ->
                if (index in revealedWordIndices) {
                    word to true // Revealed word
                } else if (index % 2 == 0) {
                    "—" to false // Masked token
                } else {
                    word to false // Unmasked token
                }
            }
        }

        /**
         * Computes the pause duration in milliseconds allocated for the actor to deliver their line.
         * Default: ~350ms per word + 1.5s grace time.
         */
        fun calculateAudioGapMillis(wordCount: Int): Long = (wordCount * 350L) + 1500L

        /**
         * Computes pacing duration in milliseconds for spoken lines during continuous audio walk.
         */
        fun calculateSpokenPacingMillis(wordCount: Int): Long = (wordCount * 320L) + 1200L
    }
}
