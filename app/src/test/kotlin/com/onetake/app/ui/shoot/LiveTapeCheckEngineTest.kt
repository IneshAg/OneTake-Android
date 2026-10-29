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
    fun testFramingShiftDirectionsForFrontCameraMatchMirroredSelfieView() {
        // Sensor rawCenterX = 0.80 (864 / 1080).
        // On front camera, raw X = 0.80 is the camera's right = actor's physical LEFT (screenX = 0.20 on mirrored preview).
        // Actor sees themselves on the left edge of the screen and must shift RIGHT toward center.
        val (statusFromLeftEdge, labelFromLeftEdge) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 864f,
            faceHeight = 500f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.ShiftRight, statusFromLeftEdge)
        assertEquals("Shift right", labelFromLeftEdge)

        // Sensor rawCenterX = 0.20 (216 / 1080).
        // On front camera, raw X = 0.20 is actor's physical RIGHT (screenX = 0.80 on mirrored preview).
        // Actor sees themselves on the right edge of the screen and must shift LEFT toward center.
        val (statusFromRightEdge, labelFromRightEdge) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 216f,
            faceHeight = 500f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.ShiftLeft, statusFromRightEdge)
        assertEquals("Shift left", labelFromRightEdge)
    }

    @Test
    fun testFillCenterViewportMappingDetectsOffCenterOnTallPhoneDisplay() {
        // 480 x 640 (4:3 = 0.75 aspect) analysis frame displayed FILL_CENTER on a 20:9 (0.45 aspect) portrait screen.
        // Visible horizontal slice is 0.45 / 0.75 = 0.60 (from x = 0.20 to 0.80 of sensor).
        // Face at raw sensor X = 180 / 480 = 0.375 -> mirrored X = 0.625 -> visible screenX = (0.625 - 0.20) / 0.60 = 0.708!
        // Without FILL_CENTER mapping, 0.375 looked almost centered; on screen it's at 71% (way off to the right).
        val box = LiveTapeCheckEngine.sensorToVisibleScreenBox(
            faceLeft = 130f,
            faceTop = 160f,
            faceRight = 230f,
            faceBottom = 320f,
            uprightWidth = 480,
            uprightHeight = 640,
            isFrontCamera = true,
            viewAspect = 0.45f,
        )
        assertEquals(0.708f, box.centerX, 0.02f)

        val (status, _) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 180f,
            faceHeight = 160f,
            uprightWidth = 480,
            uprightHeight = 640,
            isFrontCamera = true,
            viewAspect = 0.45f,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.ShiftLeft, status)
    }

    @Test
    fun testFullBodyFramingModeForFullLengthStage() {
        // In FullBody mode, medium close-up (faceHeight = 500 / 1920 = 0.26) is way too close for head-to-toe!
        val (tooCloseStatus, tooCloseLabel) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 540f,
            faceHeight = 500f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
            target = LiveTapeCheckEngine.FramingTarget.FullBody,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.StepBack, tooCloseStatus)
        assertEquals("Step further back", tooCloseLabel)

        // Stepping 10 feet back: faceHeight = 190 / 1920 (~0.10), head near upper frame (y = 320 / 1920 = 0.166)
        val (goodBodyStatus, goodBodyLabel) = LiveTapeCheckEngine.evaluateFramingMetrics(
            faceCenterX = 540f,
            faceHeight = 190f,
            uprightWidth = 1080,
            uprightHeight = 1920,
            isFrontCamera = true,
            target = LiveTapeCheckEngine.FramingTarget.FullBody,
        )
        assertEquals(LiveTapeCheckEngine.FramingStatus.Centered, goodBodyStatus)
        assertEquals("Full body framed", goodBodyLabel)

        val (eyeStatus, eyeLabel) = LiveTapeCheckEngine.evaluateEyelineMetrics(
            faceCenterY = 320f,
            uprightHeight = 1920,
            target = LiveTapeCheckEngine.FramingTarget.FullBody,
        )
        assertEquals(LiveTapeCheckEngine.EyelineStatus.Good, eyeStatus)
        assertEquals("Headroom good", eyeLabel)
    }

    @Test
    fun testFramingDistanceGuidance() {
        // Too far: face height = 150px in 1920 (normHeight = 0.078 < 0.14)
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

        // Face too high in frame: y = 200 in 1920 -> normY = 0.104 < 0.20
        val (statusHigh, labelHigh) = LiveTapeCheckEngine.evaluateEyelineMetrics(
            faceCenterY = 200f,
            uprightHeight = 1920,
        )
        assertEquals(LiveTapeCheckEngine.EyelineStatus.TooHigh, statusHigh)
        assertEquals("Tilt down", labelHigh)
    }

    @Test
    fun testLightingEvaluation() {
        val (statusBalanced, labelBalanced) = LiveTapeCheckEngine.evaluateLighting(120.0)
        assertEquals(LiveTapeCheckEngine.LightStatus.Balanced, statusBalanced)
        assertEquals("Light balanced", labelBalanced)

        val (statusDark, labelDark) = LiveTapeCheckEngine.evaluateLighting(30.0)
        assertEquals(LiveTapeCheckEngine.LightStatus.TooDark, statusDark)
        assertEquals("Too dark", labelDark)

        val (statusBright, labelBright) = LiveTapeCheckEngine.evaluateLighting(230.0)
        assertEquals(LiveTapeCheckEngine.LightStatus.TooBright, statusBright)
        assertEquals("Too bright", labelBright)
    }

    @Test
    fun testSoundEvaluationAndLiveUpdateWithoutFrame() {
        val (statusOff, labelOff) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = false, audioDb = -20f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.MicOff, statusOff)
        assertEquals("Mic needed", labelOff)

        val (statusQuiet, labelQuiet) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = true, audioDb = -45f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.QuietRoom, statusQuiet)
        assertEquals("Room quiet", labelQuiet)

        val (statusClear, labelClear) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = true, audioDb = -18f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.Clear, statusClear)
        assertEquals("Clear level", labelClear)

        val (statusClip, labelClip) = LiveTapeCheckEngine.evaluateSound(micPermissionGranted = true, audioDb = -2f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.Clipping, statusClip)
        assertEquals("Audio peaking", labelClip)

        val initial = LiveTapeCheckEngine.LiveTapeCheck()
        val updated = initial.withUpdatedSound(micPermissionGranted = true, audioDb = -3f)
        assertEquals(LiveTapeCheckEngine.SoundStatus.Clipping, updated.sound)
        assertFalse(updated.isSoundPassed)
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
    fun testCalculateLuminanceRewindsConsumedBuffer() {
        val bytes = ByteArray(100) { 35.toByte() }
        val buffer = ByteBuffer.wrap(bytes)
        // Simulate a buffer whose position was already advanced to limit
        buffer.position(buffer.limit())
        val luma = LiveTapeCheckEngine.calculateLuminance(buffer)
        assertEquals(35.0, luma, 1.0)
    }
}
