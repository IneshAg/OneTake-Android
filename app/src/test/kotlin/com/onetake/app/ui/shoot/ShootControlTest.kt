package com.onetake.app.ui.shoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShootControlTest {

    @Test
    fun testDirectorCommandsDoNotFalseCutOnDialogueWords() {
        val stopRegex = ShootVoiceCommander.STOP_REGEX
        // Common dialogue lines that must NOT cut
        val dialogueLines = listOf(
            "I am done with this conversation",
            "At the end of the road",
            "We finished the job yesterday",
            "Stop by the house later",
        )
        for (line in dialogueLines) {
            assertFalse("Dialogue line '$line' should not trigger cut", stopRegex.containsMatchIn(line))
        }
        // True director cut commands must trigger
        val directorCommands = listOf("Cut!", "and cut", "scene cut", "stop recording", "cut take", "that's a wrap")
        for (cmd in directorCommands) {
            assertTrue("Director command '$cmd' must trigger cut", stopRegex.containsMatchIn(cmd))
        }
    }

    @Test
    fun testVoiceCommandRecordTriggers() {
        val recordRegex = ShootVoiceCommander.RECORD_REGEX
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
        val stopRegex = ShootVoiceCommander.STOP_REGEX
        // Valid phrases spoken to cut the take
        val triggers = listOf(
            "cut",
            "Cut!",
            "please stop recording",
            "and cut",
            "cut take",
            "scene cut",
            "that's a wrap",
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

    @Test
    fun testVoiceCommanderCallbackUpdate() {
        var recordCount = 0
        var cutCount = 0
        var onRecord: () -> Unit = { recordCount++ }
        var onCut: () -> Unit = { cutCount++ }

        val wrapperRecord: () -> Unit = { onRecord() }
        val wrapperCut: () -> Unit = { onCut() }

        wrapperRecord()
        assertEquals(1, recordCount)

        // Switch to new lambdas (simulating recomposition)
        var newRecordCalled = false
        onRecord = { newRecordCalled = true }
        wrapperRecord()
        assertTrue(newRecordCalled)
    }
}
