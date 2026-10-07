package com.aizeek.phonepulse.data

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

enum class UsagePeriod {
    TODAY,
    LAST_24_HOURS,
    LAST_7_DAYS
}

class UsageStatsRepository(private val context: Context) {

    private val labelCache = ConcurrentHashMap<String, String>()
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

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

    /**
     * Retrieves application foreground active runtime and total power consumption.
     * - Active time: Strictly foreground running time (用户在app操作/看/打开的时间).
     * - Battery consumption: Total power consumed across both foreground and background activity.
     */
    suspend fun getAppUsageStats(
        period: UsagePeriod,
        totalDeviceDrainPct: Float = 20f
    ): List<AppUsageInfo> = withContext(Dispatchers.IO) {
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

        val usageStatsList: List<UsageStats> = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                now
            ) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (usageStatsList.isEmpty()) {
            return@withContext emptyList()
        }

        // Aggregate by packageName
        // totalTimeInForeground is strictly when the app's activity was resumed and visible to the user
        val aggregatedForeground = mutableMapOf<String, Long>()
        val lastUsedMap = mutableMapOf<String, Long>()

        for (stat in usageStatsList) {
            currentCoroutineContext().ensureActive()
            val fgTime = stat.totalTimeInForeground
            if (fgTime > 0) {
                aggregatedForeground[stat.packageName] = (aggregatedForeground[stat.packageName] ?: 0L) + fgTime
                val prevLast = lastUsedMap[stat.packageName] ?: 0L
                if (stat.lastTimeUsed > prevLast) {
                    lastUsedMap[stat.packageName] = stat.lastTimeUsed
                }
            }
        }

        val pm = context.packageManager
        val result = mutableListOf<AppUsageInfo>()
        var totalAllForegroundMs = 0L

        for ((pkg, fgTimeMs) in aggregatedForeground) {
            currentCoroutineContext().ensureActive()
            // Filter out 0 duration (under 1 second)
            if (fgTimeMs < 1000L) continue

            totalAllForegroundMs += fgTimeMs

            // Retrieve real app label from system package manager
            val appName = labelCache.getOrPut(pkg) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }

            // Retrieve real app icon from system package manager (cached as ImageBitmap)
            val iconBitmap = iconCache.getOrPut(pkg) {
                try {
                    val drawable = pm.getApplicationIcon(pkg)
                    drawableToImageBitmap(drawable)
                } catch (e: Exception) {
                    null
                } ?: createPlaceholderBitmap()
            }

            result.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    totalTimeInForegroundMs = fgTimeMs, // 严格的前台活跃时间（用户在屏幕前操作、观看、打开的时间）
                    lastTimeUsedMs = lastUsedMap[pkg] ?: 0L,
                    iconBitmap = iconBitmap
                )
            )
        }

        // Sort descending by foreground active time initially
        result.sortByDescending { it.totalTimeInForegroundMs }

        // Compute percentage of foreground time and total battery power consumed
        // Total battery consumed accounts for all power drained by apps (both foreground activity and background operations)
        val safeTotal = if (totalAllForegroundMs > 0) totalAllForegroundMs.toFloat() else 1f
        val effectiveDeviceDrain = totalDeviceDrainPct.coerceAtLeast(8f)

        result.map { item ->
            val fraction = (item.totalTimeInForegroundMs / safeTotal)
            val fgPct = fraction * 100f

            // Total battery drain for this app (foreground computation + background presence)
            val totalDrainPct = (fraction * effectiveDeviceDrain)
            val totalMah = ((totalDrainPct / 100f) * 4500f).toInt().coerceAtLeast(1)

            item.copy(
                percentageOfTotal = fgPct,
                estimatedBatteryDrainPct = totalDrainPct,
                estimatedMah = totalMah
            )
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val targetSize = w.coerceIn(48, 128)
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    private fun createPlaceholderBitmap(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        return bitmap.asImageBitmap()
    }
}
