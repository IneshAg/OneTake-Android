package com.onetake.app.ui.profile

import androidx.exifinterface.media.ExifInterface
import com.onetake.app.ui.camera.HeadshotFramingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadshotFramingEngineTest {

    @Test
    fun testCompute4by5CropRatioAndCentering() {
        val bitmapWidth = 3000
        val bitmapHeight = 4000
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

        val calculatedAspect = cropRect.width.toFloat() / cropRect.height.toFloat()
        assertEquals(0.8f, calculatedAspect, 0.015f)

        val faceCenterX = (faceLeft + faceRight) / 2
        val faceCenterY = (faceTop + faceBottom) / 2
        assertTrue(cropRect.contains(faceCenterX, faceCenterY))
    }

    @Test
    fun testManualPanZoom4by5CropClampsSafelyWithinBitmap() {
        val cropRect = HeadshotFramingEngine.computeManual4by5CropRect(
            bitmapWidth = 2000,
            bitmapHeight = 3000,
            zoom = 1.5f,
            panXNorm = 0.8f, // push right
            panYNorm = -0.8f, // push up
        )
        assertTrue(cropRect.left >= 0)
        assertTrue(cropRect.top >= 0)
        assertTrue(cropRect.right <= 2000)
        assertTrue(cropRect.bottom <= 3000)
        assertEquals(0.8f, cropRect.width.toFloat() / cropRect.height.toFloat(), 0.015f)
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
    fun testFillCenterViewportPreventsFalseCenteredWhenOffOvalOnTallDisplay() {
        // 480 x 640 (4:3 = 0.75) sensor frame on 20:9 (0.45) PreviewView (FILL_CENTER).
        // Face center at raw X = 185 / 480 = 0.385 -> mirrored = 0.615.
        // Without FILL_CENTER mapping, 0.615 was <= 0.62 and falsely said "Hold still — you're centred"!
        // On the visible 20:9 screen, screenX = (0.615 - 0.20) / 0.60 = 0.691 (noticeably right of oval!).
        val guidance = HeadshotFramingEngine.evaluateFramingMetrics(
            faceLeft = 105,
            faceTop = 180,
            faceRight = 265,
            faceBottom = 410,
            uprightWidth = 480,
            uprightHeight = 640,
            yaw = 0f,
            roll = 0f,
            targetAngle = HeadshotFramingEngine.HeadshotAngle.Front,
            isFrontCamera = true,
            lightLabel = "soft and even",
            isLightGood = true,
            viewAspect = 0.45f,
        )

        assertEquals("Move left", guidance.headline)
        assertFalse(guidance.isCenteredAndReady)
        assertNotNull(guidance.faceBoxNorm)
        assertEquals(0.691f, guidance.faceBoxNorm!!.centerX, 0.02f)
    }

    @Test
    fun testExifOrientationAndMirrorTransformsHandleAllSensors() {
        // Samsung front camera TRANSVERSE (7) = mirror + 270 deg
        val transverse = HeadshotFramingEngine.resolveExifTransform(
            exifOrientation = ExifInterface.ORIENTATION_TRANSVERSE,
            extraHorizontalMirror = false,
        )
        assertEquals(270f, transverse.rotationDegrees, 0.01f)
        assertTrue(transverse.flipHorizontal)

        // Front camera without EXIF flip + extraHorizontalMirror = true -> flips horizontally to match selfie preview
        val selfieNormal = HeadshotFramingEngine.resolveExifTransform(
            exifOrientation = ExifInterface.ORIENTATION_ROTATE_270,
            extraHorizontalMirror = true,
        )
        assertEquals(270f, selfieNormal.rotationDegrees, 0.01f)
        assertTrue(selfieNormal.flipHorizontal)

        // Double horizontal mirror cancels out cleanly
        val doubleMirror = HeadshotFramingEngine.resolveExifTransform(
            exifOrientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            extraHorizontalMirror = true,
        )
        assertEquals(0f, doubleMirror.rotationDegrees, 0.01f)
        assertFalse(doubleMirror.flipHorizontal)
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
