package com.aizeek.phonepulse

import com.aizeek.phonepulse.battery.*
import org.junit.Assert.*
import org.junit.Test

class BatteryEstimatesTest {
    private val hour = 3_600_000L
    private val settings = BatteryMonitorSettings(designCapacityMah = 4000.0)
    private fun cycle(id: Long, capacity: Double = 4000.0) = BatteryCycle(id = id,
        charging = true, startTime = 0, endTime = id, lastTime = id, lastElapsedMs = id,
        startPct = 20, endPct = 80, measuredMs = hour, netMah = capacity * 0.6,
        estimatedCapacityMah = capacity)
    private fun interval(start: Long, end: Long, from: Int, to: Int, screen: Boolean) =
        BatteryInterval(cycleId = 1, startTime = start, endTime = end, screenOn = screen,
            charging = false, startPct = from, endPct = to, netMah = -40.0,
            measuredMs = end - start, missingMs = 0, deepSleepMs = 0, source = "CURRENT")

    @Test fun `health retains accepted matching calibration and never supplies default capacity`() {
        assertNull(BatteryEstimates.health(emptyList(), settings).capacityMah)
        val health = BatteryEstimates.health((1L..6L).map { cycle(it, it * 1000.0) } +
            cycle(7).copy(excluded = true) + cycle(8).copy(calibrationRevision = 1), settings)
        assertEquals(6, health.acceptedCount)
        assertEquals(3500.0, health.capacityMah!!, 0.000001)
        assertEquals(87.5, health.healthPct!!, 0.000001)
    }

    @Test fun `confirmed full capacity counts before unplugging but unfinished charge does not`() {
        val active = cycle(2).copy(endTime = null, endPct = 100, fullChargeMah = 3200.0)
        val health = BatteryEstimates.health(listOf(cycle(1), active), settings)
        assertEquals(2, health.acceptedCount)
        assertEquals(4000.0, health.capacityMah!!, 0.0)
        assertEquals(1, BatteryEstimates.health(listOf(cycle(1), active.copy(fullChargeMah = null)), settings).acceptedCount)
    }

    @Test fun `discharge rates include plateaus and require observed change`() {
        val sample = BatteryTelemetry(hour, hour, hour, 50, false, 3, true, null)
        val intervals = listOf(interval(0, hour / 2, 60, 60, true),
            interval(hour / 2, hour, 60, 50, true))
        val estimate = BatteryEstimates.estimate(sample, null, emptyList(), intervals, settings)
        assertEquals(5 * hour, estimate.screenOnRemainingMs)
        assertNull(estimate.screenOffRemainingMs)
        assertNull(BatteryEstimates.estimate(sample, null, emptyList(), listOf(interval(0, hour, 60, 60, true)), settings).mixedRemainingMs)
    }

    @Test fun `charging uses whole session duration and stale samples return unknown`() {
        val active = cycle(1).copy(endTime = null, startPct = 20, endPct = 40,
            lastTime = hour, lastElapsedMs = hour)
        val sample = BatteryTelemetry(hour, hour, hour, 40, true, 2, true, null)
        val estimate = BatteryEstimates.estimate(sample, active, emptyList(), emptyList(), settings)
        assertEquals(2 * hour, estimate.toTargetMs)
        assertEquals(3 * hour, estimate.toFullMs)
        assertNull(BatteryEstimates.estimate(sample.copy(timestamp = 2 * hour), active, emptyList(), emptyList(), settings).toFullMs)
    }

    @Test fun `charging learns slower high SOC bins only for matching plug type`() {
        val active = cycle(1).copy(endTime = null, startPct = 60, endPct = 80,
            lastTime = 3 * hour, lastElapsedMs = 3 * hour, plugType = "USB")
        val sample = BatteryTelemetry(3 * hour, 3 * hour, 3 * hour, 80, true, 2, true, null, plugType = "USB")
        val slow = interval(0, hour, 80, 90, true).copy(charging = true, plugType = "USB")
        val slower = interval(hour, 3 * hour, 90, 100, true).copy(charging = true, plugType = "USB")
        val result = BatteryEstimates.estimate(sample, active, emptyList(), listOf(slow, slower), settings)
        assertEquals(3 * hour, result.toFullMs)
        assertEquals("SOC_HISTORY", result.source)
        val otherPlug = BatteryEstimates.estimate(sample, active, emptyList(),
            listOf(slow.copy(plugType = "AC"), slower.copy(plugType = "AC")), settings)
        assertEquals(hour, otherPlug.toFullMs)
    }

    @Test fun `measured mAh and accepted capacity predict discharge without invented percentage changes`() {
        val healthy = cycle(99)
        val discharge = cycle(1).copy(charging = false, startPct = 50, endPct = 50,
            rejectionReason = "NOT_CHARGING", estimatedCapacityMah = null)
        val sample = BatteryTelemetry(hour, hour, hour, 50, false, 3, true, -400_000.0)
        val measured = interval(0, hour, 50, 50, true).copy(netMah = -400.0)
        val estimate = BatteryEstimates.estimate(sample, null, listOf(healthy, discharge), listOf(measured), settings)
        assertEquals(5 * hour, estimate.screenOnRemainingMs)
        assertEquals("MAH_HISTORY", estimate.source)
        assertNull(BatteryEstimates.estimate(sample, null, listOf(discharge), listOf(measured), settings).screenOnRemainingMs)
        assertNull(BatteryEstimates.estimate(sample, null, listOf(healthy, discharge),
            listOf(measured.copy(source = "MISSING", measuredMs = 0, missingMs = hour)), settings).screenOnRemainingMs)
    }

