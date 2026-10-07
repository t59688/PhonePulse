package com.example.data

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.example.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class UsagePeriod {
    TODAY,
    LAST_24_HOURS,
    LAST_7_DAYS
}

class UsageStatsRepository(private val context: Context) {

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun getAppUsageStats(period: UsagePeriod): List<AppUsageInfo> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) {
            return@withContext emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext emptyList()

        val now = System.currentTimeMillis()
        val startTime = when (period) {
            UsagePeriod.TODAY -> TimeFormatter.getStartOfDay(now)
            UsagePeriod.LAST_24_HOURS -> now - 24 * 60 * 60 * 1000L
            UsagePeriod.LAST_7_DAYS -> now - 7 * 24 * 60 * 60 * 1000L
        }

        val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            now
        ) ?: emptyList()

        if (usageStatsList.isEmpty()) {
            return@withContext emptyList()
        }

        // Aggregate by packageName (queryUsageStats might return multiple intervals)
        val aggregated = mutableMapOf<String, Long>()
        val lastUsedMap = mutableMapOf<String, Long>()

        for (stat in usageStatsList) {
            val totalTime = stat.totalTimeInForeground
            if (totalTime > 0) {
                aggregated[stat.packageName] = (aggregated[stat.packageName] ?: 0L) + totalTime
                val prevLast = lastUsedMap[stat.packageName] ?: 0L
                if (stat.lastTimeUsed > prevLast) {
                    lastUsedMap[stat.packageName] = stat.lastTimeUsed
                }
            }
        }

        val pm = context.packageManager
        val result = mutableListOf<AppUsageInfo>()
        var totalAllForegroundMs = 0L

        for ((pkg, timeMs) in aggregated) {
            totalAllForegroundMs += timeMs
            val appName = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast('.')
            }

            val icon = try {
                pm.getApplicationIcon(pkg)
            } catch (e: Exception) {
                null
            }

            result.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    totalTimeInForegroundMs = timeMs,
                    lastTimeUsedMs = lastUsedMap[pkg] ?: 0L,
                    icon = icon
                )
            )
        }

        // Sort descending by time
        result.sortByDescending { it.totalTimeInForegroundMs }

        // Calculate percentage
        val safeTotal = if (totalAllForegroundMs > 0) totalAllForegroundMs.toFloat() else 1f
        result.map { item ->
            item.copy(percentageOfTotal = (item.totalTimeInForegroundMs / safeTotal) * 100f)
        }
    }
}
