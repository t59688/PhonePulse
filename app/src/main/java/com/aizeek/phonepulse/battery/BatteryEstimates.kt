package com.aizeek.phonepulse.battery

import kotlin.math.sqrt

object BatteryEstimates {
    fun health(cycles: List<BatteryCycle>, settings: BatteryMonitorSettings): BatteryHealthEstimate {
        val capacities = cycles.asSequence().filter {
            it.endTime != null && it.charging && !it.excluded && it.rejectionReason == null &&
                (!it.socDiscontinuity || it.fullChargeMah != null) && it.calibrationRevision == settings.calibrationRevision
        }.sortedByDescending { it.endTime }.mapNotNull {
            it.estimatedCapacityMah?.takeIf { capacity -> capacity.isFinite() && capacity > 0 }
        }.take(5).toList()
        if (capacities.isEmpty()) return BatteryHealthEstimate()
        val mean = capacities.average()
        val spread = if (capacities.size > 1)
            sqrt(capacities.sumOf { (it - mean) * (it - mean) } / capacities.size) / mean * 100 else null
        return BatteryHealthEstimate(capacityMah = mean,
            healthPct = settings.designCapacityMah?.takeIf { it.isFinite() && it > 0 }?.let { mean / it * 100 },
            acceptedCount = capacities.size, spreadPct = spread)
    }

    fun estimate(sample: BatteryTelemetry?, active: BatteryCycle?, cycles: List<BatteryCycle>,
        intervals: List<BatteryInterval>, settings: BatteryMonitorSettings): BatteryTimeEstimates {
        if (sample == null || sample.percentage !in 0..100) return BatteryTimeEstimates()
        if (active != null && (sample.timestamp - active.lastTime !in 0..120_000 ||
                sample.elapsedMs - active.lastElapsedMs !in 0..120_000 ||
                active.calibrationRevision != settings.calibrationRevision)) return BatteryTimeEstimates()
        val known = (cycles + listOfNotNull(active)).associateBy { it.id }
        val eligible = intervals.filter { interval ->
            val cycle = known[interval.cycleId]
            (cycle == null && known.isEmpty() || cycle != null && !cycle.excluded &&
                !cycle.socDiscontinuity && cycle.calibrationRevision == settings.calibrationRevision &&
                cycle.rejectionReason !in setOf("REBOOT", "INCOMPLETE_RESTORE", "CALIBRATION_CHANGED")) &&
                interval.startPct in 0..100 && interval.endPct in 0..100 &&
                interval.endTime >= interval.startTime && interval.endTime <= sample.timestamp &&
                sample.timestamp - interval.endTime <= HISTORY_WINDOW_MS
        }
        val usable = eligible.filter(::hasCoverage)
        // Only measured capacity supplies the mAh/% conversion; design capacity is not
        // a substitute for historical sensor data or a real accepted health session.
        val capacity = health(cycles, settings).capacityMah
        if (sample.plugged) {
            if (active == null || !active.charging || active.socDiscontinuity) return BatteryTimeEstimates()
            // Plug presence defines a session; Android's status determines whether a
            // countdown can currently run. An already reached target needs no forecast.
            if (sample.status != STATUS_CHARGING) return BatteryTimeEstimates(
                toTargetMs = 0L.takeIf { sample.percentage >= settings.chargeTargetPct.coerceIn(1, 100) },
                toFullMs = 0L.takeIf { sample.percentage == 100 })
            val duration = active.measuredMs + active.missingMs
            val points = active.endPct - active.startPct
            val observedDuration = usable.filter { it.cycleId == active.id && it.charging }
                .sumOf { it.measuredMs + it.missingMs }
            val rate = if (duration >= MIN_DURATION && points >= MIN_POINTS &&
                active.plugType == sample.plugType &&
                (active.missingMs.toDouble() / duration <= 0.1 || observedDuration.toDouble() / duration >= 0.9) &&
                active.rejectionReason != "INCOMPLETE_RESTORE")
                points.toDouble() / duration else null
            val chargeIntervals = usable.filter { it.charging && it.plugType == sample.plugType && it.endPct >= it.startPct }
            val bins = (0..9).associateWith { bin -> binRate(chargeIntervals, bin * 10, (bin + 1) * 10) }
            var usedHistory = false
            var usedSession = false
            fun time(target: Int): Long? {
                if (target <= sample.percentage) return 0L
                var total = 0.0
                var history = false
                var session = false
                for (pct in sample.percentage until target) {
                    // SOC timing captures taper and the local mAh/% relationship.
                    // Full-cycle capacity cannot establish a linear local conversion.
                    val speed = bins[pct / 10]?.also { history = true }
                        ?: rate?.also { session = true } ?: return null
                    total += 1 / speed
                }
                return bounded(total)?.also {
                    usedHistory = usedHistory || history
                    usedSession = usedSession || session
                }
            }
            val target = time(settings.chargeTargetPct.coerceIn(1, 100))
            val full = time(100)
            return BatteryTimeEstimates(toTargetMs = target, toFullMs = full, source = when {
                usedHistory && usedSession -> "SOC_HISTORY_SESSION"
                usedHistory -> "SOC_HISTORY"
                usedSession -> "SESSION"
                else -> null
            })
        }
        val discharge = usable.filter { !it.charging && it.endPct <= it.startPct }
        var usedMah = false
        fun remaining(screen: Boolean?): Long? {
            // Do not treat wake-only CURRENT sampling as full standby coverage.
            // Remove the affected cycle for this screen bucket; independently covered
            // screen-on observations and other complete cycles remain usable.
            val uncoveredSleepCycles = eligible.filter {
                !it.charging && (screen == null || it.screenOn == screen) &&
                    it.deepSleepMs > 0 && !hasCoverage(it)
            }.map { it.cycleId }.toSet()
            val selected = discharge.filter {
                (screen == null || it.screenOn == screen) && it.cycleId !in uncoveredSleepCycles
            }
            val mahRate = capacity?.let { observedMahRate(selected, it) }
            if (mahRate != null) {
                val prediction = bounded(sample.percentage / mahRate)
                if (prediction != null) usedMah = true
                return prediction
            }
            val rate = observedRate(selected) ?: return null
            return bounded(sample.percentage / rate)
        }
        val on = remaining(true)
        val off = remaining(false)
        val mixed = remaining(null)
        return BatteryTimeEstimates(screenOnRemainingMs = on, screenOffRemainingMs = off,
            mixedRemainingMs = mixed, source = (if (usedMah) "MAH_HISTORY" else "HISTORY")
                .takeIf { on != null || off != null || mixed != null })
    }

