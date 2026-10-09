package com.aizeek.phonepulse

import com.aizeek.phonepulse.companion.CompanionInsights
import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.ScreenSession
import org.junit.Assert.*
import org.junit.Test

class CompanionInsightsTest {
    @Test fun `brief repeated checks suggest waiting without asserting user activity`() {
        val now = 10_000_000L
        val sessions = (1..5).map { i -> ScreenSession(type = "SCREEN_ON", startTime = now - i * 180_000,
            endTime = now - i * 180_000 + 60_000, durationMs = 60_000, dateKey = "2026-10-09") }
        val text = CompanionInsights.describe(sessions, null, now, false)
        assertTrue(text.contains("可能在等消息"))
        assertFalse(text.contains("依据："))
    }

    @Test fun `video category is a qualified daily inference not a continuous duration claim`() {
        val text = CompanionInsights.describe(emptyList(),
            AppUsageInfo("tv.danmaku.bili", "哔哩哔哩", 3_600_000, 0), 10_000_000, true)
        assertTrue(text.contains("可能看了些视频"))
        assertFalse(text.contains("依据："))
    }

    @Test fun `unknown app does not automatically mean video or work`() {
        val text = CompanionInsights.describe(emptyList(), AppUsageInfo("unknown", "未知", 3_600_000, 0), 10_000_000, true)
        assertFalse(text.contains("看了些视频"))
        assertFalse(text.contains("忙工作"))
    }
}
