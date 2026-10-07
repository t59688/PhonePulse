package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BatteryRecord): Long

    @Query("SELECT * FROM battery_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestRecord(): Flow<BatteryRecord?>

    @Query("SELECT * FROM battery_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecordSync(): BatteryRecord?

    @Query("SELECT * FROM battery_records WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    fun getRecordsForDate(dateKey: String): Flow<List<BatteryRecord>>

    @Query("SELECT * FROM battery_records WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    suspend fun getRecordsForDateSync(dateKey: String): List<BatteryRecord>

    @Query("SELECT * FROM battery_records WHERE timestamp >= :sinceMs ORDER BY timestamp ASC")
    fun getRecordsSince(sinceMs: Long): Flow<List<BatteryRecord>>

    @Query("DELETE FROM battery_records")
    suspend fun clearAll()
}
