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
    }
}
