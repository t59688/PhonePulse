package com.aizeek.phonepulse.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.room.withTransaction
import com.aizeek.phonepulse.service.ScreenStateHolder
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

data class LiveBatteryInfo(
    val percentage: Int = 100,
    val isCharging: Boolean = false,
    val plugType: String = "未充电",
    val health: String = "良好",
    val temperature: Float = 25f,
    val voltageMv: Int = 4000,
    val screenOnDrainPerHour: Float = 0f,
    val screenOffDrainPerHour: Float = 0f,
    val todayTotalDrainPct: Int = 0, // 全设备总耗电量 (前台+后台全部消耗)
    val totalScreenOnDrainPct: Int = 0, // 亮屏前台耗电
    val totalScreenOffDrainPct: Int = 0 // 熄屏后台待机耗电
)

class BatteryRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val dao = database.batteryDao()

    private val _liveBattery = MutableStateFlow(readCurrentBattery())
    val liveBattery: Flow<LiveBatteryInfo> = observeBatteryRecords(getTodayBatteryRecords())

    fun observeBatteryRecords(records: Flow<List<BatteryRecord>>): Flow<LiveBatteryInfo> = combine(
        _liveBattery, records
    ) { current, records ->
        val latest = records.lastOrNull() ?: return@combine current
        val snapshot = current.copy(
            percentage = latest.percentage,
            isCharging = latest.isCharging,
            plugType = latest.plugType,
            health = latest.health,
            temperature = latest.temperature,
            voltageMv = latest.voltage
        )
        val stats = computeAllDrainStats(records, snapshot)
        snapshot.copy(
            screenOnDrainPerHour = stats.onRate,
            screenOffDrainPerHour = stats.offRate,
            todayTotalDrainPct = stats.totalDrain,
            totalScreenOnDrainPct = stats.onDrain,
            totalScreenOffDrainPct = stats.offDrain
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun readCurrentBattery(
        batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    ): LiveBatteryInfo {
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
            else -> if (isCharging) "充电中" else "放电使用中"
        }

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
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

    suspend fun recordBatterySnapshot(
        current: LiveBatteryInfo? = null,
        isScreenOn: Boolean = ScreenStateHolder.isScreenOn.value
    ) = withContext(Dispatchers.IO) {
        val snapshot = current ?: readCurrentBattery()
        val now = System.currentTimeMillis()
        val today = TimeFormatter.todayKey()

        val record = BatteryRecord(
            timestamp = now,
            level = snapshot.percentage,
            scale = 100,
            percentage = snapshot.percentage,
            isCharging = snapshot.isCharging,
            plugType = snapshot.plugType,
            health = snapshot.health,
            temperature = snapshot.temperature,
            voltage = snapshot.voltageMv,
            screenState = if (isScreenOn) "SCREEN_ON" else "SCREEN_OFF",
            dateKey = today
        )
        database.withTransaction {
            val previous = dao.getLatestRecordSync()
            val elapsed = previous?.let { now - it.timestamp } ?: Long.MAX_VALUE
            val stateChanged = previous == null ||
                previous.percentage != record.percentage ||
                previous.isCharging != record.isCharging ||
                previous.plugType != record.plugType ||
                previous.health != record.health ||
                previous.screenState != record.screenState ||
                previous.dateKey != record.dateKey
            val telemetryChanged = previous != null &&
                (previous.temperature != record.temperature || previous.voltage != record.voltage)
            // Preserve state boundaries immediately; coalesce sensor noise and duplicate broadcasts.
            // The heartbeat only runs when a system event arrives, never waking the device.
            if (stateChanged || elapsed < 0 || elapsed >= 15 * 60_000L ||
                (telemetryChanged && elapsed >= 60_000L)) {
                dao.insertRecord(record)
            }
        }
        _liveBattery.value = snapshot
    }

    data class DrainStatsResult(
        val onRate: Float,
        val offRate: Float,
        val totalDrain: Int,
        val onDrain: Int,
        val offDrain: Int
    )

    private fun computeAllDrainStats(
        records: List<BatteryRecord>,
        current: LiveBatteryInfo
    ): DrainStatsResult {
        if (records.size < 2) {
            return DrainStatsResult(
                onRate = 10.8f,
                offRate = 0.8f,
                totalDrain = (100 - current.percentage).coerceAtLeast(0),
                onDrain = ((100 - current.percentage) * 0.75f).toInt(),
                offDrain = ((100 - current.percentage) * 0.25f).toInt()
            )
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

            // Count ALL battery discharge drops whenever battery decreased
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

        val onPerHour = if (onTimeMs > 60_000L) (onDrain.toFloat() / (onTimeMs / 3600_000f)) else 10.8f
        val offPerHour = if (offTimeMs > 60_000L) (offDrain.toFloat() / (offTimeMs / 3600_000f)) else 0.8f

        // Ensure totalDrain reflects real day-drop if records cover earlier points
        val initialRecord = records.first()
        val directDrop = (initialRecord.percentage - current.percentage).coerceAtLeast(0)
        val finalTotalDrain = maxOf(totalDrain, directDrop)

        return DrainStatsResult(
            onRate = onPerHour.coerceIn(3f, 35f),
            offRate = offPerHour.coerceIn(0.1f, 5f),
            totalDrain = finalTotalDrain,
            onDrain = onDrain,
            offDrain = offDrain
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getTodayBatteryRecords(): Flow<List<BatteryRecord>> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(TimeFormatter.dateKey(now))
            val nextDay = Calendar.getInstance().apply {
                timeInMillis = TimeFormatter.getStartOfDay(now)
                add(Calendar.DATE, 1)
            }.timeInMillis
            delay((nextDay - now).coerceAtLeast(1L))
        }
    }.distinctUntilChanged().flatMapLatest { dao.getRecordsForDate(it) }
}
