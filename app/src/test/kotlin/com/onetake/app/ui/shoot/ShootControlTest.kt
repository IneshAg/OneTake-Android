package com.onetake.app.ui.shoot

import com.onetake.app.ui.profile.HeadshotVoiceCommander
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

    @Test
    fun testShootVoiceCommanderRegexes() {
        // Record triggers
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("Action"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("action!"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("roll camera"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("Start recording"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("okay action"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("alright action"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("action please"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("action now"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("rolling"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("speed"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("camera speed"))
        assertTrue(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("let's record"))
        assertFalse(ShootVoiceCommander.RECORD_REGEX.containsMatchIn("I will take action tomorrow"))

        // Stop triggers
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("cut"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("Cut!"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("and cut"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("okay cut"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("alright cut"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("cut now"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("cut please"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("cut it"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("cut cut"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("cut take"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("Stop recording"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("That's a wrap"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("wrap"))
        assertTrue(ShootVoiceCommander.STOP_REGEX.containsMatchIn("wrap it"))
        assertFalse(ShootVoiceCommander.STOP_REGEX.containsMatchIn("Don't cut me off while I'm speaking"))
        assertFalse(ShootVoiceCommander.STOP_REGEX.containsMatchIn("I had to cut the wire and run"))
    }

    @Test
    fun testHeadshotVoiceCommanderRegexes() {
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("now"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("say now"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("take"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("take photo"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("take picture"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("shoot"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("shoot it"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("cheese"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("smile"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("click"))
        assertTrue(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("snap"))
        assertFalse(HeadshotVoiceCommander.CAPTURE_REGEX.containsMatchIn("I am not in position yet"))
    }

    @Test
    fun testMatchesCommandWithCrowdedRoomNoise() {
        // In crowded rooms or noisy environments, speech recognizer prefixes ambient noise/words
        assertTrue("Ambient noise before Action should trigger record", ShootVoiceCommander.matchesCommand("ambient background chatter action", isRecording = false))
        assertTrue("Noise before Cut should trigger cut", ShootVoiceCommander.matchesCommand("crowd noise and cut", isRecording = true))
        assertTrue("Director 'okay cut' in noisy room should trigger cut", ShootVoiceCommander.matchesCommand("people speaking okay cut", isRecording = true))
        assertTrue("Director 'that's a wrap' in noisy room should trigger cut", ShootVoiceCommander.matchesCommand("background noise that's a wrap", isRecording = true))
        assertTrue("Trailing punctuation should not break match", ShootVoiceCommander.matchesCommand("Action!", isRecording = false))
        assertTrue("Trailing exclamation on cut should match", ShootVoiceCommander.matchesCommand("Cut!", isRecording = true))

        // Dialogue protection: monologue during take must not trigger cut
        assertFalse("Dialogue mentioning cut inside monologue should not trigger cut", ShootVoiceCommander.matchesCommand("I had to cut the wire and run before the building fell", isRecording = true))
        assertFalse("Dialogue saying don't cut me off should not trigger cut", ShootVoiceCommander.matchesCommand("Don't cut me off while I'm speaking to you", isRecording = true))
        assertFalse("Budget cut sentence should not trigger cut", ShootVoiceCommander.matchesCommand("We made a severe cut to our departmental budget", isRecording = true))

        // Standby protection: random speech before recording must not start take
        assertFalse("Casual sentence with action should not trigger record", ShootVoiceCommander.matchesCommand("This is an action movie about two detectives", isRecording = false))
        assertFalse("I will take action tomorrow should not trigger record", ShootVoiceCommander.matchesCommand("I will take action tomorrow morning", isRecording = false))
    }
}
