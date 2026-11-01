package com.onetake.app.ui.takes

import android.app.Application
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
        val store = AppStore(Application())
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
        val store = AppStore(Application())
        val initialCount = store.takes.size
        val newTake = Take(number = 99, durationSeconds = 45, recordedAt = LocalDateTime.now(), isCircled = false, notes = emptyList(), fileUri = "content://media/take-99.mp4")
        store.addTake(newTake)

        assertEquals(initialCount + 1, store.takes.size)
        assertEquals("content://media/take-99.mp4", store.takes.last().fileUri)
    }

    @Test
    fun testEvaluateTrimLoopActionPreventsTightLoopDuringSeekGracePeriod() {
        // Start: 2000ms, End: 6000ms.
        // A seek to start was initiated at t = 1000ms.
        val lastSeekTime = 1000L

        // At t = 1100ms (100ms later), the player is still seeking and reports curPos = 0.
        // In the old broken code, curPos < 2000 immediately re-triggered seek every 100ms.
        val action1 = evaluateTrimLoopAction(
            currentPos = 0,
            startMs = 2000,
            endMs = 6000,
            lastSeekTimeMs = lastSeekTime,
            currentTimeMs = 1100L,
            seekGracePeriodMs = 350L,
        )
        assertEquals(
            "Must NOT trigger seekTo while within seek grace period (prevents 0.1s loop)",
            TrimLoopAction.NONE,
            action1,
        )

        // At t = 1400ms (400ms later), seek finished and curPos is 2100ms (inside range).
        val action2 = evaluateTrimLoopAction(
            currentPos = 2100,
            startMs = 2000,
            endMs = 6000,
            lastSeekTimeMs = lastSeekTime,
            currentTimeMs = 1400L,
            seekGracePeriodMs = 350L,
        )
        assertEquals(TrimLoopAction.NONE, action2)

        // At t = 4000ms, playback reaches the end (6000ms).
        val action3 = evaluateTrimLoopAction(
            currentPos = 6000,
            startMs = 2000,
            endMs = 6000,
            lastSeekTimeMs = lastSeekTime,
            currentTimeMs = 4000L,
            seekGracePeriodMs = 350L,
        )
        assertEquals(
            "Must trigger seek to start when reaching end of trim range",
            TrimLoopAction.SEEK_TO_START,
            action3,
        )
    }

    @Test
    fun testClampTrimRangeEnforcesMinimumDuration() {
        // Total duration: 10000ms. Minimum duration: 500ms.
        val currentRange = 1000f..5000f

        // User attempts to collapse range to 0.1s (100ms)
        val collapsed = 2000f..2100f
        val clamped = clampTrimRange(collapsed, currentRange, 10000f, minDurationMs = 500f)
        assertTrue(
            "Clamped range must be at least 500ms",
            clamped.endInclusive - clamped.start >= 500f,
        )

        // Valid range is preserved
        val valid = 1500f..4000f
        val clampedValid = clampTrimRange(valid, currentRange, 10000f, minDurationMs = 500f)
        assertEquals(1500f, clampedValid.start, 0.01f)
        assertEquals(4000f, clampedValid.endInclusive, 0.01f)
    }
}
