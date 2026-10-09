package com.aizeek.phonepulse.data

import android.content.Context
import com.aizeek.phonepulse.service.ScreenStateHolder
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class HourlyScreenStat(
    val hour: Int, // 0..23
    val screenOnMinutes: Float
)

class ScreenStateRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).screenSessionDao()

    val allSessions: Flow<List<ScreenSession>> = dao.getAllSessions()
    val recentSessions: Flow<List<ScreenSession>> = dao.getRecentSessions(4)

    fun getSessionsForDate(dateKey: String): Flow<List<ScreenSession>> =
        dao.getSessionsForDate(dateKey)

    val lastScreenOffSession: Flow<ScreenSession?> = dao.getLastSessionByType("SCREEN_OFF")
    val lastScreenOnSession: Flow<ScreenSession?> = dao.getLastSessionByType("SCREEN_ON")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getTodayTotalScreenOnMs(): Flow<Long> {
        return ScreenStateHolder.currentDateKey.flatMapLatest { dateKey ->
            combine(
                dao.getTotalDurationForDate(dateKey, "SCREEN_ON"),
                ScreenStateHolder.isScreenOn,
                ScreenStateHolder.stateStartTime
            ) { dbDuration, isScreenOn, stateStartTime ->
                val dbMs = dbDuration ?: 0L
                val now = System.currentTimeMillis()
                val isToday = dateKey == TimeFormatter.dateKey(now)
                val dayStart = TimeFormatter.getStartOfDay(now)
                val ongoingMs = if (isScreenOn && isToday) {
                    val activeStart = maxOf(stateStartTime, dayStart)
                    (now - activeStart).coerceAtLeast(0L)
                } else {
                    0L
                }
                dbMs + ongoingMs
            }
        }.distinctUntilChanged()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getTodayTotalScreenOffMs(): Flow<Long> {
        return ScreenStateHolder.currentDateKey.flatMapLatest { dateKey ->
            dao.getTotalDurationForDate(dateKey, "SCREEN_OFF").map { it ?: 0L }
        }.distinctUntilChanged()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getTodayWakeCount(): Flow<Int> {
        return ScreenStateHolder.currentDateKey.flatMapLatest { dateKey ->
            combine(
                dao.getWakeCountForDate(dateKey),
                ScreenStateHolder.isScreenOn,
                ScreenStateHolder.stateStartTime
            ) { dbWakeCount, isScreenOn, stateStartTime ->
                val now = System.currentTimeMillis()
                val isToday = dateKey == TimeFormatter.dateKey(now)
                val dayStart = TimeFormatter.getStartOfDay(now)
                val ongoingWake = if (isScreenOn && isToday && stateStartTime >= dayStart) 1 else 0
                dbWakeCount + ongoingWake
            }
        }.distinctUntilChanged()
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            dao.clearAll()
        }
    }

    companion object {
        fun calculateHourlyBreakdown(
            dateKey: String,
            sessions: List<ScreenSession>,
            activeSessionStartMs: Long? = null,
            nowMs: Long = System.currentTimeMillis()
        ): List<HourlyScreenStat> {
            val hourlyMinutes = FloatArray(24) { 0f }
            val dayStartMs = TimeFormatter.parseDateToStartOfDay(dateKey)
            val isToday = dateKey == TimeFormatter.dateKey(nowMs)

            for (session in sessions) {
                if (!session.isScreenOn) continue
                // Reject any session erroneously timestamped in the future
                if (isToday && session.startTime > nowMs) continue

                val sessStart = session.startTime
                val sessEnd = if (session.endTime > 0L) session.endTime else (sessStart + session.durationMs)

                for (h in 0..23) {
                    val hStart = dayStartMs + h * 3600_000L
                    val hEnd = hStart + 3600_000L
                    val overlapMs = (minOf(sessEnd, hEnd) - maxOf(sessStart, hStart)).coerceAtLeast(0L)
                    if (overlapMs > 0L) {
                        hourlyMinutes[h] += (overlapMs / 60_000f)
                    }
                }
            }

            // Add ongoing screen session if any
            if (activeSessionStartMs != null && isToday && activeSessionStartMs < nowMs) {
                val activeEnd = nowMs
                for (h in 0..23) {
                    val hStart = dayStartMs + h * 3600_000L
                    val hEnd = hStart + 3600_000L
                    val overlapMs = (minOf(activeEnd, hEnd) - maxOf(activeSessionStartMs, hStart)).coerceAtLeast(0L)
                    if (overlapMs > 0L) {
                        hourlyMinutes[h] += (overlapMs / 60_000f)
                    }
                }
            }

            return (0..23).map { hour ->
                HourlyScreenStat(hour = hour, screenOnMinutes = hourlyMinutes[hour].coerceIn(0f, 60f))
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getHourlyBreakdownFlow(): Flow<List<HourlyScreenStat>> {
        return ScreenStateHolder.currentDateKey.flatMapLatest { dateKey ->
            combine(
                dao.getScreenOnSessionsForDate(dateKey),
                ScreenStateHolder.isScreenOn,
                ScreenStateHolder.stateStartTime
            ) { sessions, isScreenOn, stateStartTime ->
                val now = System.currentTimeMillis()
                val isToday = dateKey == TimeFormatter.dateKey(now)
                val activeStart = if (isScreenOn && isToday) stateStartTime else null
                calculateHourlyBreakdown(dateKey, sessions, activeStart, now)
            }
        }.distinctUntilChanged()
    }

    suspend fun getHourlyBreakdown(dateKey: String = TimeFormatter.todayKey()): List<HourlyScreenStat> = withContext(Dispatchers.IO) {
        val sessions = dao.getScreenOnSessionsForDateSync(dateKey)
        val now = System.currentTimeMillis()
        val isToday = dateKey == TimeFormatter.dateKey(now)
        val isScreenOn = ScreenStateHolder.isScreenOn.value
        val activeStart = if (isScreenOn && isToday) ScreenStateHolder.stateStartTime.value else null
        calculateHourlyBreakdown(dateKey, sessions, activeStart, now)
    }
}
