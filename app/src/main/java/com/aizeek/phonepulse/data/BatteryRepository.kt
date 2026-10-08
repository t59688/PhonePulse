package com.aizeek.phonepulse.data

import android.content.Context
import android.content.BroadcastReceiver
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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.util.Calendar

data class LiveBatteryInfo(
    val percentage: Int = -1,
    val isCharging: Boolean = false,
    val plugType: String = "状态未知",
    val health: String = "未知",
    val temperature: Float? = null,
    val voltageMv: Int? = null,
    val screenOnDrainPerHour: Float = 0f,
    val screenOffDrainPerHour: Float = 0f,
    val todayTotalDrainPct: Int = 0, // 已记录的 SOC 下降百分点
    val totalScreenOnDrainPct: Int = 0, // 可归属于亮屏区间的 SOC 下降
    val totalScreenOffDrainPct: Int = 0, // 可归属于熄屏区间的 SOC 下降
    val screenOnRateKnown: Boolean = false,
    val screenOffRateKnown: Boolean = false,
    val todayDrainKnown: Boolean = false,
    val plugged: Boolean? = null
)

class BatteryRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val dao = database.batteryDao()

    private val _liveBattery = MutableStateFlow(readCurrentBattery())
    val liveBattery: Flow<LiveBatteryInfo> = observeBatteryRecords(getTodayBatteryRecords())

    fun observeBatteryRecords(records: Flow<List<BatteryRecord>>): Flow<LiveBatteryInfo> = combine(
        _liveBattery, records, systemBatteryUpdates()
    ) { current, records, _ ->
        val stats = computeAllDrainStats(records)
        current.copy(
            screenOnDrainPerHour = stats.onRate,
            screenOffDrainPerHour = stats.offRate,
            todayTotalDrainPct = stats.totalDrain,
            totalScreenOnDrainPct = stats.onDrain,
            totalScreenOffDrainPct = stats.offDrain,
            screenOnRateKnown = stats.onKnown,
            screenOffRateKnown = stats.offKnown,
            todayDrainKnown = stats.totalKnown
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private fun systemBatteryUpdates(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                _liveBattery.value = readCurrentBattery(intent)
                trySend(Unit)
            }
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        _liveBattery.value = readCurrentBattery(sticky)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.onStart { emit(Unit) }

    fun readCurrentBattery(
        batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    ): LiveBatteryInfo {
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (scale > 0 && level in 0..scale) ((level * 100.0) / scale).toInt() else -1

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugged = chargePlug.takeIf { it >= 0 }?.let { it != 0 }
        val plugType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "交流电充电"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB 充电"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            else -> when {
                plugged == true -> "已连接电源"
                plugged == false -> "未连接电源"
                else -> "状态未知"
            }
        }
        val stateLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> plugType
            BatteryManager.BATTERY_STATUS_FULL -> "系统报告已满"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> if (plugged == true) "已连接 · 暂停充电" else "未在充电"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电使用中"
            else -> "充电状态未知"
        }

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "电压过高"
            BatteryManager.BATTERY_HEALTH_COLD -> "低温"
            else -> "未知"
        }

        val temperature = batteryStatus?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f)
        val voltageMv = batteryStatus?.takeIf { it.hasExtra(BatteryManager.EXTRA_VOLTAGE) }
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }

        return LiveBatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            plugType = stateLabel,
            health = health,
            temperature = temperature,
            voltageMv = voltageMv,
            plugged = plugged
        )
    }

    suspend fun recordBatterySnapshot(
        current: LiveBatteryInfo? = null,
        isScreenOn: Boolean = ScreenStateHolder.isScreenOn.value
    ) = withContext(Dispatchers.IO) {
        val snapshot = current ?: readCurrentBattery()
        _liveBattery.value = snapshot
        if (snapshot.percentage !in 0..100) return@withContext
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
            dateKey = today,
            plugged = snapshot.plugged
        )
        database.withTransaction {
            val previous = dao.getLatestRecordSync()
            val elapsed = previous?.let { now - it.timestamp } ?: Long.MAX_VALUE
            val stateChanged = previous == null ||
                previous.percentage != record.percentage ||
                previous.isCharging != record.isCharging ||
                previous.plugged != record.plugged ||
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
        val offDrain: Int,
        val onKnown: Boolean = false,
        val offKnown: Boolean = false,
        val totalKnown: Boolean = false
    )

    private fun computeAllDrainStats(
        records: List<BatteryRecord>
    ): DrainStatsResult {
        if (records.size < 2) {
            return DrainStatsResult(
                onRate = 0f,
                offRate = 0f,
                totalDrain = 0,
                onDrain = 0,
                offDrain = 0
            )
        }

        var onDrain = 0
        var onTimeMs = 0L
        var offDrain = 0
        var offTimeMs = 0L
        var totalDrain = 0
        var totalKnown = false

        for (i in 0 until records.size - 1) {
            val prev = records[i]
            val next = records[i + 1]
            val deltaLevel = prev.percentage - next.percentage
            val deltaTime = next.timestamp - prev.timestamp

            // SOC declines are observed even when the power source is unknown.
            if (prev.percentage in 0..100 && next.percentage in 0..100 &&
                deltaLevel >= 0 && deltaTime > 0) {
                totalKnown = true
                totalDrain += deltaLevel
                // Event history has a 15-minute heartbeat. Larger gaps cannot establish screen attribution.
                if (deltaTime > 15 * 60_000L || prev.plugged != false || next.plugged != false ||
                    prev.isCharging || next.isCharging) continue
                if (prev.screenState == "SCREEN_ON") {
                    onDrain += deltaLevel
                    onTimeMs += deltaTime
                } else {
                    offDrain += deltaLevel
                    offTimeMs += deltaTime
                }
            }
        }

        val onPerHour = if (onTimeMs >= 60_000L) (onDrain.toFloat() / (onTimeMs / 3600_000f)) else 0f
        val offPerHour = if (offTimeMs >= 60_000L) (offDrain.toFloat() / (offTimeMs / 3600_000f)) else 0f

        return DrainStatsResult(
            onRate = onPerHour,
            offRate = offPerHour,
            totalDrain = totalDrain,
            onDrain = onDrain,
            offDrain = offDrain,
            onKnown = onTimeMs >= 60_000L,
            offKnown = offTimeMs >= 60_000L,
            totalKnown = totalKnown
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