    private fun hasCoverage(interval: BatteryInterval): Boolean =
        interval.missingMs >= 0 && (interval.source == "SOC_ONLY" && interval.measuredMs == 0L &&
            interval.missingMs > 0 && interval.deepSleepMs == 0L ||
            interval.measuredMs > 0 && interval.missingMs.toDouble() /
                (interval.measuredMs + interval.missingMs) <= 0.1)

    // Flat SOC intervals belong in the denominator; dropping them biases every rate upward.
    private fun observedRate(intervals: List<BatteryInterval>): Double? {
        val duration = intervals.sumOf { it.measuredMs + it.missingMs }
        val points = intervals.sumOf { it.startPct - it.endPct }
        return if (duration >= MIN_DURATION && points >= MIN_POINTS) points.toDouble() / duration else null
    }

    /** Return equivalent percentage/ms while preserving signed net drain across all plateaus. */
    private fun observedMahRate(intervals: List<BatteryInterval>, capacity: Double): Double? {
        val measured = intervals.filter { it.source in MEASURED_SOURCES && it.netMah.isFinite() }
        val duration = measured.sumOf { it.measuredMs + it.missingMs }
        val drain = -measured.sumOf { it.netMah }
        return if (duration >= MIN_DURATION && drain >= capacity * MIN_POINTS / 100)
            drain / capacity * 100 / duration else null
    }

    private fun binRate(intervals: List<BatteryInterval>, low: Int, high: Int): Double? {
        var duration = 0.0
        var points = 0.0
        intervals.forEach {
            val delta = it.endPct - it.startPct
            val overlap = (minOf(it.endPct, high) - maxOf(it.startPct, low)).coerceAtLeast(0)
            val time = (it.measuredMs + it.missingMs).toDouble()
            if (delta > 0 && overlap > 0) {
                points += overlap
                duration += time * overlap / delta
            } else if (delta == 0 && it.startPct in low until high) duration += time
        }
        return if (duration >= MIN_DURATION && points >= MIN_POINTS) points / duration else null
    }

    private fun bounded(ms: Double): Long? = ms.takeIf { it.isFinite() && it >= 0 && it <= MAX_PREDICTION }?.toLong()

    // Evidence and retention limits, not default battery measurements or speeds.
    private const val MIN_DURATION = 600_000L
    private const val MIN_POINTS = 2
    private const val HISTORY_WINDOW_MS = 30L * 24 * 3_600_000
    private const val MAX_PREDICTION = 30.0 * 24 * 3_600_000
    private const val STATUS_CHARGING = 2
    private val MEASURED_SOURCES = setOf("CURRENT", "COUNTER")
}
