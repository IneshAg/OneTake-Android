package com.onetake.app.ui.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsReaderEngineTest {
    class FakeSpeechEngine : RehearsalSpeechEngine {
        var lastSpoken: String? = null
        var stopped: Boolean = false
        var available: Boolean = true

        override fun speak(text: String, onDone: () -> Unit) {
            lastSpoken = text
            onDone()
        }

        override fun stop() {
            stopped = true
        }

        override fun isAvailable(): Boolean = available
    }

    @Test
    fun testFakeSpeechEngineDeliversTextAndCallsOnDone() {
        val engine = FakeSpeechEngine()
        var completed = false
        engine.speak("You said you'd be home by eight.") { completed = true }
        assertEquals("You said you'd be home by eight.", engine.lastSpoken)
        assertTrue(completed)
    }

    @Test
    fun testFakeSpeechEngineStops() {
        val engine = FakeSpeechEngine()
        engine.stop()
        assertTrue(engine.stopped)
    }
}
