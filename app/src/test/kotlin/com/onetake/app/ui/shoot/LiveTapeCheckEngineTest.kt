package com.onetake.app.ui.shoot

import com.onetake.app.ui.camera.LiveTapeCheckEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class LiveTapeCheckEngineTest {

    @Test
    fun testFramingCenteredOptimalMediumCloseUp() {
        // 1080 x 1920 upright frame
        // Face centered horizontally: x = 540 (normCenterX = 0.50)
        // Face height = 500 (normHeight = 500 / 1920 = 0.26)
        val (status, label) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 540f,
            faceHeight = 500f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
        )

        assertEquals(LiveTapeCheckEngine.FramingStatus.Centered, status)
        assertEquals("Framing centered", label)
    }

    @Test
    fun testFramingShiftDirectionsForFrontCamera() {
        // Face shifted to the right side of frame (normCenterX = 0.80 > 0.62)
        // On front (mirrored) camera, this means user needs to shift left
        val (statusLeft, labelLeft) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 864f,
            faceHeight = 500f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.ShiftLeft, statusLeft)
        assertEquals("Shift left", labelLeft)

        // Face shifted to the left side of frame (normCenterX = 0.20 < 0.38)
        // On front (mirrored) camera, this means user needs to shift right
        val (statusRight, labelRight) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 216f,
            faceHeight = 500f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.ShiftRight, statusRight)
        assertEquals("Shift right", labelRight)
    }

    @Test
    fun testFramingDistanceGuidance() {
        // Too far: face height = 150px in 1920 (normHeight = 0.078 < 0.13)
        val (statusFar, labelFar) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 540f,
            faceHeight = 150f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.MoveCloser, statusFar)
        assertEquals("Move closer", labelFar)

        // Too close: face height = 1200px in 1920 (normHeight = 0.625 > 0.56)
        val (statusClose, labelClose) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 540f,
            faceHeight = 1200f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.StepBack, statusClose)
        assertEquals("Step back", labelClose)
    }

    @Test
    fun testEyelineUpperThirdDetection() {
        // Normal self-tape eye-level: upper third (y = 650 in 1920 -> normY = 0.338)
        val (statusGood, labelGood) = LiveTapeCheckEngine.evaluateEyelineMetrics(
            faceCenterY = 650f,
            uprightHeight = 1920,
        )
        assertEquals(LiveTapeCheckEngine.EyelineStatus.Good, statusGood)
        assertEquals("Eyeline level", labelGood)

        // Face too low in frame: y = 1200 in 1920 -> normY = 0.625 > 0.52
        val (statusLow, labelLow) = LiveTapeCheckEngine.evaluateEyelineMetrics(
            faceCenterY = 1200f,
            uprightHeight = 1920,
        )
        assertEquals(LiveTapeCheckEngine.EyelineStatus.TooLow, statusLow)
        assertEquals("Raise camera", labelLow)

        // Face too high in frame: y = 200 in 1920 -> normY = 0.104 < 0.22
        val (statusHigh, labelHigh) = LiveTapeCheckEngine.evaluateEyelineMetrics(
            faceCenterY = 200f,
            uprightHeight = 1920,
        )
        assertEquals(LiveTapeCheckEngine.EyelineStatus.TooHigh, statusHigh)
        assertEquals("Tilt down", labelHigh)
    }

    @Test
    fun testLightingEvaluation() {
        // Balanced lighting (luma = 120)
        val (statusBalanced, labelBalanced) = LiveTapeCheckEngine.evaluateLighting(120.0)
        assertEquals(LiveTapeCheckEngine.LightStatus.Balanced, statusBalanced)
        assertEquals("Light balanced", labelBalanced)

        // Too dark (luma = 30)
        val (statusDark, labelDark) = LiveTapeCheckEngine.evaluateLighting(30.0)
        assertEquals(LiveTapeCheckEngine.LightStatus.TooDark, statusDark)
        assertEquals("Too dark", labelDark)

        // Too bright (luma = 230)
        val (statusBright, labelBright) = LiveTapeCheckEngine.evaluateLighting(230.0)
        assertEquals(LiveTapeCheckEngine.LightStatus.TooBright, statusBright)
        assertEquals("Too bright", labelBright)
    }

    @Test
    fun testSoundEvaluation() {
        // Mic permission not granted
        val (statusOff, labelOff) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = false, audioDb = -20f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.MicOff, statusOff)
        assertEquals("Mic needed", labelOff)

        // Quiet room tone (-45 dB)
        val (statusQuiet, labelQuiet) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = true, audioDb = -45f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.QuietRoom, statusQuiet)
        assertEquals("Room quiet", labelQuiet)

        // Clear speech level (-18 dB)
        val (statusClear, labelClear) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = true, audioDb = -18f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.Clear, statusClear)
        assertEquals("Clear level", labelClear)

        // Audio clipping / peaking (-2 dB > -8 dB)
        val (statusClip, labelClip) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = true, audioDb = -2f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.Clipping, statusClip)
        assertEquals("Audio peaking", labelClip)
    }

    @Test
    fun testLiveTapeCheckAllPassedCondition() {
        val allGood = LiveTapeCheckEngine.LiveTapeCheck(
            framing = LiveTapeCheckEngine.FramingStatus.Centered,
            isFramingPassed = true,
            eyeline = LiveTapeCheckEngine.EyelineStatus.Good,
            isEyelinePassed = true,
            light = LiveTapeCheckEngine.LightStatus.Balanced,
            isLightPassed = true,
            sound = LiveTapeCheckEngine.SoundStatus.Clear,
            isSoundPassed = true,
        )
        assertTrue(allGood.allPassed)

        val oneFailed = allGood.copy(isFramingPassed = false)
        assertFalse(oneFailed.allPassed)
    }

    @Test
    fun testCalculateLuminanceFromBuffer() {
        val bytes = ByteArray(100) { 100.toByte() }
        val buffer = ByteBuffer.wrap(bytes)
        val luma = LiveTapeCheckEngine.calculateLuminance(buffer)
        assertEquals(100.0, luma, 1.0)
    }
}
