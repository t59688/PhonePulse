package com.aizeek.phonepulse.data

import android.content.Context
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar

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

    fun getTodayTotalScreenOnMs(): Flow<Long> {
        val today = TimeFormatter.todayKey()
        return dao.getTotalDurationForDate(today, "SCREEN_ON").map { it ?: 0L }
    }

    fun getTodayTotalScreenOffMs(): Flow<Long> {
        val today = TimeFormatter.todayKey()
        return dao.getTotalDurationForDate(today, "SCREEN_OFF").map { it ?: 0L }
    }

    fun getTodayWakeCount(): Flow<Int> {
        val today = TimeFormatter.todayKey()
        return dao.getWakeCountForDate(today)
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            dao.clearAll()
        }
    }

    suspend fun getHourlyBreakdown(dateKey: String = TimeFormatter.todayKey()): List<HourlyScreenStat> = withContext(Dispatchers.IO) {
        val hourlyMinutes = FloatArray(24) { 0f }
        val sessions = dao.getScreenOnSessionsForDateSync(dateKey)
        val cal = Calendar.getInstance()

        for (session in sessions) {
            cal.timeInMillis = session.startTime
            val startHour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            val minutes = (session.durationMs / (60 * 1000f))
            hourlyMinutes[startHour] = (hourlyMinutes[startHour] + minutes).coerceAtMost(60f)
        }

        hourlyMinutes.mapIndexed { index, minutes ->
            HourlyScreenStat(hour = index, screenOnMinutes = minutes)
        }
    }
}

