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

    /**
     * Seeds realistic demo data only if user explicitly clicks the inject button
     */
    suspend fun injectSampleDemoData() = withContext(Dispatchers.IO) {
        val today = TimeFormatter.todayKey()
        val now = System.currentTimeMillis()
        val demo = mutableListOf<ScreenSession>()

        // 5 realistic sessions over the last 8 hours
        var cursor = now - 8 * 3600 * 1000L

        // 1. Slept / screen off overnight (6h 20m)
        val sleepDur = 6 * 3600 * 1000L + 20 * 60 * 1000L
        demo.add(
            ScreenSession(
                type = "SCREEN_OFF",
                startTime = cursor,
                endTime = cursor + sleepDur,
                durationMs = sleepDur,
                dateKey = today
            )
        )
        cursor += sleepDur

        // 2. Morning wakeup screen on (18m 30s)
        val wake1 = 18 * 60 * 1000L + 30 * 1000L
        demo.add(
            ScreenSession(
                type = "SCREEN_ON",
                startTime = cursor,
                endTime = cursor + wake1,
                durationMs = wake1,
                dateKey = today
            )
        )
        cursor += wake1

        // 3. Commute screen off (42m)
        val off1 = 42 * 60 * 1000L
        demo.add(
            ScreenSession(
                type = "SCREEN_OFF",
                startTime = cursor,
                endTime = cursor + off1,
                durationMs = off1,
                dateKey = today
            )
        )
        cursor += off1

        // 4. Working / phone check screen on (34m 15s)
        val wake2 = 34 * 60 * 1000L + 15 * 1000L
        demo.add(
            ScreenSession(
                type = "SCREEN_ON",
                startTime = cursor,
                endTime = cursor + wake2,
                durationMs = wake2,
                dateKey = today
            )
        )
        cursor += wake2

        // 5. Last screen off before current open (1h 10m)
        val off2 = 70 * 60 * 1000L
        demo.add(
            ScreenSession(
                type = "SCREEN_OFF",
                startTime = cursor,
                endTime = cursor + off2,
                durationMs = off2,
                dateKey = today
            )
        )

        dao.insertAll(demo)
    }
}
