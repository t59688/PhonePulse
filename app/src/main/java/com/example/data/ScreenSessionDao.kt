package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScreenSession): Long

    @Query("SELECT * FROM screen_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ScreenSession>>

    @Query("SELECT * FROM screen_sessions WHERE dateKey = :dateKey ORDER BY startTime DESC")
    fun getSessionsForDate(dateKey: String): Flow<List<ScreenSession>>

    @Query("SELECT * FROM screen_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<ScreenSession>>

    @Query("SELECT * FROM screen_sessions WHERE type = :type ORDER BY startTime DESC LIMIT 1")
    fun getLastSessionByType(type: String): Flow<ScreenSession?>

    @Query("SELECT * FROM screen_sessions WHERE type = :type ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastSessionByTypeSync(type: String): ScreenSession?

    @Query("SELECT * FROM screen_sessions WHERE startTime >= :sinceTimeMs ORDER BY startTime ASC")
    fun getSessionsSince(sinceTimeMs: Long): Flow<List<ScreenSession>>

    @Query("SELECT SUM(durationMs) FROM screen_sessions WHERE dateKey = :dateKey AND type = :type")
    fun getTotalDurationForDate(dateKey: String, type: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM screen_sessions WHERE dateKey = :dateKey AND type = 'SCREEN_ON'")
    fun getWakeCountForDate(dateKey: String): Flow<Int>

    @Query("DELETE FROM screen_sessions")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<ScreenSession>)
}
