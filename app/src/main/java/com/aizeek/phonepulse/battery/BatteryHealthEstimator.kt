package com.aizeek.phonepulse.battery

import kotlin.math.abs
import kotlin.math.sqrt

/** Capacity evidence from independent charging sessions; never bridges an unmeasured unplug gap. */
internal object BatteryHealthEstimator {
    const val MIN_SOC_CHANGE = 25
    const val MIN_COVERAGE = 0.9
    private const val REFERENCE_SOC_CHANGE = 60
    private const val MAX_SAMPLES = 30
    private const val WINDOW_MS = 30L * 24 * 3_600_000

    data class Sample(val cycle: BatteryCycle, val capacityMah: Double,
        val startPct: Int, val endPct: Int, val coverage: Double) {
        val span: Int get() = endPct - startPct
        // Endpoint SOC quantization has greater relative error in a smaller SOC span.
        // Cap leverage at a reference span; one long session must not overwhelm the history.
        val weight: Double get() = (span.coerceAtMost(REFERENCE_SOC_CHANGE) * coverage).let { it * it }
    }

    fun sample(cycle: BatteryCycle, settings: BatteryMonitorSettings): Sample? {
        if (!cycle.charging || cycle.excluded || cycle.calibrationRevision != settings.calibrationRevision ||
            cycle.startPct !in 0..100) return null
        val frozen = cycle.fullChargeMah?.let { it.isFinite() && it > 0 } == true &&
            cycle.estimatedCapacityMah?.let { it.isFinite() && it > 0 } == true
        if (frozen && (cycle.rejectionReason == null || cycle.rejectionReason == "INCOMPLETE_RESTORE")) {
            if (100 - cycle.startPct < MIN_SOC_CHANGE) return null
            // The stored capacity proves its prefix qualified. Later maintenance changed
            // the duration/SOC, so only the guaranteed minimum prefix coverage is known.
            val capacity = cycle.fullChargeMah!! * 100 / (100 - cycle.startPct)
            return capacity.takeIf { it.isFinite() && it > 0 }
                ?.let { Sample(cycle, it, cycle.startPct, 100, MIN_COVERAGE) }
        }
        // An unqualified full-charge prefix cannot be repaired with maintenance charge.
        if (cycle.fullChargeMah != null) return null
        if (cycle.endTime == null || cycle.endPct !in 0..100 || cycle.socDiscontinuity ||
            cycle.rejectionReason !in setOf(null, "SMALL_SOC_CHANGE") ||
            cycle.endPct - cycle.startPct < MIN_SOC_CHANGE || cycle.measuredMs <= 0 || cycle.missingMs < 0) return null
        val duration = cycle.measuredMs.toDouble() + cycle.missingMs
        val coverage = cycle.measuredMs / duration
        if (coverage < MIN_COVERAGE || !cycle.netMah.isFinite() || cycle.netMah <= 0) return null
        // Re-evaluate legacy short sessions from retained measurements without rewriting history.
        val capacity = cycle.netMah * 100 / (cycle.endPct - cycle.startPct)
        return capacity.takeIf { it.isFinite() && it > 0 }
            ?.let { Sample(cycle, it, cycle.startPct, cycle.endPct, coverage) }
    }

    fun estimate(cycles: List<BatteryCycle>, settings: BatteryMonitorSettings): BatteryHealthEstimate {
        val newestTime = cycles.maxOfOrNull { it.lastTime } ?: return BatteryHealthEstimate()
        val samples = cycles.asSequence().mapNotNull { sample(it, settings) }
            .filter { it.cycle.lastTime >= newestTime - WINDOW_MS }
            .sortedByDescending { it.cycle.lastTime }
            .distinctBy { it.cycle.id to it.cycle.startTime }.take(MAX_SAMPLES).toList()
        if (samples.isEmpty()) return BatteryHealthEstimate()

        val center = median(samples.map { it.capacityMah })
        val mad = median(samples.map { abs(it.capacityMah - center) })
        // A nonzero floor handles quantized identical samples with a single bad reading.
        // These are robust weighting parameters, not a claimed sensor accuracy or confidence interval.
        val cutoff = maxOf(center * 0.05, 3 * 1.4826 * mad)
        val weights = samples.map {
            val residual = abs(it.capacityMah - center)
            val attenuation = if (samples.size >= 3 && residual > cutoff) {
                (cutoff / residual).let { ratio -> ratio * ratio }
            } else 1.0
            it.weight * attenuation
        }
        val weightSum = weights.sum()
        val capacity = samples.indices.sumOf { samples[it].capacityMah * (weights[it] / weightSum) }
        if (!capacity.isFinite() || capacity <= 0) return BatteryHealthEstimate()

        // Report dispersion BEFORE robust attenuation. Otherwise downweighting a fault
        // could falsely make the history look consistent.
        val baseWeightSum = samples.sumOf { it.weight }
        val spread = if (samples.size > 1) sqrt(samples.sumOf {
            val relative = (it.capacityMah - capacity) / capacity
            it.weight / baseWeightSum * relative * relative
        }) * 100 else null
        val effectiveCount = weightSum * weightSum / weights.sumOf { it * it }
        val socCoverage = samples.flatMap { (it.startPct until it.endPct).toList() }.toSet().size
        val referenceCount = samples.count { it.span >= REFERENCE_SOC_CHANGE }
        val consistent = effectiveCount >= 3 - 1e-9 && socCoverage >= REFERENCE_SOC_CHANGE &&
            spread != null && spread <= 10
        return BatteryHealthEstimate(
            capacityMah = capacity,
            healthPct = settings.designCapacityMah?.takeIf { it.isFinite() && it > 0 }?.let { capacity / it * 100 },
            acceptedCount = samples.size, spreadPct = spread,
            shortSampleCount = samples.size - referenceCount, referenceSampleCount = referenceCount,
            effectiveSampleCount = effectiveCount, socCoveragePct = socCoverage,
            downWeightedCount = samples.indices.count { weights[it] < samples[it].weight },
            confidence = if (consistent) BatteryHealthConfidence.CONSISTENT else BatteryHealthConfidence.PRELIMINARY
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else sorted[middle - 1] / 2 + sorted[middle] / 2
    }
}
