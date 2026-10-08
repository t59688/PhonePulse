package com.aizeek.phonepulse.data

data class AppBatteryUsage(val app: AppUsageInfo, val estimatedDrainPct: Float?)

@Suppress("UNUSED_PARAMETER")
fun estimateAppBatteryUsage(apps: List<AppUsageInfo>, records: List<BatteryRecord>): List<AppBatteryUsage> =
    // UsageStats measures foreground time, not energy. Never attribute device drain to UIDs by time share.
    apps.map { AppBatteryUsage(it, null) }