    @Test fun `charging target follows observed SOC time instead of linear full capacity conversion`() {
        val active = cycle(1).copy(endTime = null, startPct = 60, endPct = 80,
            lastTime = hour, lastElapsedMs = hour, plugType = "USB")
        val sample = BatteryTelemetry(hour, hour, hour, 80, true, 2, true, 600_000.0, plugType = "USB")
        val observed = interval(0, hour, 80, 90, true).copy(charging = true,
            netMah = 600.0, plugType = "USB")
        val estimate = BatteryEstimates.estimate(sample, active, listOf(cycle(99)),
            listOf(observed), settings.copy(chargeTargetPct = 90))
        assertEquals(hour, estimate.toTargetMs)
        assertEquals("SOC_HISTORY_SESSION", estimate.source)
    }

    @Test fun `plugged paused or unknown charging status does not show a countdown`() {
        val active = cycle(1).copy(endTime = null, startPct = 20, endPct = 40,
            lastTime = hour, lastElapsedMs = hour)
        val sample = BatteryTelemetry(hour, hour, hour, 40, true, 4, true, 0.0)
        for (status in listOf(1, 3, 4, 5)) {
            val estimate = BatteryEstimates.estimate(sample.copy(status = status), active,
                emptyList(), emptyList(), settings)
            assertNull(estimate.toTargetMs)
            assertNull(estimate.toFullMs)
            assertNull(estimate.source)
        }
    }

    @Test fun `charging history outside requested range does not label session forecast as history`() {
        val active = cycle(1).copy(endTime = null, startPct = 60, endPct = 80,
            lastTime = hour, lastElapsedMs = hour, plugType = "USB")
        val sample = BatteryTelemetry(hour, hour, hour, 80, true, 2, true, null, plugType = "USB")
        val earlier = interval(0, hour, 20, 30, true).copy(charging = true, plugType = "USB")
        val estimate = BatteryEstimates.estimate(sample, active, emptyList(), listOf(earlier), settings)
        assertEquals(hour, estimate.toFullMs)
        assertEquals("SESSION", estimate.source)
    }

    @Test fun `unmeasured sleep does not turn awake discharge into standby or mixed forecast`() {
        val sample = BatteryTelemetry(3 * hour, 3 * hour, 2 * hour, 50, false, 3, false, null)
        val observations = listOf(interval(0, hour, 70, 60, true),
            interval(hour, 2 * hour, 60, 55, false),
            interval(2 * hour, 3 * hour, 55, 50, false).copy(source = "MISSING",
                measuredMs = 0, missingMs = hour, deepSleepMs = hour))
        val estimate = BatteryEstimates.estimate(sample, null, emptyList(), observations, settings)
        assertEquals(5 * hour, estimate.screenOnRemainingMs)
        assertNull(estimate.screenOffRemainingMs)
        assertNull(estimate.mixedRemainingMs)

        val coveredSleep = observations.last().copy(source = "COUNTER", measuredMs = hour,
            missingMs = 0, netMah = -200.0)
        val covered = BatteryEstimates.estimate(sample, null, emptyList(),
            observations.dropLast(1) + coveredSleep, settings)
        assertEquals(10 * hour, covered.screenOffRemainingMs)
        assertNotNull(covered.mixedRemainingMs)
    }

    @Test fun `SOC only continuous observations predict without granting mAh or health coverage`() {
        val engine = BatteryAnalytics()
        val intervals = mutableListOf<BatteryInterval>()
        var active = engine.accept(BatteryTelemetry(0, 0, 0, 20, true, 2, true, null), settings).active
        for (step in 1..60) {
            val update = engine.accept(BatteryTelemetry(step * 60_000L, step * 60_000L,
                step * 60_000L, 20 + step / 3, true, 2, true, null), settings)
            active = update.active
            intervals += update.interval!!
        }
        val sample = BatteryTelemetry(hour, hour, hour, 40, true, 2, true, null)
        val charge = BatteryEstimates.estimate(sample, active, emptyList(), intervals, settings)
        assertEquals(2 * hour, charge.toTargetMs)
        assertEquals(3 * hour, charge.toFullMs)
        assertNull(BatteryEstimates.estimate(sample, active, emptyList(),
            intervals.map { it.copy(source = "MISSING") }, settings).toFullMs)
        assertEquals(0L, active.measuredMs)
        val discharge = intervals.map { it.copy(charging = false,
            startPct = 100 - it.startPct, endPct = 100 - it.endPct) }
        val remaining = BatteryEstimates.estimate(sample.copy(plugged = false, percentage = 60),
            null, emptyList(), discharge, settings)
        assertEquals(3 * hour, remaining.screenOnRemainingMs)
        assertEquals("HISTORY", remaining.source)
    }
}
