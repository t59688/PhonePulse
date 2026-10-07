package com.aizeek.phonepulse

import android.content.Context
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
    fun `battery state observes snapshots written by the tracking service repository`() = runBlocking {
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
        assertEquals(42, repository.liveBattery.first().percentage)
        assertEquals(true, repository.liveBattery.first().isCharging)
        dao.clearAll()
    }
}
