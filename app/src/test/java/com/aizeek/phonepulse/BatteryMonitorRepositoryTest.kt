package com.aizeek.phonepulse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.battery.*
import com.aizeek.phonepulse.data.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryMonitorRepositoryTest {
    private class Source(var telemetry: BatteryTelemetry) : BatteryTelemetrySource {
        override fun read(screenOn: Boolean, settings: BatteryMonitorSettings) = telemetry.copy(screenOn = screenOn)
    }
    private fun sample(t: Long, pct: Int = 80, plugged: Boolean = true) =
        BatteryTelemetry(t, t, t, pct, plugged, 2, true, 1_000_000.0)

    private fun withRepository(block: suspend (AppDatabase, BatteryMonitorRepository, CoroutineScope) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { block(db, BatteryMonitorRepository(db, scope), scope) }
        finally { scope.cancel(); db.close() }
    }

    @Test fun `legacy short measurements are recovered for UI without mutating persisted history`() = withRepository { db, repo, _ ->
        val legacy = BatteryCycle(charging = true, startTime = 0, endTime = 3_600_000,
            lastTime = 3_600_000, lastElapsedMs = 3_600_000, startPct = 30, endPct = 60,
            netMah = 1200.0, measuredMs = 3_600_000, rejectionReason = "SMALL_SOC_CHANGE")
        val id = db.batteryMonitorDao().saveCycle(legacy)
        val state = withTimeout(5000) { repo.state.first { it.health.acceptedCount == 1 } }
        assertEquals(4000.0, state.health.capacityMah!!, 0.000001)
        assertNull(state.cycles.single().rejectionReason)
        assertEquals(4000.0, state.cycles.single().estimatedCapacityMah!!, 0.000001)
        val saved = db.batteryMonitorDao().getCycle(id)!!
        assertEquals("SMALL_SOC_CHANGE", saved.rejectionReason)
        assertNull(saved.estimatedCapacityMah)
    }

    @Test fun `charge alarm is claimed once across repository restart`() = withRepository { db, repo, scope ->
        repo.saveSettings(BatteryMonitorSettings(chargeAlarmEnabled = true))
        val source = Source(sample(100_000))
        val cycle = repo.sample(source, true)!!
        assertNotNull(repo.claimChargeAlarm(cycle.id))
        assertNull(repo.claimChargeAlarm(cycle.id))
        repo.stop()
        val restored = BatteryMonitorRepository(db, scope)
        source.telemetry = sample(110_000)
        val resumed = restored.sample(source, true)!!
        assertEquals(cycle.id, resumed.id)
        assertNull(restored.claimChargeAlarm(resumed.id))
    }

    @Test fun `muted session is silent until power boundary`() = withRepository { _, repo, _ ->
        repo.saveSettings(BatteryMonitorSettings(chargeAlarmEnabled = true))
        val source = Source(sample(100_000))
        repo.sample(source, true)
        repo.muteAlarm()
        assertNull(repo.claimChargeAlarm(repo.sample(source, true)!!.id))
        source.telemetry = sample(110_000, plugged = false)
        repo.sample(source, true)
        source.telemetry = sample(120_000)
        assertNotNull(repo.claimChargeAlarm(repo.sample(source, true)!!.id))
    }

    @Test fun `settings target race does not alert using an obsolete target`() = withRepository { _, repo, _ ->
        repo.saveSettings(BatteryMonitorSettings(chargeAlarmEnabled = true, chargeTargetPct = 80))
        val cycle = repo.sample(Source(sample(100_000, pct = 95)), true)!!
        repo.saveSettings(BatteryMonitorSettings(chargeAlarmEnabled = true, chargeTargetPct = 90))
        assertNull(repo.claimChargeAlarm(cycle.id, expectedTarget = 80))
        assertNotNull(repo.claimChargeAlarm(cycle.id, expectedTarget = 90))
    }

    @Test fun `old service owner cannot stop or sample a restarted service`() = withRepository { _, repo, _ ->
        val old = repo.beginMonitoring()
        repo.sample(Source(sample(100_000)), true, old)
        val current = repo.beginMonitoring()
        repo.sample(Source(sample(105_000)), true, current)
        repo.stop(old)
        assertNull(repo.sample(Source(sample(110_000)), true, old))
        assertNotNull(repo.sample(Source(sample(110_000)), true, current))
    }

    @Test fun `minute checkpoint flush and raw calibration happen once`() = withRepository { db, repo, _ ->
        repo.saveSettings(BatteryMonitorSettings(currentScale = 2.0))
        val source = Source(sample(100_000))
        repo.sample(source, true)
        source.telemetry = sample(103_600)
        val cycle = repo.sample(source, true)!!
        assertEquals(2.0, cycle.netMah, 0.000001)
        repo.stop()
        assertEquals(2.0, db.batteryMonitorDao().getActiveCycle()!!.netMah, 0.000001)
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT * FROM battery_intervals").use { it.count })
    }

    @Test fun `design capacity change preserves calibration revision and measurements`() = withRepository { db, repo, _ ->
        repo.saveSettings(BatteryMonitorSettings(designCapacityMah = 4000.0))
        repo.saveSettings(BatteryMonitorSettings(designCapacityMah = 4500.0))
        assertEquals(0, db.batteryMonitorDao().getState()!!.settings.calibrationRevision)
        repo.saveSettings(BatteryMonitorSettings(designCapacityMah = 4500.0, invertCurrent = true))
        assertEquals(1, db.batteryMonitorDao().getState()!!.settings.calibrationRevision)
    }
}
