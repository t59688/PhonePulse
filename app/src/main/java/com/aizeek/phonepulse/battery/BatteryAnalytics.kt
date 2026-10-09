package com.aizeek.phonepulse.battery

import kotlin.math.abs
import kotlin.math.max

/** Single-writer accumulator. Plug state defines sessions; current always keeps its net sign. */
class BatteryAnalytics {
    private var active: BatteryCycle? = null
    private var previous: BatteryTelemetry? = null
    private var counterAnchor: BatteryTelemetry? = null
    private var counterAnchorNetMah = 0.0

    fun restore(cycle: BatteryCycle?) {
        active = cycle?.takeIf { it.endTime == null }
        previous = null
        counterAnchor = null
    }

    fun replaceActive(cycle: BatteryCycle) {
        require(cycle.endTime == null)
        active = cycle
    }

    fun accept(sample: BatteryTelemetry, settings: BatteryMonitorSettings): BatteryAnalyticsUpdate {
        val old = active
        if (old == null) return start(sample, settings)
        val reason = when {
            sample.elapsedMs < old.lastElapsedMs -> "REBOOT"
            old.calibrationRevision != settings.calibrationRevision -> "CALIBRATION_CHANGED"
            old.charging != sample.plugged -> "POWER_CHANGE"
            old.plugType != sample.plugType -> "POWER_SOURCE_CHANGE"
            else -> null
        }
        if (reason == "REBOOT" || reason == "CALIBRATION_CHANGED") {
            val completed = finish(old, reason)
            return start(sample, settings).copy(completed = completed)
        }
        // Zero-duration events update the next interval's state without adding charge.
        val dt = (sample.elapsedMs - old.lastElapsedMs).coerceAtLeast(0)
        val prior = previous
        val awake = prior?.let { (sample.uptimeMs - it.uptimeMs).coerceIn(0, dt) } ?: 0L
        // Separate clock reads can straddle a millisecond boundary without actual suspend.
        val sleep = if (prior != null && dt - awake > 20) dt - awake else 0L
        val a = prior?.let { calibratedCurrent(it, settings) }
        // The power-event sample can already report the new charger's current.
        val b = if (reason == "POWER_CHANGE" || reason == "POWER_SOURCE_CHANGE") a else calibratedCurrent(sample, settings)
        val currentValid = prior != null && dt in 1..60_000 && sleep == 0L && a != null && b != null
        val integral = if (currentValid) (a!! + b!!) / 2 * dt / 3_600_000_000.0 else null
        val anchor = counterAnchor
        val counter = anchor?.let {
            val span = sample.elapsedMs - it.elapsedMs
            val awakeSpan = (sample.uptimeMs - it.uptimeMs).coerceIn(0, span.coerceAtLeast(0))
            val sleepSpan = if (span - awakeSpan > 20) span - awakeSpan else 0L
            counterDelta(it, sample, settings, span, sleepSpan)?.let { cumulative ->
                // A coarse or delayed counter includes charge already integrated while awake.
                // Only its unrecorded remainder may belong to this interval.
                val remainder = cumulative - (old.netMah - counterAnchorNetMah)
                remainder.takeIf { validCounterRemainder(settings, dt, cumulative, it, a, b) }
            }
        }?.takeIf { !currentValid || (integral == 0.0 && it == 0.0) }
        val socOnly = prior != null && dt in 1..60_000 && sleep == 0L && !currentValid && counter == null
        val measured = if (counter != null || currentValid) dt else 0L
        val mah = counter ?: integral ?: 0.0
        val pctDelta = sample.percentage - old.endPct
        val discontinuity = old.socDiscontinuity ||
            (old.charging && pctDelta < 0) || (!old.charging && pctDelta > 0) ||
            (abs(pctDelta) >= 5 && dt < abs(pctDelta) * 60_000L)
        val net = old.netMah + mah
        val screenOn = prior?.screenOn ?: sample.screenOn
        val normalized = calibratedCurrent(sample, settings)
        val confirmingFull = reason == null && sample.plugged && sample.percentage == 100 &&
            sample.status == 5 && normalized != null && abs(normalized) <= 100_000
        // FULL alone cannot prove electrical termination. Missing current or an uncovered
        // interval breaks the confirmation; a later sample starts a fresh five-minute window.
        val lowCurrentSince = if (confirmingFull) {
            old.lowCurrentSinceElapsedMs?.takeIf {
                measured == dt && prior != null && dt <= 60_000 && sleep == 0L
            } ?: sample.elapsedMs
        } else null
        val fullCharge = old.fullChargeMah ?: net.takeIf {
            lowCurrentSince != null && sample.elapsedMs - lowCurrentSince >= 300_000
        }
        var updated = old.copy(lastTime = sample.timestamp, lastElapsedMs = sample.elapsedMs,
            endPct = sample.percentage, netMah = net,
            screenOnMah = old.screenOnMah + if (screenOn) mah else 0.0,
            screenOffMah = old.screenOffMah + if (!screenOn) mah else 0.0,
            measuredMs = old.measuredMs + measured, missingMs = old.missingMs + dt - measured,
            screenOnMeasuredMs = old.screenOnMeasuredMs + if (screenOn) measured else 0L,
            screenOffMeasuredMs = old.screenOffMeasuredMs + if (!screenOn) measured else 0L,
            deepSleepMs = old.deepSleepMs + sleep,
            counterMah = old.counterMah + (counter ?: 0.0),
            to100Mah = old.to100Mah ?: net.takeIf {
                old.charging && old.startPct < 100 && old.endPct < 100 && sample.percentage == 100 &&
                    dt > 0 && measured == dt
            },
            fullChargeMah = fullCharge, lowCurrentSinceElapsedMs = lowCurrentSince,
            highSocMs = old.highSocMs + if (old.endPct >= 80) dt else 0L,
            maxTemperatureC = listOfNotNull(old.maxTemperatureC, sample.temperatureC?.takeIf { it.isFinite() }).maxOrNull(),
            socDiscontinuity = discontinuity,
            rejectionReason = if (prior == null && dt > 0) "INCOMPLETE_RESTORE" else old.rejectionReason)
        if (old.fullChargeMah == null && fullCharge != null) {
            // Persist the qualified prefix at termination. Later idle gaps cannot invalidate
            // sensor coverage that was already complete when this capacity was measured.
            val prefix = finish(updated, "FULL_CONFIRMED")
            updated = updated.copy(estimatedCapacityMah = prefix.estimatedCapacityMah,
                rejectionReason = prefix.rejectionReason)
        }
        val interval = if (dt > 0) BatteryInterval(cycleId = old.id,
            startTime = old.lastTime, endTime = sample.timestamp, screenOn = screenOn,
            charging = old.charging, startPct = old.endPct, endPct = sample.percentage,
            netMah = mah, measuredMs = measured, missingMs = dt - measured, deepSleepMs = sleep,
            source = if (counter != null) "COUNTER" else if (currentValid) "CURRENT" else if (socOnly) "SOC_ONLY" else "MISSING",
            plugType = old.plugType) else null
        if (reason != null) {
            // The interval ending at the power event belongs to the preceding session.
            return start(sample, settings).copy(completed = finish(updated, reason), interval = interval)
        }
        active = updated
        previous = sample
        if ((dt > 0 && measured == 0L && anchor != null) || sample.chargeCounterUah?.let { it > 0 } != true) {
            counterAnchor = null
        } else if (anchor == null || sample.chargeCounterUah != anchor.chargeCounterUah) {
            counterAnchor = sample
            counterAnchorNetMah = net
        }
        return BatteryAnalyticsUpdate(updated, interval = interval)
    }

