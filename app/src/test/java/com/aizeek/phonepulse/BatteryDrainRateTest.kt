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

    @Test fun `DAO retains charging boundaries and discharge after charging counts toward daily drain`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val records = listOf(
                sample(0, 95), sample(60_000, 85),
                sample(120_000, 85).copy(isCharging = true, plugged = true),
                sample(180_000, 95).copy(isCharging = true, plugged = true),
                sample(240_000, 95).copy(screenState = "SCREEN_OFF"),
                sample(300_000, 80).copy(screenState = "SCREEN_OFF")
            )
            records.reversed().forEach { db.batteryDao().insertRecord(it) }
            val saved = db.batteryDao().getRecordsForDateSync("2026-10-08")
            assertEquals(records.map { it.timestamp }, saved.map { it.timestamp })
            assertEquals(2, saved.count { it.isCharging })
            val result = BatteryRepository(context).observeBatteryRecords(flowOf(saved)).first()
            assertEquals(25, result.todayTotalDrainPct)
            assertEquals(10, result.totalScreenOnDrainPct)
            assertEquals(15, result.totalScreenOffDrainPct)
            assertEquals(result.todayTotalDrainPct, result.totalScreenOnDrainPct + result.totalScreenOffDrainPct)
        } finally { db.close() }
    }

    @Test fun `charging gain cannot cancel earlier or later discharge`() = runBlocking {
        val repo = BatteryRepository(ApplicationProvider.getApplicationContext<Context>())
        val result = repo.observeBatteryRecords(flowOf(listOf(
            sample(0, 50), sample(60_000, 40),
            sample(120_000, 40).copy(isCharging = true, plugged = true),
            sample(180_000, 80).copy(isCharging = true, plugged = true),
            sample(240_000, 80), sample(300_000, 70)
        ))).first()
        assertEquals(20, result.todayTotalDrainPct)
        assertEquals(20, result.totalScreenOnDrainPct)
    }

    @Test fun `multiple discharge cycles can exceed one hundred percentage points`() = runBlocking {
        val repo = BatteryRepository(ApplicationProvider.getApplicationContext<Context>())
        val records = listOf(100, 10, 100, 10).mapIndexed { i, pct -> sample(i * 60_000L, pct) }
        assertEquals(180, repo.observeBatteryRecords(flowOf(records)).first().todayTotalDrainPct)
    }

    @Test fun `unknown screen state leaves observed decline unattributed`() = runBlocking {
        val repo = BatteryRepository(ApplicationProvider.getApplicationContext<Context>())
        val result = repo.observeBatteryRecords(flowOf(listOf(
            sample(0, 80).copy(screenState = "UNKNOWN"), sample(60_000, 78)
        ))).first()
        assertEquals(2, result.todayTotalDrainPct)
        assertEquals(0, result.totalScreenOnDrainPct)
        assertEquals(0, result.totalScreenOffDrainPct)
        assertFalse(result.screenOffRateKnown)
    }
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
