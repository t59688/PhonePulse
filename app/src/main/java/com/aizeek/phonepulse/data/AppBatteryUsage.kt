package com.aizeek.phonepulse.data

data class AppBatteryUsage(val app: AppUsageInfo, val estimatedDrainPct: Float?)

fun estimateAppBatteryUsage(apps: List<AppUsageInfo>, records: List<BatteryRecord>): List<AppBatteryUsage> {
    var hasForegroundSamples = false
    var foregroundDrain = 0
    for (index in 0 until records.lastIndex) {
        val previous = records[index]
        val next = records[index + 1]
        if (next.timestamp <= previous.timestamp || previous.isCharging || next.isCharging ||
            previous.screenState != "SCREEN_ON") continue
        hasForegroundSamples = true
        foregroundDrain += (previous.percentage - next.percentage).coerceAtLeast(0)
    }
    val totalForegroundMs = apps.sumOf { it.totalTimeInForegroundMs.coerceAtLeast(0) }
    // This attributes recorded screen-on discharge by usage share; it cannot measure per-UID background power.
    return apps.map { app ->
        AppBatteryUsage(app, if (hasForegroundSamples && totalForegroundMs > 0) {
            foregroundDrain * (app.totalTimeInForegroundMs.coerceAtLeast(0).toDouble() / totalForegroundMs).toFloat()
        } else null)
    }
}
