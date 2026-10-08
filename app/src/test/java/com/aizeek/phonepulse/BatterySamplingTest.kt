package com.aizeek.phonepulse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.data.AppDatabase
import com.aizeek.phonepulse.data.BatteryRepository
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatterySamplingTest {
    @Test
    fun `unchanged battery broadcasts do not write duplicate samples`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        val repository = BatteryRepository(context)
        repository.recordBatterySnapshot(LiveBatteryInfo(percentage = 80))
        repository.recordBatterySnapshot(LiveBatteryInfo(percentage = 80))
        assertEquals(1, dao.getRecordsForDateSync(TimeFormatter.todayKey()).size)
        dao.clearAll()
    }

    @Test
    fun `concurrent service and UI sampling only writes one unchanged point`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        val serviceRepository = BatteryRepository(context)
        val uiRepository = BatteryRepository(context)
        coroutineScope {
            repeat(10) {
                launch { serviceRepository.recordBatterySnapshot(LiveBatteryInfo(percentage = 80)) }
                launch { uiRepository.recordBatterySnapshot(LiveBatteryInfo(percentage = 80)) }
            }
        }
        assertEquals(1, dao.getRecordsForDateSync(TimeFormatter.todayKey()).size)
        dao.clearAll()
    }

    @Test
    fun `discharge is preserved even when the last sample was just written`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        val repository = BatteryRepository(context)
        val current = LiveBatteryInfo(percentage = 80)
        repository.recordBatterySnapshot(current = current)
        val initial = dao.getLatestRecordSync()!!
        dao.clearAll()
        dao.insertRecord(initial.copy(id = 0, percentage = 81))
        repository.recordBatterySnapshot(current = current)
        assertEquals(2, dao.getRecordsForDateSync(TimeFormatter.todayKey()).size)
        dao.clearAll()
    }

    @Test
    fun `screen and charging boundaries are never throttled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        val repository = BatteryRepository(context)
        val current = LiveBatteryInfo(percentage = 80)
        repository.recordBatterySnapshot(current, isScreenOn = true)
        repository.recordBatterySnapshot(current, isScreenOn = false)
        repository.recordBatterySnapshot(current.copy(isCharging = true), isScreenOn = false)
        val records = dao.getRecordsForDateSync(TimeFormatter.todayKey())
        assertEquals(3, records.size)
        assertEquals(listOf("SCREEN_ON", "SCREEN_OFF", "SCREEN_OFF"), records.map { it.screenState })
        assertEquals(listOf(false, false, true), records.map { it.isCharging })
        dao.clearAll()
    }

    @Test
    fun `rapid voltage and temperature broadcasts are coalesced`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        val repository = BatteryRepository(context)
        val current = LiveBatteryInfo(percentage = 80)
        repository.recordBatterySnapshot(current)
        repeat(20) {
            repository.recordBatterySnapshot(current.copy(temperature = 25f + it / 10f, voltageMv = 4000 + it))
        }
        assertEquals(1, dao.getRecordsForDateSync(TimeFormatter.todayKey()).size)
        dao.clearAll()
    }

    @Test
    fun `stable level gets an event driven heartbeat after fifteen minutes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getInstance(context).batteryDao()
        dao.clearAll()
        val repository = BatteryRepository(context)
        val current = LiveBatteryInfo(percentage = 80)
        repository.recordBatterySnapshot(current)
        val initial = dao.getLatestRecordSync()!!
        dao.clearAll()
        dao.insertRecord(initial.copy(id = 0, timestamp = initial.timestamp - 16 * 60_000))
        repository.recordBatterySnapshot(current)
        assertEquals(2, dao.getRecordsForDateSync(TimeFormatter.todayKey()).size)
        dao.clearAll()
    }
}