    private fun start(sample: BatteryTelemetry, settings: BatteryMonitorSettings): BatteryAnalyticsUpdate {
        val cycle = BatteryCycle(charging = sample.plugged, startTime = sample.timestamp,
            lastTime = sample.timestamp, lastElapsedMs = sample.elapsedMs,
            startPct = sample.percentage, endPct = sample.percentage,
            lowCurrentSinceElapsedMs = sample.elapsedMs.takeIf {
                sample.plugged && sample.percentage == 100 && sample.status == 5 &&
                    calibratedCurrent(sample, settings)?.let { abs(it) <= 100_000 } == true
            },
            maxTemperatureC = sample.temperatureC?.takeIf { it.isFinite() },
            calibrationRevision = settings.calibrationRevision, plugType = sample.plugType)
        active = cycle
        previous = sample
        counterAnchor = sample.takeIf { it.chargeCounterUah?.let { value -> value > 0 } == true }
        counterAnchorNetMah = 0.0
        return BatteryAnalyticsUpdate(cycle)
    }

    private fun finish(cycle: BatteryCycle, reason: String): BatteryCycle {
        val confirmed = cycle.estimatedCapacityMah?.takeIf { it.isFinite() && it > 0 }
        if (cycle.charging && cycle.fullChargeMah?.let { it.isFinite() && it > 0 } == true &&
            confirmed != null && reason != "CALIBRATION_CHANGED") {
            return cycle.copy(endTime = cycle.lastTime, completionReason = reason, rejectionReason = null)
        }
        val duration = cycle.measuredMs + cycle.missingMs
        val delta = cycle.endPct - cycle.startPct
        val healthCharge = cycle.fullChargeMah ?: cycle.netMah
        val rejection = when {
            !cycle.charging -> "NOT_CHARGING"
            reason == "REBOOT" -> "REBOOT"
            reason == "CALIBRATION_CHANGED" -> "CALIBRATION_CHANGED"
            cycle.rejectionReason == "INCOMPLETE_RESTORE" -> "INCOMPLETE_RESTORE"
            // Maintenance can improve cumulative coverage without repairing the frozen prefix.
            cycle.fullChargeMah != null && reason != "FULL_CONFIRMED" ->
                cycle.rejectionReason ?: "UNQUALIFIED_FULL_PREFIX"
            cycle.socDiscontinuity -> "SOC_DISCONTINUITY"
            delta < BatteryHealthEstimator.MIN_SOC_CHANGE -> "SMALL_SOC_CHANGE"
            cycle.measuredMs <= 0 || duration <= 0 -> "NO_MEASUREMENT"
            cycle.measuredMs.toDouble() / duration < BatteryHealthEstimator.MIN_COVERAGE -> "LOW_COVERAGE"
            !healthCharge.isFinite() || healthCharge <= 0 -> "NON_POSITIVE_CHARGE"
            else -> null
        }
        // Health stops at confirmed termination; later maintenance pulses remain in netMah.
        // Before termination can be confirmed, use measured charge up to the plug boundary.
        val capacity = if (rejection == null) healthCharge * 100 / delta else null
        return cycle.copy(endTime = cycle.lastTime, completionReason = reason,
            rejectionReason = rejection, estimatedCapacityMah = capacity?.takeIf { it.isFinite() })
    }

