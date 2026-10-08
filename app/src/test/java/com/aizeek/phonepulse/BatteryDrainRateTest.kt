package com.aizeek.phonepulse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryDrainRateTest {
    private fun sample(t: Long, pct: Int) = BatteryRecord(timestamp = t, level = pct,
        percentage = pct, isCharging = false, plugType = "NONE", health = "GOOD", temperature = 25f,
        voltage = 4000, screenState = "SCREEN_ON", dateKey = "2026-10-08", plugged = false)
    @Test fun `unknown plug state cannot establish an unplugged discharge rate`() = runBlocking {
        val repo = BatteryRepository(ApplicationProvider.getApplicationContext<Context>())
        val result = repo.observeBatteryRecords(flowOf(listOf(
            sample(0, 80).copy(plugged = null), sample(60_000, 79).copy(plugged = null)))).first()
        assertFalse(result.screenOnRateKnown)
        assertEquals(1, result.todayTotalDrainPct)
    }
    @Test fun `flat intervals remain in measured drain rate denominator`() = runBlocking {
        val repo = BatteryRepository(ApplicationProvider.getApplicationContext<Context>())
        val records = (0..8).map { sample(it * 900_000L, if (it == 8) 78 else 80) }
        val result = repo.observeBatteryRecords(flowOf(records)).first()
        assertEquals(1f, result.screenOnDrainPerHour, 0.001f)
        assertTrue(result.screenOnRateKnown)
    }

    @Test fun `long history gap cannot establish a screen drain rate`() = runBlocking {
        val repo = BatteryRepository(ApplicationProvider.getApplicationContext<Context>())
        val result = repo.observeBatteryRecords(flowOf(listOf(sample(0, 80), sample(7_200_000, 60)))).first()
        assertEquals(20, result.todayTotalDrainPct)
        assertTrue(result.todayDrainKnown)
        assertFalse(result.screenOnRateKnown)
    }
    @Test fun `one battery reading cannot fabricate daily drain or rates`() = runBlocking {
        val repo = BatteryRepository(ApplicationProvider.getApplicationContext<Context>())
        val result = repo.observeBatteryRecords(flowOf(listOf(sample(0, 45)))).first()
        assertEquals(0, result.todayTotalDrainPct)
        assertFalse(result.screenOnRateKnown)
        assertFalse(result.screenOffRateKnown)
    }
}
