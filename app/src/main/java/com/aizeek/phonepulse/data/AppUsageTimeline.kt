package com.aizeek.phonepulse.data

enum class UsageTimelineEventType { RESUME, PAUSE, RESET }
data class UsageTimelineEvent(val timestamp: Long, val type: UsageTimelineEventType, val activity: String = "")
data class AppUsageSession(val startMs: Long, val endMs: Long)
data class AppUsageHour(val startMs: Long, val endMs: Long, val durationMs: Long)
data class AppUsageTimeline(val startMs: Long, val endMs: Long, val sessions: List<AppUsageSession>, val hours: List<AppUsageHour>) {
    val totalDurationMs: Long get() = sessions.sumOf { it.endMs - it.startMs }
}

fun buildAppUsageTimeline(startMs: Long, endMs: Long, events: List<UsageTimelineEvent>): AppUsageTimeline {
    require(endMs >= startMs)
    val activeActivities = mutableSetOf<String>()
    val sessions = mutableListOf<AppUsageSession>()
    var sessionStart: Long? = null
    fun closeSession(timestamp: Long) {
        sessionStart?.let {
            val start = it.coerceAtLeast(startMs)
            val end = timestamp.coerceAtMost(endMs)
            if (end > start) {
                val previous = sessions.lastOrNull()
                if (previous != null && previous.endMs == start) {
                    sessions[sessions.lastIndex] = previous.copy(endMs = end)
                } else sessions.add(AppUsageSession(start, end))
            }
        }
        sessionStart = null
    }
    for (event in events.sortedBy { it.timestamp }) {
        if (event.timestamp > endMs) break
        when (event.type) {
            UsageTimelineEventType.RESUME -> {
                if (activeActivities.isEmpty()) sessionStart = event.timestamp
                activeActivities.add(event.activity)
            }
            UsageTimelineEventType.PAUSE -> {
                activeActivities.remove(event.activity)
                if (activeActivities.isEmpty()) closeSession(event.timestamp)
            }
            UsageTimelineEventType.RESET -> {
                closeSession(event.timestamp)
                activeActivities.clear()
            }
        }
    }
    closeSession(endMs)
    val hourMs = 3_600_000L
    val hours = mutableListOf<AppUsageHour>()
    var start = startMs
    while (start < endMs) {
        val end = (start + hourMs).coerceAtMost(endMs)
        val duration = sessions.sumOf { (minOf(it.endMs, end) - maxOf(it.startMs, start)).coerceAtLeast(0) }
        hours.add(AppUsageHour(start, end, duration))
        start = end
    }
    return AppUsageTimeline(startMs, endMs, sessions, hours)
}
