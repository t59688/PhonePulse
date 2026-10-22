package com.aizeek.phonepulse

import com.aizeek.phonepulse.service.ScreenBoundary
import com.aizeek.phonepulse.service.ScreenTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimelineTest {
    @Test
    fun recoversMissedOffAndOnWhileProcessWasDead() {
        val result = ScreenTimeline.replay(
            initialScreenOn = true,
            initialStartMs = 1000,
            boundaries = listOf(ScreenBoundary(false, 2000), ScreenBoundary(true, 10000)),
            untilMs = 11000
        )
        assertTrue(result.screenOn)
        assertEquals(10000L, result.startMs)
        assertEquals(2, result.completed.size)
        assertEquals(1000L, result.completed[0].startMs)
        assertEquals(2000L, result.completed[0].endMs)
        assertEquals(false, result.completed[1].screenOn)
        assertEquals(8000L, result.completed[1].endMs - result.completed[1].startMs)
    }

    @Test
    fun repeatedBroadcastsAndReorderedHistoryDoNotDuplicate() {
        val result = ScreenTimeline.replay(
            true, 1000,
            listOf(
                ScreenBoundary(true, 4000),
                ScreenBoundary(false, 3000),
                ScreenBoundary(false, 3000),
                ScreenBoundary(true, 4000),
                ScreenBoundary(false, 900),
                ScreenBoundary(false, 9000)
            ), 5000
        )
        assertTrue(result.screenOn)
        assertEquals(4000L, result.startMs)
        assertEquals(2, result.completed.size)
    }

    @Test
    fun noObservedTransitionsLeavesOngoingIntervalUnchanged() {
        val result = ScreenTimeline.replay(false, 500, emptyList(), 900000)
        assertEquals(false, result.screenOn)
        assertEquals(500L, result.startMs)
        assertTrue(result.completed.isEmpty())
    }
}
