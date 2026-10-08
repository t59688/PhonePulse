package com.aizeek.phonepulse

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryDataTruthTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `new sample between freshness ticks uses current clock`() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val clock = java.util.concurrent.atomic.AtomicLong(100_000)
        val firstClockRead = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = com.aizeek.phonepulse.battery.BatteryMonitorRepository(db, scope) {
            clock.get().also { firstClockRead.complete(Unit) }
        }
        val collecting = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
            repo.state.collect { }
        }
        try {
            // Wait for the first clock observation before submitting a newer sample.
            kotlinx.coroutines.withTimeout(5000) { firstClockRead.await() }
            clock.set(105_000)
            val source = object : com.aizeek.phonepulse.battery.BatteryTelemetrySource {
                override fun read(screenOn: Boolean, settings: com.aizeek.phonepulse.battery.BatteryMonitorSettings) =
                    com.aizeek.phonepulse.battery.BatteryTelemetry(123_456, 105_000, 105_000, 80,
                        false, 3, screenOn, -100_000.0)
            }
            repo.sample(source, true)
            val state = kotlinx.coroutines.withTimeout(5000) { repo.state.first { it.lastSampleTime == 123_456L } }
            assertNull(state.error)
            assertEquals(-100_000.0, state.currentUa!!, 0.0)
        } finally {
            collecting.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            db.close()
        }
    }

    @Test fun `missing broadcast must not fabricate percentage temperature or voltage`() {
        val current = BatteryRepository(context).readCurrentBattery(null)
        assertEquals(-1, current.percentage)
        assertNull(current.temperature)
        assertNull(current.voltageMv)
        assertEquals("未知", current.health)
    }

    @Test fun `missing sensor extras remain unknown when SOC is valid`() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 75).putExtra(BatteryManager.EXTRA_SCALE, 100)
        val current = BatteryRepository(context).readCurrentBattery(intent)
        assertEquals(75, current.percentage)
        assertNull(current.temperature)
        assertNull(current.voltageMv)
    }

    @Test fun `invalid SOC never enters history`() = runBlocking {
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        BatteryRepository(context).recordBatterySnapshot(LiveBatteryInfo(percentage = -1))
        assertNull(dao.getLatestRecordSync())
        dao.clearAll()
    }

    @Test fun `unknown sensors round trip as null without inventing units`() = runBlocking {
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        BatteryRepository(context).recordBatterySnapshot(LiveBatteryInfo(percentage = 80))
        val saved = dao.getLatestRecordSync()!!
        assertNull(saved.temperature)
        assertNull(saved.voltage)
        dao.clearAll()
    }

    @Test fun `historical record cannot overwrite live system SOC`() = runBlocking {
        val old = BatteryRecord(timestamp = 0, level = 17, percentage = 17, isCharging = false,
            plugType = "NONE", health = "未知", temperature = null, voltage = null,
            screenState = "SCREEN_ON", dateKey = "2026-10-08")
        val repo = BatteryRepository(context)
        val actual = repo.readCurrentBattery().percentage
        assertEquals(actual, repo.observeBatteryRecords(flowOf(listOf(old))).first().percentage)
    }

    @Test fun `freshness uses the current monotonic clock`() {
        val sample = com.aizeek.phonepulse.battery.BatteryTelemetry(0, 1000, 1000, 80,
            false, 3, true, null)
        assertTrue(com.aizeek.phonepulse.battery.isBatterySampleFresh(sample, 2000))
        assertFalse(com.aizeek.phonepulse.battery.isBatterySampleFresh(sample, 122_000))
        assertFalse(com.aizeek.phonepulse.battery.isBatterySampleFresh(sample, 0))
    }

    @Test fun `foreground share cannot be reported as measured app energy`() {
        val app = AppUsageInfo(packageName = "example", appName = "Example", totalTimeInForegroundMs = 1000, lastTimeUsedMs = 0)
        val records = listOf(
            BatteryRecord(timestamp = 0, level = 80, scale = 100, percentage = 80, isCharging = false,
                plugType = "NONE", health = "未知", temperature = 25f, voltage = 4000,
                screenState = "SCREEN_ON", dateKey = "2026-10-08"),
            BatteryRecord(timestamp = 60_000, level = 70, scale = 100, percentage = 70, isCharging = false,
                plugType = "NONE", health = "未知", temperature = 25f, voltage = 4000,
                screenState = "SCREEN_ON", dateKey = "2026-10-08"))
        assertNull(estimateAppBatteryUsage(listOf(app), records).single().estimatedDrainPct)
    }
}
