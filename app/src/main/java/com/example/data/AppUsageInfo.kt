package com.example.data

import androidx.compose.ui.graphics.ImageBitmap

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMs: Long,
    val lastTimeUsedMs: Long,
    val launchCount: Int = 0,
    val iconBitmap: ImageBitmap? = null,
    val percentageOfTotal: Float = 0f,
    val estimatedBatteryDrainPct: Float = 0f,
    val estimatedMah: Int = 0
)
