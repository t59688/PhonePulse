package com.aizeek.phonepulse

import com.aizeek.phonepulse.battery.*
import org.junit.Assert.*
import org.junit.Test

class BatteryAnalyticsTest {
    private val settings = BatteryMonitorSettings(designCapacityMah = 4000.0)
    private fun sample(t: Long, pct: Int = 20, plugged: Boolean = true,
        current: Double? = 1_000_000.0, awake: Long = t, screen: Boolean = true,
        counter: Long? = null) = BatteryTelemetry(t, t, awake, pct, plugged, 2, screen, current, counter)

    @Test fun `delayed counter does not count already integrated charge twice`() {
        for (sign in listOf(1, -1)) {
            val engine = BatteryAnalytics()
            engine.accept(sample(0, plugged = sign > 0, current = sign * 360_000.0,
                counter = 1_000_000), settings)
            engine.accept(sample(5000, plugged = sign > 0, current = sign * 360_000.0,
                counter = 1_000_000), settings)
            val update = engine.accept(sample(10000, plugged = sign > 0,
                current = sign * 360_000.0, counter = 1_000_000L + sign * 1000), settings)
            assertEquals(sign * 1.0, update.active.netMah, 0.000001)
            assertEquals(10000L, update.active.measuredMs)
        }
    }

