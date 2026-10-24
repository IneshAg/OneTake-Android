package com.onetake.app.ui.takes

import com.onetake.app.AppStore
import com.onetake.app.models.Take
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TakesReviewTest {
    @Test
    fun testCircleTakeTogglesCircledStatusExclusively() {
        val store = AppStore()
        val take1 = Take(id = "tk-1", number = 1, durationSeconds = 65, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList())
        val take2 = Take(id = "tk-2", number = 2, durationSeconds = 72, recordedAt = LocalDateTime.now(), isCircled = false, notes = emptyList())
        store.takes = listOf(take1, take2)

        store.circle(take2)

        assertFalse(store.takes.first { it.id == "tk-1" }.isCircled)
        assertTrue(store.takes.first { it.id == "tk-2" }.isCircled)
    }

    @Test
    fun testFormatTakeTimestamp() {
        val seconds = 85
        val formatted = "%02d:%02d".format(seconds / 60, seconds % 60)
        assertEquals("01:25", formatted)
    }

    @Test
    fun testAddTakeAppendsToStore() {
        val store = AppStore()
        val initialCount = store.takes.size
        val newTake = Take(number = 99, durationSeconds = 45, recordedAt = LocalDateTime.now(), isCircled = false, notes = emptyList(), fileUri = "content://media/take-99.mp4")
        store.addTake(newTake)

        assertEquals(initialCount + 1, store.takes.size)
        assertEquals("content://media/take-99.mp4", store.takes.last().fileUri)
    }

    @Test
    fun testCircleTakeIsScopedToScene() {
        val store = AppStore()
        val takeScene1A = Take(id = "tk-1a", number = 1, durationSeconds = 60, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList(), sceneId = "scene-1")
        val takeScene1B = Take(id = "tk-1b", number = 2, durationSeconds = 65, recordedAt = LocalDateTime.now(), isCircled = false, notes = emptyList(), sceneId = "scene-1")
        val takeScene2A = Take(id = "tk-2a", number = 1, durationSeconds = 70, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList(), sceneId = "scene-2")

        store.takes = listOf(takeScene1A, takeScene1B, takeScene2A)

        // Circle takeScene1B: should uncircle 1A, but preserve 2A
        store.circle(takeScene1B)

        assertFalse(store.takes.first { it.id == "tk-1a" }.isCircled)
        assertTrue(store.takes.first { it.id == "tk-1b" }.isCircled)
        assertTrue("Take in scene-2 should remain circled", store.takes.first { it.id == "tk-2a" }.isCircled)
    }
}
