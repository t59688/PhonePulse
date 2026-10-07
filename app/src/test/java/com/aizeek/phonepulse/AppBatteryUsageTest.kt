package com.aizeek.phonepulse

import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.data.estimateAppBatteryUsage
import org.junit.Assert.*
import org.junit.Test

class AppBatteryUsageTest {
    private val apps = listOf(
        AppUsageInfo("first.app", "First", 180_000, 0),
        AppUsageInfo("second.app", "Second", 60_000, 0)
    )

    private fun sample(time: Long, level: Int, charging: Boolean = false, screen: String = "SCREEN_ON") =
        BatteryRecord(timestamp = time, level = level, percentage = level, isCharging = charging,
            plugType = "NONE", health = "GOOD", temperature = 25f, voltage = 4000,
            screenState = screen, dateKey = "2026-10-07")

    @Test fun `foreground estimates share recorded discharge without assigning standby to apps`() {
        val records = listOf(sample(0, 100), sample(60_000, 96),
            sample(120_000, 96, screen = "SCREEN_OFF"), sample(180_000, 90))
        val estimates = estimateAppBatteryUsage(apps, records)
        assertEquals(3f, estimates[0].estimatedDrainPct!!, 0.001f)
        assertEquals(1f, estimates[1].estimatedDrainPct!!, 0.001f)
    }

    @Test fun `flat battery produces zero rather than an invented minimum`() {
        val estimates = estimateAppBatteryUsage(apps, listOf(sample(0, 100), sample(60_000, 100)))
        assertEquals(0f, estimates[0].estimatedDrainPct!!, 0f)
        assertEquals(0f, estimates[1].estimatedDrainPct!!, 0f)
    }

    @Test fun `missing samples and zero usage have no estimate`() {
        assertNull(estimateAppBatteryUsage(apps, emptyList())[0].estimatedDrainPct)
        assertNull(estimateAppBatteryUsage(apps, listOf(sample(0, 100)))[0].estimatedDrainPct)
        val unused = listOf(apps[0].copy(totalTimeInForegroundMs = 0))
        assertNull(estimateAppBatteryUsage(unused, listOf(sample(0, 100), sample(60_000, 96)))[0].estimatedDrainPct)
    }

    @Test fun `charging gaps and reversed timestamps are not counted as discharge`() {
        val records = listOf(sample(0, 90, true), sample(60_000, 80), sample(60_000, 75), sample(120_000, 75, true))
        assertNull(estimateAppBatteryUsage(apps, records)[0].estimatedDrainPct)
    }
}
