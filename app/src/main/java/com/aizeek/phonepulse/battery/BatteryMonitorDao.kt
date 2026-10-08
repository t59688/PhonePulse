package com.aizeek.phonepulse.battery

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "battery_monitor_state")
data class BatteryMonitorState(
    @PrimaryKey val id: Int = 1,
    @Embedded val settings: BatteryMonitorSettings = BatteryMonitorSettings()
)

@Dao
interface BatteryMonitorDao {
    @Query("SELECT * FROM battery_monitor_state WHERE id = 1")
    suspend fun getState(): BatteryMonitorState?
    @Query("SELECT * FROM battery_monitor_state WHERE id = 1")
    fun observeState(): Flow<BatteryMonitorState?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: BatteryMonitorState)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCycle(cycle: BatteryCycle): Long
    @Query("SELECT * FROM battery_cycles WHERE endTime IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getActiveCycle(): BatteryCycle?
    @Query("SELECT * FROM battery_cycles WHERE id = :id")
    suspend fun getCycle(id: Long): BatteryCycle?
    @Query("SELECT * FROM battery_cycles ORDER BY startTime DESC LIMIT 500")
    fun observeCycles(): Flow<List<BatteryCycle>>
    @Query("UPDATE battery_cycles SET excluded = :excluded WHERE id = :id AND endTime IS NOT NULL")
    suspend fun setExcluded(id: Long, excluded: Boolean)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveInterval(interval: BatteryInterval): Long
    @Query("SELECT * FROM battery_intervals ORDER BY endTime DESC LIMIT 5000")
    fun observeIntervals(): Flow<List<BatteryInterval>>
    @Query("DELETE FROM battery_intervals WHERE endTime < :before")
    suspend fun deleteOldIntervals(before: Long)
    @Query("DELETE FROM battery_cycles WHERE endTime IS NOT NULL AND endTime < :before")
    suspend fun deleteOldCycles(before: Long)
}
