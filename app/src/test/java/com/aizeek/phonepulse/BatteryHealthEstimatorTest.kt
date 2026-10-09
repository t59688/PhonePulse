package com.aizeek.phonepulse

import com.aizeek.phonepulse.battery.*
import org.junit.Assert.*
import org.junit.Test

class BatteryHealthEstimatorTest {
    private val settings = BatteryMonitorSettings(designCapacityMah = 4000.0)
    private fun charge(id: Long, start: Int = 30, end: Int = 60, capacity: Double = 4000.0,
        coverage: Double = 1.0, legacy: Boolean = false): BatteryCycle {
        val duration = 3_600_000L
        return BatteryCycle(id = id, charging = true, startTime = id * duration,
            endTime = (id + 1) * duration, lastTime = (id + 1) * duration,
            lastElapsedMs = (id + 1) * duration, startPct = start, endPct = end,
            netMah = capacity * (end - start) / 100,
            measuredMs = (duration * coverage).toLong(), missingMs = (duration * (1 - coverage)).toLong(),
            estimatedCapacityMah = capacity.takeUnless { legacy },
            rejectionReason = "SMALL_SOC_CHANGE".takeIf { legacy })
    }

    @Test fun `complete historical short charges contribute without merging unplugged gaps`() {
        val estimate = BatteryEstimates.health(listOf(
            charge(1, 30, 70, legacy = true), charge(2, 70, 100, legacy = true)
        ), settings)
        assertEquals(2, estimate.acceptedCount)
        assertEquals(4000.0, estimate.capacityMah!!, 0.000001)
    }

    @Test fun `larger SOC spans carry more weight than a noisy short charge`() {
        val estimate = BatteryEstimates.health(listOf(
            charge(1, 20, 80, capacity = 4000.0), charge(2, 30, 55, capacity = 4200.0)
        ), settings)
        assertEquals(4029.585798816568, estimate.capacityMah!!, 0.000001)
    }

    @Test fun `one corrupt reading cannot dominate a group of consistent charges`() {
        val samples = (1L..5L).map { charge(it, 20, 80) } + charge(6, 20, 80, capacity = 10000.0)
        val estimate = BatteryEstimates.health(samples, settings)
        // The arithmetic mean is 5000 mAh; bounded weighting must stay within 1% of 4000.
        assertEquals(4000.0, estimate.capacityMah!!, 40.0)
        assertEquals(6, estimate.acceptedCount)
        assertTrue(estimate.spreadPct!! > 10)
    }

    @Test fun `short charges retain enough recent observations to accumulate evidence`() {
        val estimate = BatteryEstimates.health((1L..35L).map { charge(it) }, settings)
        assertEquals(30, estimate.acceptedCount)
        assertEquals(4000.0, estimate.capacityMah!!, 0.000001)
    }

    @Test fun `the same durable session is counted once`() {
        val first = charge(1, 20, 80)
        assertEquals(2, BatteryEstimates.health(listOf(first, first, charge(2, 20, 80)), settings).acceptedCount)
    }

    @Test fun `short historical samples still require complete trustworthy measurements`() {
        val good = charge(1, legacy = true)
        val bad = listOf(
            charge(2, legacy = true, coverage = 0.8),
            charge(3, legacy = true).copy(socDiscontinuity = true),
            charge(4, legacy = true).copy(rejectionReason = "INCOMPLETE_RESTORE"),
            charge(5, legacy = true).copy(calibrationRevision = 1),
            charge(6, legacy = true).copy(netMah = Double.NaN),
            charge(7, legacy = true).copy(netMah = -1200.0),
            charge(8, legacy = true).copy(measuredMs = 0),
            charge(9, 30, 54, legacy = true),
            charge(10, legacy = true).copy(excluded = true)
        )
        val estimate = BatteryEstimates.health(listOf(good) + bad, settings)
        assertEquals(1, estimate.acceptedCount)
        assertEquals(4000.0, estimate.capacityMah!!, 0.000001)
    }

    @Test fun `consistent low capacity is not rejected by nominal capacity bounds`() {
        val estimate = BatteryEstimates.health((1L..6L).map { charge(it, capacity = 2000.0) }, settings)
        assertEquals(6, estimate.acceptedCount)
        assertEquals(50.0, estimate.healthPct!!, 0.000001)
    }

    @Test fun `repeated narrow SOC observations remain preliminary despite many agreeing samples`() {
        val estimate = BatteryEstimates.health((1L..10L).map { charge(it, 50, 80) }, settings)
        assertEquals(BatteryHealthConfidence.PRELIMINARY, estimate.confidence)
        assertEquals(30, estimate.socCoveragePct)
        assertEquals(10, estimate.shortSampleCount)
        assertEquals(0, estimate.referenceSampleCount)
    }

    @Test fun `complementary short sessions can build consistent evidence over a broad SOC range`() {
        val estimate = BatteryEstimates.health(listOf(
            charge(1, 10, 40), charge(2, 40, 70), charge(3, 70, 100)
        ), settings)
        assertEquals(BatteryHealthConfidence.CONSISTENT, estimate.confidence)
        assertEquals(90, estimate.socCoveragePct)
        assertEquals(3.0, estimate.effectiveSampleCount, 0.000001)
    }

    @Test fun `expired capacity samples are not kept by a new discharge session`() {
        val old = charge(1, 20, 80, capacity = 6000.0)
        val recent = charge(1000, 20, 80)
        val estimate = BatteryEstimates.health(listOf(old, recent,
            charge(1001).copy(charging = false, rejectionReason = "NOT_CHARGING")), settings)
        assertEquals(1, estimate.acceptedCount)
        assertEquals(4000.0, estimate.capacityMah!!, 0.000001)
    }

    @Test fun `legacy unqualified full prefix cannot use later maintenance to fabricate a qualified capacity`() {
        val legacy = charge(1, 60, 100, legacy = true).copy(fullChargeMah = 1600.0, netMah = 2000.0)
        assertNull(BatteryEstimates.health(listOf(legacy), settings).capacityMah)
    }

    @Test fun `confirmed capacity uses frozen charge rather than a mismatched cached capacity`() {
        val frozen = charge(1, 20, 100).copy(fullChargeMah = 3200.0, estimatedCapacityMah = 5000.0)
        assertEquals(4000.0, BatteryEstimates.health(listOf(frozen), settings).capacityMah!!, 0.000001)
    }

    @Test fun `preliminary short capacity cannot drive mAh remaining time until evidence is consistent`() {
        val discharge = charge(9).copy(charging = false, rejectionReason = "NOT_CHARGING",
            lastTime = 6 * 3_600_000L, endTime = 6 * 3_600_000L)
        val sample = BatteryTelemetry(6 * 3_600_000L, 6 * 3_600_000L, 6 * 3_600_000L,
            50, false, 3, true, -400_000.0)
        val measured = BatteryInterval(cycleId = 9, startTime = 5 * 3_600_000L,
            endTime = 6 * 3_600_000L, screenOn = true, charging = false, startPct = 50,
            endPct = 50, netMah = -400.0, measuredMs = 3_600_000, missingMs = 0,
            deepSleepMs = 0, source = "CURRENT")
        assertNull(BatteryEstimates.estimate(sample, null, listOf(charge(1), discharge),
            listOf(measured), settings).screenOnRemainingMs)
        val broad = listOf(charge(1, 10, 40), charge(2, 40, 70), charge(3, 70, 100))
        assertEquals(5 * 3_600_000L, BatteryEstimates.estimate(sample, null, broad + discharge,
            listOf(measured), settings).screenOnRemainingMs)
    }
}
