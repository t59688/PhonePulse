package com.aizeek.phonepulse.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.aizeek.phonepulse.data.UsageStatsRepository

/** Android 9+ records screen-interactive transitions even while our process isn't alive. */
internal object ScreenHistorySource {
    const val MAX_LOOKBACK_MS = 48L * 60 * 60 * 1000

    /** null means unavailable; an empty list means the query succeeded without transitions. */
    fun read(context: Context, fromMs: Long, toMs: Long): List<ScreenBoundary>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            !UsageStatsRepository(context).hasUsageStatsPermission()
        ) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (context.getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked == false
        ) return null

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val start = maxOf(fromMs, toMs - MAX_LOOKBACK_MS).coerceAtLeast(0L)
        if (start >= toMs) return emptyList()
        return try {
            val events = manager.queryEvents(start, toMs + 1) ?: return null
            val event = UsageEvents.Event()
            val transitions = mutableListOf<ScreenBoundary>()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE ->
                        transitions += ScreenBoundary(true, event.timeStamp)
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                        transitions += ScreenBoundary(false, event.timeStamp)
                }
            }
            transitions
        } catch (e: SecurityException) {
            Log.w("ScreenHistorySource", "Usage history permission unavailable", e)
            null
        } catch (e: RuntimeException) {
            Log.w("ScreenHistorySource", "Could not read screen history", e)
            null
        }
    }
}
