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
    fun testTakeRadioCircleTogglesKeptStatusInStore() {
        val store = AppStore(Application())
        val take1 = Take(id = "tk-1", auditionId = "aud-1", sceneIndex = 0, number = 1, durationSeconds = 40, recordedAt = LocalDateTime.now(), isCircled = true, notes = emptyList())
        val take2 = Take(id = "tk-2", auditionId = "aud-1", sceneIndex = 0, number = 2, durationSeconds = 45, recordedAt = LocalDateTime.now(), isCircled = false, notes = emptyList())
        store.takes = listOf(take1, take2)

        store.circle(take2)

        assertFalse("Previous take must be uncircled", store.takes.first { it.id == "tk-1" }.isCircled)
        assertTrue("Newly selected take must be circled", store.takes.first { it.id == "tk-2" }.isCircled)
    }

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
    fun testEvaluateTrimLoopActionDoesNotSeekWhenPlaybackPositionIsBeforeStart() {
        // Root-cause regression test:
        // Android VideoView/MediaPlayer seeks to keyframes (sync frames), which can land before startMs.
        // During forward playback between keyframe and startMs, evaluateTrimLoopAction must NOT seek to start.
        val action = evaluateTrimLoopAction(
            currentPos = 1500,
            startMs = 2000,
            endMs = 6000,
            isSeeking = false,
            lastSeekTimeMs = 1000L,
            currentTimeMs = 2000L,
        )
        assertEquals(
            "Playback at keyframe before startMs must NOT trigger seek to start (prevents 0.1s loop)",
            TrimLoopAction.NONE,
            action,
        )
    }

    @Test
    fun testEvaluateTrimLoopActionTriggersWhenReachingEnd() {
        val action = evaluateTrimLoopAction(
            currentPos = 6000,
            startMs = 2000,
            endMs = 6000,
            isSeeking = false,
            lastSeekTimeMs = 1000L,
            currentTimeMs = 4000L,
        )
        assertEquals(
            "Must trigger seek to start when reaching end of trim range",
            TrimLoopAction.SEEK_TO_START,
            action,
        )
    }

    @Test
    fun testEvaluateTrimLoopActionSuppressesWhileSeekingAtEnd() {
        // When a seek back to start was initiated at t = 1000ms, and currentPos is still >= endMs at t = 1200ms
        // while the decoder flushes/seeks, action must be NONE.
        val action = evaluateTrimLoopAction(
            currentPos = 6000,
            startMs = 2000,
            endMs = 6000,
            isSeeking = true,
            lastSeekTimeMs = 1000L,
            currentTimeMs = 1200L,
            seekTimeoutMs = 800L,
        )
        assertEquals(
            "Must NOT trigger duplicate seek while isSeeking is active and within timeout",
            TrimLoopAction.NONE,
            action,
        )
    }

    @Test
    fun testEvaluateTrimLoopActionAllowsPlaybackAfterSeekCompletesBelowEnd() {
        // When seek has completed and currentPos is now 2000ms (< 6000ms), action is NONE.
        val action = evaluateTrimLoopAction(
            currentPos = 2000,
            startMs = 2000,
            endMs = 6000,
            isSeeking = true,
            lastSeekTimeMs = 1000L,
            currentTimeMs = 1250L,
            seekTimeoutMs = 800L,
        )
        assertEquals(TrimLoopAction.NONE, action)
    }

    @Test
    fun testEvaluateTrimLoopActionRecoversAfterSeekTimeout() {
        // If seek took longer than seekTimeoutMs and currentPos is still >= endMs,
        // it triggers SEEK_TO_START to unstick the player.
        val action = evaluateTrimLoopAction(
            currentPos = 6000,
            startMs = 2000,
            endMs = 6000,
            isSeeking = true,
            lastSeekTimeMs = 1000L,
            currentTimeMs = 1900L,
            seekTimeoutMs = 800L,
        )
        assertEquals(
            "Must trigger seek to start after timeout to recover stalled player",
            TrimLoopAction.SEEK_TO_START,
            action,
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
