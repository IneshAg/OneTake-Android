package com.onetake.app.ui.system

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandEngineTest {

    @Test
    fun `matches punctuated commands in quiet room across all stages`() {
        val standby = setOf(VoiceCommand.Record)
        assertEquals(VoiceCommand.Record, VoiceCommandMatcher.match("Action!", standby))
        assertEquals(VoiceCommand.Record, VoiceCommandMatcher.match("Okay, action.", standby))
        assertEquals(VoiceCommand.Record, VoiceCommandMatcher.match("And... record?", standby))
        assertEquals(VoiceCommand.Record, VoiceCommandMatcher.match("Roll camera!", standby))
        assertEquals(VoiceCommand.Record, VoiceCommandMatcher.match("Start recording.", standby))

        val recording = setOf(VoiceCommand.Cut)
        assertEquals(VoiceCommand.Cut, VoiceCommandMatcher.match("Cut!", recording))
        assertEquals(VoiceCommand.Cut, VoiceCommandMatcher.match("And, cut.", recording))
        assertEquals(VoiceCommand.Cut, VoiceCommandMatcher.match("Okay... cut?", recording))
        assertEquals(VoiceCommand.Cut, VoiceCommandMatcher.match("That's a wrap!", recording))
        assertEquals(VoiceCommand.Cut, VoiceCommandMatcher.match("And scene.", recording))
        assertEquals(VoiceCommand.Cut, VoiceCommandMatcher.match("Stop recording", recording))

        val headshot = setOf(VoiceCommand.CapturePhoto)
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandMatcher.match("Now!", headshot))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandMatcher.match("Say, now.", headshot))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandMatcher.match("Right now!", headshot))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandMatcher.match("Take photo?", headshot))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandMatcher.match("Cheese!", headshot))
        assertEquals(VoiceCommand.CapturePhoto, VoiceCommandMatcher.match("Snap", headshot))

        val check = setOf(VoiceCommand.ReadyMark, VoiceCommand.Record)
        assertEquals(VoiceCommand.ReadyMark, VoiceCommandMatcher.match("Go to your mark.", check))
        assertEquals(VoiceCommand.ReadyMark, VoiceCommandMatcher.match("On my mark!", check))
        assertEquals(VoiceCommand.ReadyMark, VoiceCommandMatcher.match("Ready!", check))

        val postTake = setOf(VoiceCommand.Next, VoiceCommand.Again)
        assertEquals(VoiceCommand.Next, VoiceCommandMatcher.match("Next scene!", postTake))
        assertEquals(VoiceCommand.Next, VoiceCommandMatcher.match("Continue.", postTake))
        assertEquals(VoiceCommand.Again, VoiceCommandMatcher.match("Go again!", postTake))
        assertEquals(VoiceCommand.Again, VoiceCommandMatcher.match("One more take", postTake))
        assertEquals(VoiceCommand.Again, VoiceCommandMatcher.match("Retake", postTake))
    }

    @Test
    fun `detects commands inside continuous crowded-room partial streams even with trailing ambient noise`() {
        val session = VoiceSessionTracker()
        val standby = setOf(VoiceCommand.Record)

        // Continuous background chatter in a crowded room where onResults never fires
        assertNull(session.evaluateHypotheses(listOf("two lattes for table four please"), standby))
        assertNull(session.evaluateHypotheses(listOf("two lattes for table four please yeah"), standby))

        // Actor says "Okay, action!" while background chatter adds "right" in the same partial packet
        val cmd = session.evaluateHypotheses(
            listOf("two lattes for table four please yeah okay, action! right"),
            standby,
        )
        assertEquals(VoiceCommand.Record, cmd)

        // Switching to Recording mode in the same or next partial must ignore pre-switch tokens
        session.resetForModeChange(listOf("two lattes for table four please yeah okay, action! right"))
        val recording = setOf(VoiceCommand.Cut)
        assertNull(
            session.evaluateHypotheses(
                listOf("two lattes for table four please yeah okay, action! right"),
                recording,
            )
        )

        // Actor performs lines in noisy room -> must NOT cut
        assertNull(
            session.evaluateHypotheses(
                listOf("two lattes for table four please yeah okay, action! right i had to cut"),
                recording,
            )
        )
        assertNull(
            session.evaluateHypotheses(
                listOf("two lattes for table four please yeah okay, action! right i had to cut the wire and run"),
                recording,
            )
        )

        // Actor finishes take and says "And cut" amidst background chatter
        val cutCmd = session.evaluateHypotheses(
            listOf("two lattes for table four please yeah okay, action! right i had to cut the wire and run and cut thanks"),
            recording,
        )
        assertEquals(VoiceCommand.Cut, cutCmd)
    }

    @Test
    fun `protects dramatic monologue partials and casual standby speech from false triggers`() {
        val recording = setOf(VoiceCommand.Cut)
        // Crucial: mid-sentence partial BEFORE object words arrive ("I had to cut" / "Don't cut")!
        assertNull(VoiceCommandMatcher.match("I had to cut", recording))
        assertNull(VoiceCommandMatcher.match("Don't cut", recording))
        assertNull(VoiceCommandMatcher.match("Don't cut me off while I'm speaking", recording))
        assertNull(VoiceCommandMatcher.match("I had to cut the wire and run before the building fell", recording))
        assertNull(VoiceCommandMatcher.match("We made a severe cut", recording))
        assertNull(VoiceCommandMatcher.match("We made a severe cut to our departmental budget", recording))
        assertNull(VoiceCommandMatcher.match("You cannot stop the train now", recording))
        assertNull(VoiceCommandMatcher.match("Don't stop", recording))

        val standby = setOf(VoiceCommand.Record)
        assertNull(VoiceCommandMatcher.match("This is an action movie about two detectives", standby))
        assertNull(VoiceCommandMatcher.match("I will take action", standby))
        assertNull(VoiceCommandMatcher.match("I will take action tomorrow morning", standby))
        assertNull(VoiceCommandMatcher.match("Just for the record", standby))
        assertNull(VoiceCommandMatcher.match("Off the record", standby))

        val headshot = setOf(VoiceCommand.CapturePhoto)
        assertNull(VoiceCommandMatcher.match("I am not in position yet", headshot))
        assertNull(VoiceCommandMatcher.match("From now on we wait", headshot))
    }

    @Test
    fun `Android 13 on-device errors permanently fall back to standard SpeechRecognizer`() {
        // Error 12 (ERROR_LANGUAGE_NOT_SUPPORTED), 13 (ERROR_LANGUAGE_UNAVAILABLE), 14, 11
        for (errorCode in listOf(11, 12, 13, 14)) {
            val decision = VoiceErrorRecoveryPolicy.decide(
                error = errorCode,
                usingOnDevice = true,
                consecutiveErrors = 1,
            )
            assertFalse(decision.stopPermanently)
            assertTrue("Expected recreateRecognizer for error $errorCode", decision.recreateRecognizer)
            assertTrue("Expected fallbackToStandardRecognizer for error $errorCode", decision.fallbackToStandardRecognizer)
        }

        // Normal silence boundaries (NO_MATCH / SPEECH_TIMEOUT) must NOT recreate or tear down recognizer
        val silenceDecision = VoiceErrorRecoveryPolicy.decide(
            error = SpeechRecognizer.ERROR_NO_MATCH,
            usingOnDevice = false,
            consecutiveErrors = 1,
        )
        assertFalse(silenceDecision.stopPermanently)
        assertFalse(silenceDecision.recreateRecognizer)
        assertFalse(silenceDecision.fallbackToStandardRecognizer)

        // Insufficient permissions stops cleanly instead of spinning in a background loop
        val permDecision = VoiceErrorRecoveryPolicy.decide(
            error = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            usingOnDevice = false,
            consecutiveErrors = 1,
        )
        assertTrue(permDecision.stopPermanently)
    }

    @Test
    fun `PendingSpeechQueue holds speech requested before TTS initialization and flushes on ready`() {
        val queue = PendingSpeechQueue()
        var completed = false

        // Requested while TTS is still initializing -> held, not dropped
        queue.enqueue("We have to leave before dawn.") { completed = true }
        assertFalse("Callback must not fire prematurely while TTS is still initializing", completed)

        val flushed = queue.drainOnInitialized()
        assertEquals("We have to leave before dawn.", flushed?.first)
        assertNull(queue.drainOnInitialized())

        // If TTS init fails, queued callback resolves immediately so callers never hang
        var failedCompleted = false
        queue.enqueue("Second line") { failedCompleted = true }
        queue.failAll()
        assertTrue("Callback must resolve immediately if TTS engine fails initialization", failedCompleted)
    }
}
