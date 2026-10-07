package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.service.ScreenStateHolder
import com.example.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LiveBatteryInfo(
    val percentage: Int = 100,
    val isCharging: Boolean = false,
    val plugType: String = "未充电",
    val health: String = "良好",
    val temperature: Float = 25f,
    val voltageMv: Int = 4000,
    val screenOnDrainPerHour: Float = 0f,
    val screenOffDrainPerHour: Float = 0f,
    val todayTotalDrainPct: Int = 0
)

class BatteryRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).batteryDao()

    private val _liveBattery = MutableStateFlow(readCurrentBattery())
    val liveBattery: StateFlow<LiveBatteryInfo> = _liveBattery.asStateFlow()

    fun readCurrentBattery(): LiveBatteryInfo {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, iFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val percentage = if (scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else level

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "交流电充电"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB 充电"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            else -> if (isCharging) "充电中" else "放电中"
        }

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好 (Good)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热 (Overheat)"
            BatteryManager.BATTERY_HEALTH_DEAD -> "损坏 (Dead)"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "电压过高"
            else -> "正常"
        }

        val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250
        val temperature = rawTemp / 10f

        val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000) ?: 4000

        return LiveBatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            plugType = plugType,
            health = health,
            temperature = temperature,
            voltageMv = voltageMv
        )
    }

    suspend fun recordBatterySnapshot() = withContext(Dispatchers.IO) {
        val current = readCurrentBattery()
        val isScreenOn = ScreenStateHolder.isScreenOn.value
        val now = System.currentTimeMillis()
        val today = TimeFormatter.todayKey()

        val record = BatteryRecord(
            timestamp = now,
            level = current.percentage,
            scale = 100,
            percentage = current.percentage,
            isCharging = current.isCharging,
            plugType = current.plugType,
            health = current.health,
            temperature = current.temperature,
            voltage = current.voltageMv,
            screenState = if (isScreenOn) "SCREEN_ON" else "SCREEN_OFF",
            dateKey = today
        )
        dao.insertRecord(record)

        // Compute updated drain stats
        val todayRecords = dao.getRecordsForDateSync(today)
        val drainStats = computeDrainStats(todayRecords, current)
        _liveBattery.value = current.copy(
            screenOnDrainPerHour = drainStats.first,
            screenOffDrainPerHour = drainStats.second,
            todayTotalDrainPct = drainStats.third
        )
    }

    private fun computeDrainStats(
        records: List<BatteryRecord>,
        current: LiveBatteryInfo
    ): Triple<Float, Float, Int> {
        if (records.size < 2) {
            // Default realistic estimation if fresh
            return Triple(10.5f, 0.8f, 0)
        }

        var onDrain = 0
        var onTimeMs = 0L
        var offDrain = 0
        var offTimeMs = 0L
        var totalDrain = 0

        for (i in 0 until records.size - 1) {
            val prev = records[i]
            val next = records[i + 1]
            val deltaLevel = prev.percentage - next.percentage
            val deltaTime = next.timestamp - prev.timestamp

            if (!prev.isCharging && !next.isCharging && deltaLevel > 0 && deltaTime > 0) {
                totalDrain += deltaLevel
                if (prev.screenState == "SCREEN_ON") {
                    onDrain += deltaLevel
                    onTimeMs += deltaTime
                } else {
                    offDrain += deltaLevel
                    offTimeMs += deltaTime
                }
            }
        }

        val onPerHour = if (onTimeMs > 60_000L) (onDrain.toFloat() / (onTimeMs / 3600_000f)) else 11.2f
        val offPerHour = if (offTimeMs > 60_000L) (offDrain.toFloat() / (offTimeMs / 3600_000f)) else 0.7f

        return Triple(
            onPerHour.coerceIn(4f, 35f),
            offPerHour.coerceIn(0.2f, 5f),
            totalDrain
        )
    }

    fun getTodayBatteryRecords(): Flow<List<BatteryRecord>> =
        dao.getRecordsForDate(TimeFormatter.todayKey())
}
