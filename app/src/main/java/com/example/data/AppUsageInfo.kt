package com.example.data

import android.graphics.drawable.Drawable

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMs: Long,
    val lastTimeUsedMs: Long,
    val launchCount: Int = 0,
    val icon: Drawable? = null,
    val percentageOfTotal: Float = 0f
)