    @Test fun `high resolution counter does not change the awake measurement source`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = 360_000.0, counter = 1_000_000), settings)
        val update = engine.accept(sample(5000, current = 360_000.0, counter = 1_000_500), settings)
        assertEquals("CURRENT", update.interval!!.source)
        assertEquals(0.5, update.active.netMah, 0.000001)
    }

    @Test fun `counter covers sleep without repeating its delayed awake charge`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = 360_000.0, counter = 1_000_000), settings)
        engine.accept(sample(5000, current = 360_000.0, counter = 1_000_000), settings)
        val slept = engine.accept(sample(15000, current = 360_000.0, awake = 6000,
            counter = 1_001_500), settings)
        assertEquals(1.5, slept.active.netMah, 0.000001)
        assertEquals(1.0, slept.interval!!.netMah, 0.000001)
        assertEquals("COUNTER", slept.interval!!.source)
        val resumed = engine.accept(sample(20000, current = 360_000.0, awake = 11000,
            counter = 1_002_000), settings)
        assertEquals(2.0, resumed.active.netMah, 0.000001)
    }

    @Test fun `current loss uses counter without repeating earlier awake integration`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = 360_000.0, counter = 1_000_000), settings)
        engine.accept(sample(5000, current = 360_000.0, counter = 1_000_000), settings)
        val lost = engine.accept(sample(10000, current = null, counter = 1_001_000), settings)
        assertEquals(1.0, lost.active.netMah, 0.000001)
        assertEquals(0.5, lost.interval!!.netMah, 0.000001)
    }

    @Test fun `counter recovery after missing data does not fill the gap`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = 360_000.0, counter = 1_000_000), settings)
        engine.accept(sample(5000, current = null, counter = null), settings)
        val recovered = engine.accept(sample(10000, current = null, counter = 1_001_000), settings)
        assertEquals(0.0, recovered.active.netMah, 0.0)
        assertEquals(10000L, recovered.active.missingMs)
        val next = engine.accept(sample(15000, current = null, counter = 1_001_500), settings)
        assertEquals(0.5, next.active.netMah, 0.000001)
        assertEquals(5000L, next.active.measuredMs)
    }

    @Test fun `power source change closes the old source with its previous current`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = 1_000_000.0).copy(plugType = "USB"), settings)
        val switched = engine.accept(sample(3600, current = 2_000_000.0)
            .copy(plugType = "AC"), settings)
        assertEquals("POWER_SOURCE_CHANGE", switched.completed!!.completionReason)
        assertEquals(1.0, switched.completed!!.netMah, 0.000001)
        assertEquals("USB", switched.interval!!.plugType)
        assertEquals("AC", switched.active.plugType)
        assertEquals(0.0, switched.active.netMah, 0.0)
    }

    @Test fun `signed sleep remainder can reverse earlier net charge with current evidence`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = 1_080_000.0, counter = 1_000_000), settings)
        engine.accept(sample(5000, current = -360_000.0, counter = 1_000_000), settings)
        val asleep = engine.accept(sample(10000, current = -360_000.0,
            awake = 6000, counter = 1_000_250), settings)
        assertEquals(-0.25, asleep.interval!!.netMah, 0.000001)
        assertEquals(0.25, asleep.active.netMah, 0.000001)
    }

    @Test fun `starting at full SOC cannot fabricate charge to first100`() {
        val engine = BatteryAnalytics()
        assertNull(engine.accept(sample(0, 100), settings).active.to100Mah)
        assertNull(engine.accept(sample(30000, 100), settings).active.to100Mah)
    }

    @Test fun `reaching100 without measured coverage cannot fabricate charge to first100`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, 99, current = null), settings)
        assertNull(engine.accept(sample(30000, 100, current = null), settings).active.to100Mah)
        engine.accept(sample(60000, 100), settings)
        assertNull(engine.accept(sample(90000, 100), settings).active.to100Mah)
    }

    @Test fun `trapezoid preserves signed net current and old screen bucket`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0), settings)
        val update = engine.accept(sample(3600, current = -500_000.0, screen = false), settings)
        assertEquals(0.25, update.active.netMah, 0.000001)
        assertEquals(0.25, update.active.screenOnMah, 0.000001)
        assertEquals(0.0, update.active.screenOffMah, 0.0)
        assertEquals(3600L, update.active.screenOnMeasuredMs)
        assertEquals(0L, update.active.screenOffMeasuredMs)
        assertTrue(update.interval!!.screenOn)
    }

    @Test fun `same millisecond screen event updates next bucket without duplicate integration`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0), settings)
        val event = engine.accept(sample(0, screen = false), settings)
        assertNull(event.interval)
        assertEquals(0.0, event.active.netMah, 0.0)
        val next = engine.accept(sample(3600, screen = false), settings)
        assertEquals(0.0, next.active.screenOnMah, 0.0)
        assertEquals(1.0, next.active.screenOffMah, 0.0)
        assertEquals(0L, next.active.screenOnMeasuredMs)
        assertEquals(3600L, next.active.screenOffMeasuredMs)
    }

    @Test fun `sleep and long gaps are missing without validated counter`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0), settings)
        val update = engine.accept(sample(120_000, awake = 10_000), settings)
        assertEquals(0L, update.active.measuredMs)
        assertEquals(120_000L, update.active.missingMs)
        assertEquals(110_000L, update.active.deepSleepMs)
        assertEquals(0.0, update.active.netMah, 0.0)
    }

    @Test fun `counter covers sleep once and reset is rejected`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, counter = 1_000_000), settings)
        val covered = engine.accept(sample(120_000, awake = 10_000, counter = 1_020_000), settings)
        assertEquals(20.0, covered.active.netMah, 0.0)
        assertEquals(120_000L, covered.active.measuredMs)
        assertEquals("COUNTER", covered.interval!!.source)
        val reset = engine.accept(sample(240_000, awake = 20_000, counter = 0), settings)
        assertEquals(20.0, reset.active.netMah, 0.0)
        assertEquals(120_000L, reset.active.missingMs)
    }

    @Test fun `implausible counter and unavailable current cannot fabricate charge`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = null, counter = 1_000_000), settings)
        val jump = engine.accept(sample(30_000, current = Double.NaN, counter = 2_000_000), settings)
        assertEquals(0.0, jump.active.netMah, 0.0)
        assertEquals(30_000L, jump.active.missingMs)
    }

    @Test fun `frozen counter does not cover sleep SOC changes or unknown current`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = null, counter = 1_000_000), settings)
        val stale = engine.accept(sample(3_600_000, 80, current = null,
            awake = 1000, counter = 1_000_000), settings)
        assertEquals(0L, stale.active.measuredMs)
        assertEquals(3_600_000L, stale.active.missingMs)
        val completed = engine.accept(sample(3_600_000, 80, plugged = false,
            current = null, awake = 1000, counter = 1_000_000), settings).completed!!
        assertNull(completed.estimatedCapacityMah)
        assertEquals("NO_MEASUREMENT", completed.rejectionReason)
        val unknown = BatteryAnalytics()
        unknown.accept(sample(0, current = null, counter = 1_000_000), settings)
        assertEquals(30_000L, unknown.accept(sample(30_000, current = null,
            counter = 1_000_000), settings).active.missingMs)
    }

    @Test fun `short current-supported zero counter measures a real zero net plateau`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = 0.0, counter = 1_000_000), settings)
        val zero = engine.accept(sample(30_000, current = 0.0, counter = 1_000_000), settings)
        assertEquals(30_000L, zero.active.measuredMs)
        assertEquals("COUNTER", zero.interval!!.source)
        assertEquals(0.0, zero.active.netMah, 0.0)
    }

    @Test fun `calibration applies to current and counter without removing discharge sign`() {
        val engine = BatteryAnalytics()
        val calibrated = settings.copy(invertCurrent = true, currentScale = 2.0, cellFactor = 2)
        engine.accept(sample(0), calibrated)
        val update = engine.accept(sample(3600), calibrated)
        assertEquals(-4.0, update.active.netMah, 0.0)
    }

    @Test fun `current unit correction does not multiply fixed counter units`() {
        val engine = BatteryAnalytics()
        val calibrated = settings.copy(currentScale = 1000.0)
        engine.accept(sample(0, current = 1000.0, counter = 1_000_000), calibrated)
        val update = engine.accept(sample(120_000, current = 1000.0, awake = 10_000, counter = 1_020_000), calibrated)
        assertEquals(20.0, update.active.netMah, 0.0)
        assertEquals("COUNTER", update.interval!!.source)
    }

    @Test fun `clock read skew is tolerated and unplugged current does not bleed into prior cycle`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0), settings)
        val first = engine.accept(sample(3600, awake = 3599), settings)
        assertEquals(1.0, first.active.netMah, 0.0)
        assertEquals(0L, first.active.deepSleepMs)
        val unplug = engine.accept(sample(7200, plugged = false, current = -2_000_000.0, awake = 7199), settings)
        assertEquals(2.0, unplug.completed!!.netMah, 0.0)
        assertFalse(unplug.active.charging)
    }

    @Test fun `plugged full and paused remain one cycle and first100 stays frozen`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, 99), settings)
        val full = engine.accept(sample(30_000, 100).copy(status = 5), settings)
        val paused = engine.accept(sample(60_000, 100).copy(status = 4), settings)
        assertNull(paused.completed)
        assertEquals(full.active.to100Mah, paused.active.to100Mah)
        assertTrue(paused.active.netMah > paused.active.to100Mah!!)
    }

    @Test fun `full termination freezes health charge after five minutes but keeps measuring pulses`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, 20, current = 600_000.0), settings)
        for (step in 1..320) engine.accept(sample(step * 60_000L,
            20 + step / 4, current = 600_000.0), settings)
        val fullTime = 320 * 60_000L
        var full: BatteryCycle? = null
        for (step in 1..6) full = engine.accept(sample(fullTime + step * 60_000,
            100, current = 50_000.0).copy(status = 5), settings).active
        val frozen = full!!.fullChargeMah!!
        assertTrue(frozen > full.to100Mah!!)
        val pulse = engine.accept(sample(fullTime + 7 * 60_000, 100, current = 1_000_000.0), settings)
        assertEquals(frozen, pulse.active.fullChargeMah!!, 0.0)
        assertTrue(pulse.active.netMah > frozen)
        val completed = engine.accept(sample(fullTime + 7 * 60_000, 100, plugged = false), settings).completed!!
        assertEquals(frozen * 100 / 80, completed.estimatedCapacityMah!!, 0.0)
    }

    @Test fun `unavailable current or sampling gap resets full termination confirmation`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, 100, current = 50_000.0).copy(status = 5), settings)
        for (step in 1..4) engine.accept(sample(step * 60_000L, 100,
            current = 50_000.0).copy(status = 5), settings)
        assertNull(engine.accept(sample(5 * 60_000L, 100, current = null).copy(status = 5), settings).active.fullChargeMah)
        assertNull(engine.accept(sample(20 * 60_000L, 100, current = 50_000.0).copy(status = 5), settings).active.fullChargeMah)
    }

    @Test fun `confirmed full capacity survives subsequent restore idle gap and reboot`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, 20, current = 600_000.0), settings)
        for (step in 1..320) engine.accept(sample(step * 60_000L,
            20 + step / 4, current = 600_000.0), settings)
        val fullTime = 320 * 60_000L
        var confirmed: BatteryCycle? = null
        for (step in 1..6) confirmed = engine.accept(sample(fullTime + step * 60_000,
            100, current = 50_000.0).copy(status = 5), settings).active
        assertNotNull(confirmed!!.estimatedCapacityMah)
        val restored = BatteryAnalytics()
        restored.restore(confirmed)
        val idle = restored.accept(sample(fullTime + 24 * 3_600_000L, 100,
            current = null), settings).active
        assertEquals("INCOMPLETE_RESTORE", idle.rejectionReason)
        val closed = restored.accept(sample(10_000, 100), settings).completed!!
        assertEquals("REBOOT", closed.completionReason)
        assertNull(closed.rejectionReason)
        assertEquals(confirmed.estimatedCapacityMah, closed.estimatedCapacityMah)
        assertEquals(1, BatteryEstimates.health(listOf(closed), settings).acceptedCount)
        val recalibrated = BatteryAnalytics()
        recalibrated.restore(confirmed)
        val split = recalibrated.accept(sample(fullTime + 7 * 60_000, 100),
            settings.copy(calibrationRevision = 1)).completed!!
        assertEquals("CALIBRATION_CHANGED", split.rejectionReason)
        assertNull(split.estimatedCapacityMah)
    }

    @Test fun `continuous unsupported current marks SOC only without measured capacity coverage`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0, current = null), settings)
        val supportedSoc = engine.accept(sample(30_000, current = null), settings)
        assertEquals("SOC_ONLY", supportedSoc.interval!!.source)
        assertEquals(0L, supportedSoc.active.measuredMs)
        assertEquals(30_000L, supportedSoc.active.missingMs)
        assertEquals("MISSING", engine.accept(sample(150_000, current = null), settings).interval!!.source)
    }

    @Test fun `calibration and reboot split sessions and restore never extrapolates`() {
        val engine = BatteryAnalytics()
        engine.accept(sample(0), settings)
        val old = engine.accept(sample(30_000), settings).active.copy(id = 9)
        engine.replaceActive(old)
        val changed = engine.accept(sample(60_000), settings.copy(calibrationRevision = 1))
        assertEquals(9L, changed.completed!!.id)
        assertEquals("CALIBRATION_CHANGED", changed.completed!!.completionReason)
        assertEquals(0L, changed.active.id)
        val restored = BatteryAnalytics()
        restored.restore(old)
        val resumed = restored.accept(sample(90_000), settings)
        assertEquals(old.netMah, resumed.active.netMah, 0.0)
        assertEquals(60_000L, resumed.active.missingMs)
        val reboot = restored.accept(sample(10_000), settings)
        assertEquals("REBOOT", reboot.completed!!.completionReason)
        assertNull(reboot.completed!!.estimatedCapacityMah)
    }

    @Test fun `well covered sixty point charging cycle estimates capacity and excludes jumps`() {
        fun complete(jump: Boolean): BatteryCycle {
            val engine = BatteryAnalytics()
            engine.accept(sample(0, current = 600_000.0), settings)
            for (step in 1..240) engine.accept(sample(step * 60_000L,
                if (jump && step == 120) 90 else 20 + step / 4, current = 600_000.0), settings)
            return engine.accept(sample(240 * 60_000L, 80, plugged = false), settings).completed!!
        }
        assertEquals(4000.0, complete(false).estimatedCapacityMah!!, 0.1)
        assertEquals("SOC_DISCONTINUITY", complete(true).rejectionReason)
    }
}
