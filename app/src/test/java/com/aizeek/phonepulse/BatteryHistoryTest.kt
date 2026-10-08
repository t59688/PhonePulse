package com.aizeek.phonepulse

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.data.AppDatabase
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.data.BatteryRepository
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryHistoryTest {
    @Test
    fun `battery state observes system updates while retaining service history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        val repository = BatteryRepository(context)
        val now = System.currentTimeMillis()
        dao.insertRecord(BatteryRecord(
            timestamp = now, level = 42, percentage = 42, isCharging = true,
            plugType = "USB 充电", health = "良好", temperature = 30f,
            voltage = 4100, screenState = "SCREEN_ON", dateKey = TimeFormatter.dateKey(now)
        ))
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 55)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals(55, repository.liveBattery.first { it.percentage == 55 }.percentage)
        assertEquals(true, repository.liveBattery.first().isCharging)
        assertEquals(42, dao.getLatestRecordSync()!!.percentage)
        dao.clearAll()
    }
}
