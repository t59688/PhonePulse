package com.aizeek.phonepulse.service

/** A transition observed from a system broadcast, the power manager, or UsageStats. */
internal data class ScreenBoundary(val screenOn: Boolean, val timestampMs: Long)

internal data class CompletedScreenInterval(
    val screenOn: Boolean,
    val startMs: Long,
    val endMs: Long
)

internal data class ScreenReplay(
    val screenOn: Boolean,
    val startMs: Long,
    val completed: List<CompletedScreenInterval>
)

/**
 * Apply transitions against the last known open interval. Repeated, stale and
 * out-of-order callbacks must not create duplicate or negative-length sessions.
 */
internal object ScreenTimeline {
    fun replay(
        initialScreenOn: Boolean,
        initialStartMs: Long,
        boundaries: List<ScreenBoundary>,
        untilMs: Long
    ): ScreenReplay {
        var screenOn = initialScreenOn
        var start = initialStartMs
        val completed = mutableListOf<CompletedScreenInterval>()
        for (boundary in boundaries.sortedBy { it.timestampMs }) {
            if (boundary.timestampMs <= start || boundary.timestampMs > untilMs ||
                boundary.screenOn == screenOn
            ) continue
            completed += CompletedScreenInterval(screenOn, start, boundary.timestampMs)
            screenOn = boundary.screenOn
            start = boundary.timestampMs
        }
        return ScreenReplay(screenOn, start, completed)
    }
}
