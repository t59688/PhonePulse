package com.aizeek.phonepulse

import com.aizeek.phonepulse.data.HourlyScreenStat
import com.aizeek.phonepulse.data.ScreenSession
import com.aizeek.phonepulse.data.ScreenStateRepository
import com.aizeek.phonepulse.service.ScreenStateHolder
import com.aizeek.phonepulse.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScreenStatsTest {

    @Test
    fun testSplitSessionByDay_sameDay() {
        val start = TimeFormatter.getStartOfDay() + 10 * 3600_000L // 10:00
        val end = start + 30 * 60_000L // 10:30
        val session = ScreenSession(
            type = "SCREEN_ON",
            startTime = start,
            endTime = end,
            durationMs = 30 * 60_000L,
            dateKey = TimeFormatter.dateKey(start)
        )

        val split = ScreenStateHolder.splitSessionByDay(session)
        assertEquals(1, split.size)
        assertEquals(start, split[0].startTime)
        assertEquals(end, split[0].endTime)
        assertEquals(30 * 60_000L, split[0].durationMs)
    }

    @Test
    fun testSplitSessionByDay_crossingMidnight() {
        val day1Start = TimeFormatter.getStartOfDay() - 24 * 3600_000L // Yesterday 00:00
        val start = day1Start + 23 * 3600_000L + 30 * 60_000L // Yesterday 23:30
        val end = start + 60 * 60_000L // Today 00:30
        val session = ScreenSession(
            type = "SCREEN_ON",
            startTime = start,
            endTime = end,
            durationMs = 60 * 60_000L,
            dateKey = TimeFormatter.dateKey(start)
        )

        val split = ScreenStateHolder.splitSessionByDay(session)
        assertEquals(2, split.size)

        // Part 1: Yesterday 23:30 to 24:00 (30 min)
        assertEquals(TimeFormatter.dateKey(day1Start), split[0].dateKey)
        assertEquals(start, split[0].startTime)
        assertEquals(day1Start + 24 * 3600_000L, split[0].endTime)
        assertEquals(30 * 60_000L, split[0].durationMs)

        // Part 2: Today 00:00 to 00:30 (30 min)
        val day2Start = day1Start + 24 * 3600_000L
        assertEquals(TimeFormatter.dateKey(day2Start), split[1].dateKey)
        assertEquals(day2Start, split[1].startTime)
        assertEquals(end, split[1].endTime)
        assertEquals(30 * 60_000L, split[1].durationMs)
    }

    @Test
    fun testCalculateHourlyBreakdown_noPhantomFutureOrYesterdayBars() {
        // Mock a Context-less test of calculateHourlyBreakdown
        // Suppose current time is 08:33 AM today
        val todayStart = TimeFormatter.getStartOfDay()
        val nowMs = todayStart + 8 * 3600_000L + 33 * 60_000L // 08:33 AM
        val todayKey = TimeFormatter.dateKey(todayStart)

        // Suppose yesterday had an 18:00 session (21 minutes)
        val yesterdayStart = todayStart - 24 * 3600_000L
        val yesterday18 = yesterdayStart + 18 * 3600_000L

        // And today had a session from 07:45 to 08:15 (30 minutes: 15 min in h=7, 15 min in h=8)
        val todaySession = ScreenSession(
            type = "SCREEN_ON",
            startTime = todayStart + 7 * 3600_000L + 45 * 60_000L,
            endTime = todayStart + 8 * 3600_000L + 15 * 60_000L,
            durationMs = 30 * 60_000L,
            dateKey = todayKey
        )

        // If yesterday session was erroneously passed into today's list
        val yesterdaySession = ScreenSession(
            type = "SCREEN_ON",
            startTime = yesterday18,
            endTime = yesterday18 + 21 * 60_000L,
            durationMs = 21 * 60_000L,
            dateKey = TimeFormatter.dateKey(yesterdayStart)
        )

        val result = ScreenStateRepository.calculateHourlyBreakdown(
            dateKey = todayKey,
            sessions = listOf(todaySession, yesterdaySession),
            activeSessionStartMs = null,
            nowMs = nowMs
        )

        assertEquals(24, result.size)

        // Hour 7 should have 15 minutes
        val h7 = result.find { it.hour == 7 }!!
        assertEquals(15f, h7.screenOnMinutes, 0.01f)

        // Hour 8 should have 15 minutes
        val h8 = result.find { it.hour == 8 }!!
        assertEquals(15f, h8.screenOnMinutes, 0.01f)

        // Hour 18 MUST HAVE 0 MINUTES (yesterday's 18:00 does not bleed into today's 18:00)
        val h18 = result.find { it.hour == 18 }!!
        assertEquals(0f, h18.screenOnMinutes, 0.01f)

        // All hours > 8 must be 0 minutes
        for (h in 9..23) {
            assertEquals("Hour $h should be 0", 0f, result.find { it.hour == h }!!.screenOnMinutes, 0.01f)
        }
    }

    @Test
    fun testCalculateHourlyBreakdown_multiHourSession() {
        val todayStart = TimeFormatter.getStartOfDay()
        val todayKey = TimeFormatter.dateKey(todayStart)
        val nowMs = todayStart + 16 * 3600_000L // 16:00

        // Continuous session from 13:30 to 15:30 (2 hours total: 30m in h=13, 60m in h=14, 30m in h=15)
        val longSession = ScreenSession(
            type = "SCREEN_ON",
            startTime = todayStart + 13 * 3600_000L + 30 * 60_000L,
            endTime = todayStart + 15 * 3600_000L + 30 * 60_000L,
            durationMs = 120 * 60_000L,
            dateKey = todayKey
        )

        val result = ScreenStateRepository.calculateHourlyBreakdown(
            dateKey = todayKey,
            sessions = listOf(longSession),
            activeSessionStartMs = null,
            nowMs = nowMs
        )

        val h13 = result.find { it.hour == 13 }!!
        val h14 = result.find { it.hour == 14 }!!
        val h15 = result.find { it.hour == 15 }!!

        assertEquals(30f, h13.screenOnMinutes, 0.01f)
        assertEquals(60f, h14.screenOnMinutes, 0.01f)
        assertEquals(30f, h15.screenOnMinutes, 0.01f)
    }
}
