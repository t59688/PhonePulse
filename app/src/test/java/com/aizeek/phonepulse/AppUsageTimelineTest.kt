package com.aizeek.phonepulse

import com.aizeek.phonepulse.data.*
import org.junit.Assert.*
import org.junit.Test

class AppUsageTimelineTest {
    private val hour = 3_600_000L
    private fun event(time: Long, type: UsageTimelineEventType, activity: String = "Main") =
        UsageTimelineEvent(time, type, activity)

    @Test fun `midnight clips ongoing session and splits exact usage by hour`() {
        val timeline = buildAppUsageTimeline(0, 2 * hour + hour / 2, listOf(
            event(-hour / 2, UsageTimelineEventType.RESUME), event(hour / 2, UsageTimelineEventType.PAUSE),
            event(hour + hour / 2, UsageTimelineEventType.RESUME)))
        assertEquals(listOf(hour / 2, hour / 2, hour / 2), timeline.hours.map { it.durationMs })
        assertEquals(hour + hour / 2, timeline.totalDurationMs)
        assertEquals(0L, timeline.sessions.first().startMs)
        assertEquals(2 * hour + hour / 2, timeline.sessions.last().endMs)
    }

    @Test fun `multiple resumed activities count union rather than double usage`() {
        val timeline = buildAppUsageTimeline(0, hour, listOf(
            event(0, UsageTimelineEventType.RESUME, "Main"), event(hour / 4, UsageTimelineEventType.RESUME, "Other"),
            event(hour / 2, UsageTimelineEventType.PAUSE, "Main"), event(hour, UsageTimelineEventType.PAUSE, "Other")))
        assertEquals(hour, timeline.totalDurationMs)
        assertEquals(1, timeline.sessions.size)
    }

    @Test fun `screen off and device restart close usage without filling missing starts`() {
        val timeline = buildAppUsageTimeline(0, hour, listOf(
            event(10, UsageTimelineEventType.PAUSE), event(100, UsageTimelineEventType.RESUME),
            event(200, UsageTimelineEventType.RESET), event(400, UsageTimelineEventType.PAUSE),
            event(500, UsageTimelineEventType.RESUME), event(700, UsageTimelineEventType.RESET)))
        assertEquals(300L, timeline.totalDurationMs)
        assertEquals(2, timeline.sessions.size)
    }

    @Test fun `duplicate events and future events do not inflate usage`() {
        val timeline = buildAppUsageTimeline(0, hour, listOf(
            event(0, UsageTimelineEventType.RESUME), event(10, UsageTimelineEventType.RESUME),
            event(hour / 2, UsageTimelineEventType.PAUSE), event(2 * hour, UsageTimelineEventType.RESUME)))
        assertEquals(hour / 2, timeline.totalDurationMs)
        assertEquals(1, timeline.hours.size)
        assertEquals(hour, timeline.hours.single().endMs)
    }
}
