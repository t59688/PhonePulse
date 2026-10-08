package com.aizeek.phonepulse

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.battery.*
import com.aizeek.phonepulse.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryMonitorPersistenceTest {
    @Test fun `v1 upgrade preserves screen history and creates battery tables`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "battery-v1-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE screen_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, durationMs INTEGER NOT NULL, dateKey TEXT NOT NULL)")
            old.execSQL("INSERT INTO screen_sessions VALUES (1,'SCREEN_ON',100,200,100,'2026-10-08')")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
        try {
            assertNull(db.batteryDao().getLatestRecordSync())
            assertEquals(1, db.openHelper.readableDatabase.query("SELECT * FROM screen_sessions").use { it.count })
            val id = db.batteryMonitorDao().saveCycle(cycle())
            assertEquals(id, db.batteryMonitorDao().getActiveCycle()!!.id)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `v2 migration preserves both battery and screen history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "battery-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE screen_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, durationMs INTEGER NOT NULL, dateKey TEXT NOT NULL)")
            old.execSQL("CREATE TABLE battery_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, level INTEGER NOT NULL, scale INTEGER NOT NULL, percentage INTEGER NOT NULL, isCharging INTEGER NOT NULL, plugType TEXT NOT NULL, health TEXT NOT NULL, temperature REAL NOT NULL, voltage INTEGER NOT NULL, screenState TEXT NOT NULL, dateKey TEXT NOT NULL)")
            old.execSQL("INSERT INTO screen_sessions VALUES (1,'SCREEN_ON',100,200,100,'2026-10-08')")
            old.execSQL("INSERT INTO battery_records VALUES (1,100,80,100,80,0,'NONE','GOOD',25,4000,'SCREEN_ON','2026-10-08')")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(80, db.batteryDao().getLatestRecordSync()!!.percentage)
            assertEquals(1, db.openHelper.readableDatabase.query("SELECT * FROM screen_sessions").use { it.count })
            assertNull(db.batteryMonitorDao().getState())
            val id = db.batteryMonitorDao().saveCycle(cycle())
            assertTrue(id > 0)
            assertEquals(id, db.batteryMonitorDao().getActiveCycle()!!.id)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `settings validation rejects incorrect capacity units and alarm target`() {
        assertNotNull(validateBatterySettings(BatteryMonitorSettings(designCapacityMah = -1.0)))
        assertNotNull(validateBatterySettings(BatteryMonitorSettings(currentScale = Double.NaN)))
        assertNotNull(validateBatterySettings(BatteryMonitorSettings(chargeTargetPct = 101)))
        assertNotNull(validateBatterySettings(BatteryMonitorSettings(cellFactor = 3)))
        assertNull(validateBatterySettings(BatteryMonitorSettings(designCapacityMah = 4500.0)))
    }

    @Test fun `alarm marks are durable and completed cycles can be excluded`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.batteryMonitorDao()
            val id = dao.saveCycle(cycle().copy(alarmNotified = true, alarmMuted = true))
            assertTrue(dao.getActiveCycle()!!.alarmNotified)
            assertTrue(dao.getActiveCycle()!!.alarmMuted)
            dao.saveCycle(dao.getActiveCycle()!!.copy(endTime = 500))
            dao.setExcluded(id, true)
            assertTrue(dao.getCycle(id)!!.excluded)
            dao.setExcluded(id, false)
            assertFalse(dao.getCycle(id)!!.excluded)
        } finally { db.close() }
    }

    private fun cycle() = BatteryCycle(charging = true, startTime = 100, lastTime = 100,
        lastElapsedMs = 100, startPct = 20, endPct = 20)
}
