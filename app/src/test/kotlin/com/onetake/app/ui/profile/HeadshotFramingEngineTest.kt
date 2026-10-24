package com.onetake.app.ui.profile

import com.onetake.app.ui.camera.HeadshotFramingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadshotFramingEngineTest {

    @Test
    fun testCalculateCropRectMaintainsFourByFiveAspectRatio() {
        val imageWidth = 3000
        val imageHeight = 4000
        // Face located in upper-middle
        val faceCenter = HeadshotFramingEngine.FaceCenter(1500f, 1400f)
        val rect = HeadshotFramingEngine.calculateCropRect(imageWidth, imageHeight, faceCenter, targetAspect = 4f / 5f)
        val aspect = rect.width.toFloat() / rect.height.toFloat()
        assertEquals(0.8f, aspect, 0.01f)
        assertTrue("Crop must be inside bitmap horizontally", rect.left >= 0 && rect.right <= imageWidth)
        assertTrue("Crop must be inside bitmap vertically", rect.top >= 0 && rect.bottom <= imageHeight)
        assertTrue("Crop must center around face center X", Math.abs(rect.centerX - 1500) < 50)
    }

    @Test
    fun testFallbackCropWhenNoFaceDetected() {
        val imageWidth = 1080
        val imageHeight = 1920
        // No face detected -> golden ratio upper-third center crop
        val rect = HeadshotFramingEngine.calculateCropRect(imageWidth, imageHeight, null, targetAspect = 4f / 5f)
        val aspect = rect.width.toFloat() / rect.height.toFloat()
        assertEquals(0.8f, aspect, 0.01f)
        assertTrue(rect.left >= 0 && rect.right <= imageWidth)
        assertTrue(rect.top >= 0 && rect.bottom <= imageHeight)
        assertEquals(imageWidth / 2, rect.centerX)
    }

    @Test
    fun testVoiceTriggerMatchesHeadshotPhrases() {
        val regex = HeadshotFramingEngine.VOICE_TRIGGER_REGEX
        val triggers = listOf("now", "Now!", "take", "shoot", "capture", "say cheese", "ready")
        for (phrase in triggers) {
            assertTrue("Expected phrase '$phrase' to match trigger", regex.containsMatchIn(phrase))
        }

        val nonTriggers = listOf("hello", "what is this", "looking good", "hold on")
        for (phrase in nonTriggers) {
            assertTrue("Expected phrase '$phrase' NOT to match trigger", !regex.containsMatchIn(phrase))
        }
    }
}
