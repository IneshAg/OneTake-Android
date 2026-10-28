package com.onetake.app.ui.profile

import com.onetake.app.ui.camera.HeadshotFramingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadshotFramingEngineTest {

    @Test
    fun testCompute4by5CropRatioAndCentering() {
        // Standard phone photo: 3000 x 4000 (3:4 ratio)
        val bitmapWidth = 3000
        val bitmapHeight = 4000
        // Face roughly in the upper middle
        val faceLeft = 1200
        val faceTop = 1000
        val faceRight = 1800
        val faceBottom = 1800

        val cropRect = HeadshotFramingEngine.compute4by5CropRect(
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            faceLeft = faceLeft,
            faceTop = faceTop,
            faceRight = faceRight,
            faceBottom = faceBottom,
        )

        assertNotNull(cropRect)
        assertTrue("Crop width must fit inside bitmap", cropRect.width <= bitmapWidth)
        assertTrue("Crop height must fit inside bitmap", cropRect.height <= bitmapHeight)
        assertTrue("Crop left must be non-negative", cropRect.left >= 0)
        assertTrue("Crop top must be non-negative", cropRect.top >= 0)

        // Verify 4:5 aspect ratio (within 1 pixel rounding)
        val calculatedAspect = cropRect.width.toFloat() / cropRect.height.toFloat()
        assertEquals(0.8f, calculatedAspect, 0.015f)

        // Verify face center is contained inside the crop rect
        val faceCenterX = (faceLeft + faceRight) / 2
        val faceCenterY = (faceTop + faceBottom) / 2
        assertTrue(cropRect.contains(faceCenterX, faceCenterY))
    }

    @Test
    fun testCompute4by5CropWithoutFaceDefaultsToCenter() {
        val bitmapWidth = 1080
        val bitmapHeight = 1920

        val cropRect = HeadshotFramingEngine.compute4by5CropRect(bitmapWidth, bitmapHeight, faceBox = null)

        val expectedAspect = 0.8f
        val calculatedAspect = cropRect.width.toFloat() / cropRect.height.toFloat()
        assertEquals(expectedAspect, calculatedAspect, 0.015f)
        assertEquals((bitmapWidth - cropRect.width) / 2, cropRect.left)
        assertEquals((bitmapHeight - cropRect.height) / 2, cropRect.top)
    }

    @Test
    fun testEvaluateFramingWhenNoFacesDetected() {
        val guidance = HeadshotFramingEngine.evaluateFraming(
            faces = emptyList(),
            uprightWidth = 1080,
            uprightHeight = 1920,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Step into the frame", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
    }

    @Test
    fun testEvaluateFramingMoveCloserWhenFaceTooSmall() {
        // Face height = 200px in 1920px (normHeight ~ 0.10 < 0.26)
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 440,
            faceTop = 760,
            faceRight = 640,
            faceBottom = 960,
            uprightWidth = 1080,
            uprightHeight = 1920,
            yaw = 0f,
            roll = 0f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Move closer", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
    }

    @Test
    fun testEvaluateFramingStepBackWhenFaceTooLarge() {
        // Face height = 1400px in 1920px (normHeight ~ 0.73 > 0.68)
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 140,
            faceTop = 200,
            faceRight = 940,
            faceBottom = 1600,
            uprightWidth = 1080,
            uprightHeight = 1920,
            yaw = 0f,
            roll = 0f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Step back", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
    }

    @Test
    fun testEvaluateFramingRaiseCameraWhenFaceTooHigh() {
        // Face centerY at 300px in 1920px (screenY ~ 0.15 < 0.26)
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 240,
            faceTop = 50,
            faceRight = 840,
            faceBottom = 650,
            uprightWidth = 1080,
            uprightHeight = 1920,
            yaw = 0f,
            roll = 0f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Raise camera", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
    }

    @Test
    fun testEvaluateFramingLowerCameraWhenFaceTooLow() {
        // Face centerY at 1400px in 1920px (screenY ~ 0.73 > 0.60)
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 240,
            faceTop = 1100,
            faceRight = 840,
            faceBottom = 1700,
            uprightWidth = 1080,
            uprightHeight = 1920,
            yaw = 0f,
            roll = 0f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Lower camera", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
    }

    @Test
    fun testEvaluateFramingFaceCameraDirectlyWhenYawTurned() {
        // Front camera, yaw = 25 degrees (turned away)
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 240,
            faceTop = 500,
            faceRight = 840,
            faceBottom = 1200,
            uprightWidth = 1080,
            uprightHeight = 1920,
            yaw = 25f,
            roll = 0f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Face camera directly", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
    }

    @Test
    fun testEvaluateFramingKeepHeadLevelWhenRollTilted() {
        // Front camera, roll = 20 degrees (tilted head)
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 240,
            faceTop = 500,
            faceRight = 840,
            faceBottom = 1200,
            uprightWidth = 1080,
            uprightHeight = 1920,
            yaw = 0f,
            roll = 20f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Keep head level", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
    }

    @Test
    fun testEvaluateFramingHoldStillWhenCenteredAndReady() {
        // Ideal medium close-up headshot positioning:
        // Face centered horizontally, in upper-middle vertically, upright yaw and roll, good light
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 240,
            faceTop = 500,
            faceRight = 840,
            faceBottom = 1200,
            uprightWidth = 1080,
            uprightHeight = 1920,
            yaw = 2f,
            roll = 1f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
        )

        assertEquals("Hold still — you’re centred", guidance.headline)
        assertTrue(guidance.isCenteredAndReady)
        assertTrue(guidance.isAngleGood)
        assertTrue(guidance.isLightGood)
        assertEquals("square", guidance.angleLabel)
        assertEquals("soft and even", guidance.lightLabel)
    }
}
