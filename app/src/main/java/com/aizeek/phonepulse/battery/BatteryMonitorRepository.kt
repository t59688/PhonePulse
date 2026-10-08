package com.aizeek.phonepulse.battery

import androidx.room.withTransaction
import com.aizeek.phonepulse.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Shared by the service and UI; only the service feeds telemetry into the accumulator. */
class BatteryMonitorRepository(private val database: AppDatabase, scope: CoroutineScope) {
    private val dao = database.batteryMonitorDao()
    private val mutex = Mutex()
    private val analytics = BatteryAnalytics()
    private var initialized = false
    private var active: BatteryCycle? = null
    private var pending: BatteryInterval? = null
    private var lastCheckpointElapsed = 0L
    private var lastCleanupTime = 0L
    private val monitoringOwner = AtomicLong()
    fun beginMonitoring(): Long = monitoringOwner.incrementAndGet()
    private val runtime = MutableStateFlow(Runtime())
    private val operationError = MutableStateFlow<String?>(null)
    private data class Runtime(val sample: BatteryTelemetry? = null, val cycle: BatteryCycle? = null,
        val running: Boolean = false, val error: String? = null)

    val state: StateFlow<BatteryMonitorUiState> = combine(dao.observeState(), dao.observeCycles(),
        dao.observeIntervals(), runtime, operationError) { persisted, cycles, intervals, live, actionError ->
        val settings = persisted?.settings ?: BatteryMonitorSettings()
        val sample = live.sample
        val current = sample?.takeIf { live.running && live.error == null }?.currentUa?.times(settings.currentScale)?.times(settings.cellFactor)
            ?.times(if (settings.invertCurrent) -1 else 1)?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 20_000_000 }
        BatteryMonitorUiState(settings = settings, currentUa = current,
            chargeCounterUah = sample?.chargeCounterUah?.times(settings.cellFactor),
            lastSampleTime = sample?.timestamp, activeCycle = live.cycle ?: cycles.firstOrNull { it.endTime == null },
            cycles = cycles.filter { it.endTime != null },
            health = BatteryEstimates.health(cycles, settings),
            estimates = if (live.running && live.error == null) BatteryEstimates.estimate(sample, live.cycle, cycles, intervals, settings)
                else BatteryTimeEstimates(), error = actionError ?: live.error, isRunning = live.running)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), BatteryMonitorUiState())

    suspend fun sample(source: BatteryTelemetrySource, screenOn: Boolean,
        owner: Long = monitoringOwner.get()): BatteryCycle? = mutex.withLock {
        if (owner != monitoringOwner.get()) return@withLock null
        initialize()
        val settings = dao.getState()?.settings ?: BatteryMonitorSettings()
        val sample = source.read(screenOn, settings)
        val update = analytics.accept(sample, settings)
        val previousActive = active
        var next = update.active
        val force = update.completed != null || next.id == 0L ||
            sample.elapsedMs - lastCheckpointElapsed >= 60_000 || sample.elapsedMs < lastCheckpointElapsed
        val oldPending = pending
        val oldCheckpoint = lastCheckpointElapsed
        val oldCleanup = lastCleanupTime
        try { database.withTransaction {
            val interval = update.interval
            if (interval != null) appendInterval(interval)
            if (update.completed != null) {
                flushPending()
                dao.saveCycle(update.completed)
            }
            if (force) {
                flushPending()
                val id = dao.saveCycle(next)
                if (next.id == 0L) next = next.copy(id = id)
                lastCheckpointElapsed = sample.elapsedMs
            }
            if (lastCleanupTime == 0L || sample.timestamp - lastCleanupTime >= 24 * 3_600_000L) {
                dao.deleteOldIntervals(sample.timestamp - 30L * 24 * 3_600_000)
                dao.deleteOldCycles(sample.timestamp - 365L * 24 * 3_600_000)
                lastCleanupTime = sample.timestamp
            }
        } } catch (e: Exception) {
            pending = oldPending
            active = previousActive
            analytics.restore(previousActive)
            lastCheckpointElapsed = oldCheckpoint
            lastCleanupTime = oldCleanup
            throw e
        }
        // Generated IDs and durable alarm flags must travel back into the pure accumulator.
        if (next.id == 0L && previousActive?.id != null) next = next.copy(id = previousActive.id)
        active = next
        analytics.replaceActive(next)
        runtime.value = Runtime(sample, next, running = true)
        next
    }

    suspend fun saveSettings(requested: BatteryMonitorSettings) = mutex.withLock {
        val validation = validateBatterySettings(requested)
        if (validation != null) { operationError.value = validation; return@withLock }
        initialize()
        val old = dao.getState()?.settings ?: BatteryMonitorSettings()
        val calibrationChanged = old.invertCurrent != requested.invertCurrent ||
            old.currentScale != requested.currentScale || old.cellFactor != requested.cellFactor
        val next = requested.copy(calibrationRevision = old.calibrationRevision + if (calibrationChanged) 1 else 0)
        val resetAlarm = old.chargeTargetPct != next.chargeTargetPct ||
            !old.chargeAlarmEnabled && next.chargeAlarmEnabled
        val savedActive = active?.let { if (resetAlarm) it.copy(alarmNotified = false, alarmMuted = false) else it }
        val oldPending = pending
        try { database.withTransaction {
            flushPending()
            savedActive?.let { dao.saveCycle(it) }
            dao.saveState(BatteryMonitorState(settings = next))
        } } catch (e: Exception) { pending = oldPending; throw e }
        active = savedActive
        savedActive?.let { analytics.replaceActive(it) }
        operationError.value = null
        runtime.value = runtime.value.copy(cycle = active,
            sample = if (calibrationChanged) null else runtime.value.sample)
    }

    suspend fun setExcluded(id: Long, excluded: Boolean) = mutex.withLock {
        dao.setExcluded(id, excluded)
        operationError.value = null
    }

    suspend fun muteAlarm() = mutex.withLock {
        initialize()
        active?.takeIf { it.charging }?.let {
            val next = it.copy(alarmMuted = true)
            saveCheckpoint(next)
            active = next
            analytics.replaceActive(next)
            runtime.value = runtime.value.copy(cycle = next)
        }
    }

    /** Persist before alerting: retries and restarts cannot repeatedly ring for the same session. */
    suspend fun claimChargeAlarm(cycleId: Long, expectedTarget: Int? = null): BatteryCycle? = mutex.withLock {
        claimAlarmLocked(cycleId, expectedTarget)
    }

    suspend fun deliverChargeAlarm(cycleId: Long, show: (Int, Int) -> Unit) = mutex.withLock {
        val settings = dao.getState()?.settings ?: BatteryMonitorSettings()
        val claimed = claimAlarmLocked(cycleId, settings.chargeTargetPct) ?: return@withLock
        try { show(claimed.endPct, settings.chargeTargetPct)
        } catch (e: Exception) {
            val retryable = claimed.copy(alarmNotified = false)
            saveCheckpoint(retryable)
            active = retryable
            analytics.replaceActive(retryable)
            throw e
        }
    }

    private suspend fun claimAlarmLocked(cycleId: Long, expectedTarget: Int?): BatteryCycle? {
        val settings = dao.getState()?.settings ?: BatteryMonitorSettings()
        val cycle = active ?: return null
        if (cycle.id != cycleId || !cycle.charging || !settings.chargeAlarmEnabled ||
            cycle.endPct < settings.chargeTargetPct || cycle.alarmNotified || cycle.alarmMuted ||
            expectedTarget != null && expectedTarget != settings.chargeTargetPct) return null
        val claimed = cycle.copy(alarmNotified = true)
        saveCheckpoint(claimed)
        active = claimed
        analytics.replaceActive(claimed)
        runtime.value = runtime.value.copy(cycle = claimed)
        return claimed
    }

    suspend fun settings(): BatteryMonitorSettings = mutex.withLock {
        dao.getState()?.settings ?: BatteryMonitorSettings()
    }

    suspend fun stop(owner: Long = monitoringOwner.get()) = mutex.withLock {
        if (owner != monitoringOwner.get()) return@withLock
        val cycle = active
        if (cycle != null) saveCheckpoint(cycle) else {
            val oldPending = pending
            try { database.withTransaction { flushPending() }
            } catch (e: Exception) { pending = oldPending; throw e }
        }
        analytics.restore(active)
        runtime.value = runtime.value.copy(running = false)
    }

    fun reportError(message: String, action: Boolean = false) {
        if (action) operationError.value = message else runtime.value = runtime.value.copy(error = message)
    }

    private suspend fun initialize() {
        if (initialized) return
        active = dao.getActiveCycle()
        analytics.restore(active)
        runtime.value = runtime.value.copy(cycle = active)
        initialized = true
    }

    private suspend fun appendInterval(interval: BatteryInterval) {
        val old = pending
        if (old != null && old.cycleId == interval.cycleId && old.screenOn == interval.screenOn &&
            old.charging == interval.charging && old.source == interval.source && interval.endTime >= old.endTime) {
            pending = old.copy(endTime = interval.endTime, endPct = interval.endPct,
                netMah = old.netMah + interval.netMah, measuredMs = old.measuredMs + interval.measuredMs,
                missingMs = old.missingMs + interval.missingMs, deepSleepMs = old.deepSleepMs + interval.deepSleepMs)
        } else { flushPending(); pending = interval }
    }

    private suspend fun flushPending() {
        pending?.let { dao.saveInterval(it) }
        pending = null
    }

    private suspend fun saveCheckpoint(cycle: BatteryCycle) {
        val oldPending = pending
        try { database.withTransaction { flushPending(); dao.saveCycle(cycle) }
        } catch (e: Exception) { pending = oldPending; throw e }
    }
}
