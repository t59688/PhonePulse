package com.example.data

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
import com.example.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun getAppUsageStats(
        period: UsagePeriod,
        todayScreenOnDrainPct: Float = 15f
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
            // Filter out 0 duration
            if (timeMs < 1000L) continue

            totalAllForegroundMs += timeMs

            // Retrieve real app label (with cache)
            val appName = labelCache.getOrPut(pkg) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    // Friendly fallback name
                    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }

            // Retrieve real app icon (with cache, rendered off-main-thread)
            val iconBitmap = iconCache.getOrPut(pkg) {
                try {
                    val drawable = pm.getApplicationIcon(pkg)
                    drawableToImageBitmap(drawable)
                } catch (e: Exception) {
                    null
                } ?: createPlaceholderBitmap(appName)
            }

            result.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    totalTimeInForegroundMs = timeMs,
                    lastTimeUsedMs = lastUsedMap[pkg] ?: 0L,
                    iconBitmap = iconBitmap
                )
            )
        }

        // Sort descending by foreground time
        result.sortByDescending { it.totalTimeInForegroundMs }

        // Compute percentage and battery drain estimation
        val safeTotal = if (totalAllForegroundMs > 0) totalAllForegroundMs.toFloat() else 1f
        val batteryReferencePct = todayScreenOnDrainPct.coerceAtLeast(10f)

        result.map { item ->
            val fraction = (item.totalTimeInForegroundMs / safeTotal)
            val pct = fraction * 100f
            val drainPct = fraction * batteryReferencePct
            val mah = ((drainPct / 100f) * 4500f).toInt().coerceAtLeast(1)

            item.copy(
                percentageOfTotal = pct,
                estimatedBatteryDrainPct = drainPct,
                estimatedMah = mah
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

    private fun createPlaceholderBitmap(appName: String): ImageBitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        return bitmap.asImageBitmap()
    }
}
