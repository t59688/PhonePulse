package com.aizeek.phonepulse.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val shortTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    @Synchronized
    fun todayKey(): String = dateFormat.format(Date())

    @Synchronized
    fun dateKey(timestamp: Long): String = dateFormat.format(Date(timestamp))

    @Synchronized
    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

    @Synchronized
    fun formatShortTime(timestamp: Long): String = shortTimeFormat.format(Date(timestamp))

    @Synchronized
    fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

    @Synchronized
    fun formatSampleUpdateTime(timestamp: Long): String {
        return if (dateKey(timestamp) == todayKey()) {
            "${formatShortTime(timestamp)} 更新"
        } else {
            "${formatDateTime(timestamp)} 更新"
        }
    }

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

    /**
     * 单一单位时间格式化（只有一个单位，自动进阶变换）：
     * - 不满 60 秒（< 60s）：显示为 "XX秒" (例如 0秒, 35秒, 59秒)
     * - 60 秒至 59 分钟（< 60m）：显示为 "XX分" (例如 1分, 25分, 59分)
     * - 60 分钟及以上（>= 60m）：
     *   - 如果是整小时（如 60分、120分）："XX小时" (如 1小时, 2小时)
     *   - 如果在 2 小时以内有零头（如 75分钟、90分钟）："XX分" (保留精确分钟，单一单位)
     *   - 如果在 2 小时以上有零头（如 8小时20分）："XX小时" (宏观简洁，单一单位)
     */
    fun formatSingleUnit(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val totalMinutes = totalSeconds / 60
        val totalHours = totalMinutes / 60

        return when {
            totalSeconds < 60 -> "${totalSeconds}秒"
            totalMinutes < 60 -> "${totalMinutes}分"
            totalMinutes % 60 == 0L -> "${totalHours}小时"
            totalHours < 2 -> "${totalMinutes}分"
            else -> "${totalHours}小时"
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

    fun getEndOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        return getStartOfDay(timestamp) + 24 * 3600 * 1000L
    }

    @Synchronized
    fun parseDateToStartOfDay(dateKey: String): Long {
        return try {
            val date = dateFormat.parse(dateKey)
            date?.time ?: getStartOfDay()
        } catch (e: Exception) {
            getStartOfDay()
        }
    }
}