    private fun calibratedCurrent(sample: BatteryTelemetry, settings: BatteryMonitorSettings): Double? =
        sample.currentUa?.let { it * settings.currentScale * settings.cellFactor * if (settings.invertCurrent) -1 else 1 }
            ?.takeIf { it.isFinite() && abs(it) <= 20_000_000 }

    private fun validCounterRemainder(settings: BatteryMonitorSettings, dt: Long,
        cumulative: Double, remainder: Double, firstCurrent: Double?, lastCurrent: Double?): Boolean {
        if (dt <= 0 || !remainder.isFinite()) return false
        val maxMa = settings.designCapacityMah?.takeIf { it.isFinite() && it > 0 }
            ?.let { (it * 5).coerceIn(5000.0, 20000.0) } ?: 20000.0
        if (abs(remainder) > maxMa * dt / 3_600_000 + 0.05) return false
        // A partial counter refresh must not reverse charge merely to undo earlier integration.
        return cumulative == 0.0 || remainder == 0.0 || cumulative * remainder >= 0 ||
            (firstCurrent != null && lastCurrent != null && firstCurrent * remainder > 0 && lastCurrent * remainder > 0)
    }

    private fun counterDelta(a: BatteryTelemetry, b: BatteryTelemetry, settings: BatteryMonitorSettings,
        dt: Long, sleep: Long): Double? {
        if (dt <= 0) return null
        val first = a.chargeCounterUah?.takeIf { it > 0 } ?: return null
        val last = b.chargeCounterUah?.takeIf { it > 0 } ?: return null
        // CURRENT unit correction does not change the public charge-counter's fixed uAh unit.
        val factor = settings.cellFactor.toDouble()
        val delta = (last.toDouble() - first.toDouble()) / 1000 * factor
        if (!delta.isFinite() || factor <= 0) return null
        val design = settings.designCapacityMah?.takeIf { it.isFinite() && it > 0 }
        if (design != null && max(first, last).toDouble() / 1000 * factor > design * 1.5) return null
        val maxMa = design?.let { (it * 5).coerceIn(5000.0, 20000.0) } ?: 20000.0
        if (abs(delta) > maxMa * dt / 3_600_000 + 0.05) return null
        // Counter resets/opposite sign glitches must not turn missing sleep into measured charge.
        val x = calibratedCurrent(a, settings)
        val y = if (a.plugged != b.plugged) x else calibratedCurrent(b, settings)
        // An unchanged counter can be a frozen OEM property. Across suspend or long gaps
        // its freshness is unknowable; short zero-net intervals also need current evidence.
        if (delta == 0.0) {
            if (sleep > 0 || dt > 60_000 || a.percentage != b.percentage || x == null || y == null) return null
            val expected = (x + y) / 2 * dt / 3_600_000_000.0
            if (abs(expected) > 0.05) return null
        }
        if ((a.plugged && delta < 0 && b.percentage >= a.percentage && !(x != null && y != null && x < 0 && y < 0)) ||
            (!a.plugged && delta > 0 && b.percentage <= a.percentage && !(x != null && y != null && x > 0 && y > 0))) return null
        if (sleep == 0L && dt <= 60_000) {
            if (x != null && y != null) {
                val integral = (x + y) / 2 * dt / 3_600_000_000.0
                if (abs(delta - integral) > max(0.5, abs(integral) * 0.5)) return null
            }
        }
        return delta
    }
}
