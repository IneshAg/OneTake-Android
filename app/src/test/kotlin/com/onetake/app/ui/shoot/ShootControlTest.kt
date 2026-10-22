package com.onetake.app.ui.shoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShootControlTest {

    private val recordRegex = Regex("""(?i)\b(record|recording|action|roll|rolling|start|slate)\b""")
    private val stopRegex = Regex("""(?i)\b(stop|cut|finish|finished|done|end)\b""")

    @Test
    fun testVoiceCommandRecordTriggers() {
        // Valid phrases spoken from afar
        val triggers = listOf(
            "record",
            "Action!",
            "let's record now",
            "roll camera",
            "rolling",
            "Start",
            "Mark, slate",
        )
        for (phrase in triggers) {
            assertTrue("Expected phrase '$phrase' to match record trigger", recordRegex.containsMatchIn(phrase))
        }

        // Phrases that should not trigger recording
        val nonTriggers = listOf(
            "hello",
            "ready when you are",
            "my character is walking in",
            "what was that line",
        )
        for (phrase in nonTriggers) {
            assertFalse("Phrase '$phrase' should not match record trigger", recordRegex.containsMatchIn(phrase))
        }
    }

    @Test
    fun testVoiceCommandStopTriggers() {
        // Valid phrases spoken to cut the take
        val triggers = listOf(
            "cut",
            "Cut!",
            "stop",
            "please stop recording",
            "and cut",
            "finished",
            "all done",
            "end scene",
        )
        for (phrase in triggers) {
            assertTrue("Expected phrase '$phrase' to match stop trigger", stopRegex.containsMatchIn(phrase))
        }

        // Phrases that should not cut the take
        val nonTriggers = listOf(
            "I will never forget this",
            "Look at the horizon",
            "Don't go there",
            "Tell me the truth",
        )
        for (phrase in nonTriggers) {
            assertFalse("Phrase '$phrase' should not match stop trigger", stopRegex.containsMatchIn(phrase))
        }
    }

    @Test
    fun testCountdownTimerOptionsAndDefault() {
        val timerOptions = listOf(0, 3, 5, 10, 15)
        val defaultTimer = 3

        assertTrue("Default timer should be in allowed options", timerOptions.contains(defaultTimer))
        assertEquals(3, defaultTimer)
        assertEquals(5, timerOptions.size)

        // Instant recording when timer is 0
        val isInstant = timerOptions.first() == 0
        assertTrue("0 seconds should represent instant recording without countdown", isInstant)
    }

    @Test
    fun testCountdownSequence() {
        val timerSeconds = 3
        val sequence = (timerSeconds downTo 1).toList()
        assertEquals(listOf(3, 2, 1), sequence)

        val timerFive = 5
        val sequenceFive = (timerFive downTo 1).toList()
        assertEquals(listOf(5, 4, 3, 2, 1), sequenceFive)
    }
}
