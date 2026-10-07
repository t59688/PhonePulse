package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val shortTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun todayKey(): String = dateFormat.format(Date())

    fun dateKey(timestamp: Long): String = dateFormat.format(Date(timestamp))

    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

    fun formatShortTime(timestamp: Long): String = shortTimeFormat.format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

    /**
     * Formats duration into digital clock style: "01:23:45" or "04:32"
     */
    fun formatDigitalClock(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats duration into human readable Chinese: "2小时 15分 30秒" or "45秒"
     */
    fun formatDurationChinese(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return buildString {
            if (hours > 0) {
                append("${hours}小时 ")
            }
            if (minutes > 0 || hours > 0) {
                append("${minutes}分 ")
            }
            append("${seconds}秒")
        }.trim()
    }

    /**
     * Formats duration for compact badges: "2h 15m" or "45s"
     */
    fun formatDurationCompact(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
