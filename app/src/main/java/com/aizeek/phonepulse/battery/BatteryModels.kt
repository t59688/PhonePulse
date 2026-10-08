package com.aizeek.phonepulse.battery

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

data class BatteryMonitorSettings(
    val designCapacityMah: Double? = null,
    val invertCurrent: Boolean = false,
    val currentScale: Double = 1.0,
    val cellFactor: Int = 1,
    val calibrationRevision: Int = 0,
    val chargeAlarmEnabled: Boolean = false,
    val chargeTargetPct: Int = 80
)

data class BatteryTelemetry(
    val timestamp: Long,
    val elapsedMs: Long,
    val uptimeMs: Long,
    val percentage: Int,
    val plugged: Boolean,
    val status: Int,
    val screenOn: Boolean,
    val currentUa: Double?,
    val chargeCounterUah: Long? = null,
    val temperatureC: Double? = null,
    val voltageMv: Int? = null,
    val plugType: String = "NONE"
)

@Entity(tableName = "battery_cycles", indices = [Index("startTime"), Index("endTime")])
data class BatteryCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val charging: Boolean,
    val startTime: Long,
    val endTime: Long? = null,
    val lastTime: Long,
    val lastElapsedMs: Long,
    val startPct: Int,
    val endPct: Int,
    val netMah: Double = 0.0,
    val screenOnMah: Double = 0.0,
    val screenOffMah: Double = 0.0,
    val measuredMs: Long = 0,
    val missingMs: Long = 0,
    val deepSleepMs: Long = 0,
    val counterMah: Double = 0.0,
    val to100Mah: Double? = null,
    val fullChargeMah: Double? = null,
    val lowCurrentSinceElapsedMs: Long? = null,
    val highSocMs: Long = 0,
    val maxTemperatureC: Double? = null,
    val estimatedCapacityMah: Double? = null,
    val rejectionReason: String? = null,
    val excluded: Boolean = false,
    val calibrationRevision: Int = 0,
    val completionReason: String? = null,
    val alarmNotified: Boolean = false,
    val alarmMuted: Boolean = false,
    val socDiscontinuity: Boolean = false,
    val plugType: String = "NONE"
)

@Entity(tableName = "battery_intervals", indices = [Index("endTime"), Index("cycleId")])
data class BatteryInterval(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val startTime: Long,
    val endTime: Long,
    val screenOn: Boolean,
    val charging: Boolean,
    val startPct: Int,
    val endPct: Int,
    val netMah: Double,
    val measuredMs: Long,
    val missingMs: Long,
    val deepSleepMs: Long,
    val source: String,
    val plugType: String = "NONE"
)

data class BatteryAnalyticsUpdate(
    val active: BatteryCycle,
    val completed: BatteryCycle? = null,
    val interval: BatteryInterval? = null
)

data class BatteryHealthEstimate(
    val capacityMah: Double? = null,
    val healthPct: Double? = null,
    val acceptedCount: Int = 0,
    val spreadPct: Double? = null
)

data class BatteryTimeEstimates(
    val toTargetMs: Long? = null,
    val toFullMs: Long? = null,
    val screenOnRemainingMs: Long? = null,
    val screenOffRemainingMs: Long? = null,
    val mixedRemainingMs: Long? = null,
    val source: String? = null
)

data class BatteryMonitorUiState(
    val settings: BatteryMonitorSettings = BatteryMonitorSettings(),
    val currentUa: Double? = null,
    val chargeCounterUah: Long? = null,
    val lastSampleTime: Long? = null,
    val activeCycle: BatteryCycle? = null,
    val cycles: List<BatteryCycle> = emptyList(),
    val health: BatteryHealthEstimate = BatteryHealthEstimate(),
    val estimates: BatteryTimeEstimates = BatteryTimeEstimates(),
    val error: String? = null,
    val isRunning: Boolean = false
)
