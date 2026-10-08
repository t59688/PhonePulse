package com.aizeek.phonepulse.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock

interface BatteryTelemetrySource {
    fun read(screenOn: Boolean, settings: BatteryMonitorSettings): BatteryTelemetry
}

class AndroidBatteryTelemetrySource(context: Context) : BatteryTelemetrySource {
    private val app = context.applicationContext
    private val manager = app.getSystemService(BatteryManager::class.java)

    override fun read(screenOn: Boolean, settings: BatteryMonitorSettings): BatteryTelemetry {
        return readSnapshot(screenOn, null)
    }

    fun readSnapshot(screenOn: Boolean, batteryIntent: Intent?): BatteryTelemetry {
        val intent = batteryIntent ?: app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: throw IllegalStateException("系统暂未提供电池状态")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        check(scale > 0 && level in 0..scale) { "系统电量读数无效" }
        val raw = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Keep the platform units here; the accumulator applies calibration exactly once.
        val current = raw?.toDouble()
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        check(plugged >= 0) { "系统暂未提供插电状态" }
        return BatteryTelemetry(
            timestamp = System.currentTimeMillis(), elapsedMs = SystemClock.elapsedRealtime(),
            uptimeMs = SystemClock.uptimeMillis(), percentage = (level * 100.0 / scale).toInt(),
            plugged = plugged != 0, status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            screenOn = screenOn, currentUa = current,
            chargeCounterUah = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                ?.takeIf { it >= 0 }?.toLong(),
            temperatureC = intent.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10.0),
            voltageMv = intent.takeIf { it.hasExtra(BatteryManager.EXTRA_VOLTAGE) }
                ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 },
            plugType = when {
                plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
                plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
                plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "WIRELESS"
                plugged != 0 -> "OTHER"
                else -> "NONE"
            }
        )
    }

    private fun property(id: Int): Int? = try {
        manager?.getIntProperty(id)?.takeUnless { it == Int.MIN_VALUE }
    } catch (_: RuntimeException) { null } // Unsupported/vendor properties do not invalidate SOC telemetry.
}

class RecordedBatteryTelemetrySource(private val sample: BatteryTelemetry) : BatteryTelemetrySource {
    override fun read(screenOn: Boolean, settings: BatteryMonitorSettings) = sample
}

/** Freshness is checked against the current monotonic clock, not another copy of the same sample. */
fun isBatterySampleFresh(sample: BatteryTelemetry, nowElapsedMs: Long): Boolean =
    nowElapsedMs - sample.elapsedMs in 0..120_000L

fun validateBatterySettings(settings: BatteryMonitorSettings): String? = when {
    settings.designCapacityMah != null && (!settings.designCapacityMah.isFinite() ||
        settings.designCapacityMah !in 500.0..30_000.0) -> "设计容量请输入 500–30000 mAh，或留空"
    !settings.currentScale.isFinite() || settings.currentScale !in 0.001..1000.0 -> "电流倍率请输入 0.001–1000"
    settings.cellFactor !in 1..2 -> "电芯校正仅支持 1 或 2"
    settings.chargeTargetPct !in 50..100 -> "充电目标应为 50%–100%"
    else -> null
}
