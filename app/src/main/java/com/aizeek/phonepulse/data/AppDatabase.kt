package com.aizeek.phonepulse.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aizeek.phonepulse.battery.BatteryCycle
import com.aizeek.phonepulse.battery.BatteryInterval
import com.aizeek.phonepulse.battery.BatteryMonitorState
import com.aizeek.phonepulse.battery.BatteryMonitorDao

@Database(entities = [ScreenSession::class, BatteryRecord::class, BatteryCycle::class,
    BatteryInterval::class, BatteryMonitorState::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screenSessionDao(): ScreenSessionDao
    abstract fun batteryDao(): BatteryDao
    abstract fun batteryMonitorDao(): BatteryMonitorDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS battery_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, level INTEGER NOT NULL, scale INTEGER NOT NULL, percentage INTEGER NOT NULL, isCharging INTEGER NOT NULL, plugType TEXT NOT NULL, health TEXT NOT NULL, temperature REAL NOT NULL, voltage INTEGER NOT NULL, screenState TEXT NOT NULL, dateKey TEXT NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS battery_cycles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, charging INTEGER NOT NULL,
                    startTime INTEGER NOT NULL, endTime INTEGER, lastTime INTEGER NOT NULL,
                    lastElapsedMs INTEGER NOT NULL, startPct INTEGER NOT NULL, endPct INTEGER NOT NULL,
                    netMah REAL NOT NULL, screenOnMah REAL NOT NULL, screenOffMah REAL NOT NULL,
                    measuredMs INTEGER NOT NULL, missingMs INTEGER NOT NULL, deepSleepMs INTEGER NOT NULL,
                    counterMah REAL NOT NULL, to100Mah REAL, fullChargeMah REAL,
                    lowCurrentSinceElapsedMs INTEGER, highSocMs INTEGER NOT NULL,
                    maxTemperatureC REAL, estimatedCapacityMah REAL, rejectionReason TEXT,
                    excluded INTEGER NOT NULL, calibrationRevision INTEGER NOT NULL,
                    completionReason TEXT, alarmNotified INTEGER NOT NULL, alarmMuted INTEGER NOT NULL,
                    socDiscontinuity INTEGER NOT NULL, plugType TEXT NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS battery_intervals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cycleId INTEGER NOT NULL,
                    startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, screenOn INTEGER NOT NULL,
                    charging INTEGER NOT NULL, startPct INTEGER NOT NULL, endPct INTEGER NOT NULL,
                    netMah REAL NOT NULL, measuredMs INTEGER NOT NULL, missingMs INTEGER NOT NULL,
                    deepSleepMs INTEGER NOT NULL, source TEXT NOT NULL, plugType TEXT NOT NULL)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS battery_monitor_state (
                    id INTEGER NOT NULL PRIMARY KEY, designCapacityMah REAL, invertCurrent INTEGER NOT NULL,
                    currentScale REAL NOT NULL, cellFactor INTEGER NOT NULL, calibrationRevision INTEGER NOT NULL,
                    chargeAlarmEnabled INTEGER NOT NULL, chargeTargetPct INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_cycles_startTime ON battery_cycles(startTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_cycles_endTime ON battery_cycles(endTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_intervals_endTime ON battery_intervals(endTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_intervals_cycleId ON battery_intervals(cycleId)")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phone_pulse.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
