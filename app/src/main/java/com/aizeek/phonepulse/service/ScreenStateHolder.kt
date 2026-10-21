package com.aizeek.phonepulse.service

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.aizeek.phonepulse.data.AppDatabase
import com.aizeek.phonepulse.data.ScreenSession
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object ScreenStateHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val PREFS = "screen_tracking_checkpoint"
    private const val KEY_SCREEN_ON = "screen_on"
    private const val KEY_START_MS = "started_at_ms"
    private const val KEY_LAST_OFF_DURATION = "last_off_duration_ms"
    private const val KEY_LAST_ON_DURATION = "last_on_duration_ms"
    private const val KEY_LAST_OFF_END = "last_off_end_ms"
    private const val KEY_LAST_ON_END = "last_on_end_ms"
    private var lastOffEndMs = 0L
    private var lastOnEndMs = 0L

    private data class TrackingState(val screenOn: Boolean, val startMs: Long)
    private sealed interface Command {
        val context: Context
        data class Transition(override val context: Context, val boundary: ScreenBoundary) : Command
        data class Recover(override val context: Context) : Command
        data class Clear(override val context: Context, val result: CompletableDeferred<Unit>) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var trackingState: TrackingState? = null

    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    private val _stateStartTime = MutableStateFlow(System.currentTimeMillis())
    val stateStartTime: StateFlow<Long> = _stateStartTime.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentDurationMs: StateFlow<Long> = stateStartTime.flatMapLatest { startTime ->
        flow {
            while (true) {
                emit((System.currentTimeMillis() - startTime).coerceAtLeast(0L))
                delay(1000)
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), 0L)

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _lastScreenOffDuration = MutableStateFlow<Long?>(null)
    val lastScreenOffDuration: StateFlow<Long?> = _lastScreenOffDuration.asStateFlow()

    private val _lastScreenOnDuration = MutableStateFlow<Long?>(null)
    val lastScreenOnDuration: StateFlow<Long?> = _lastScreenOnDuration.asStateFlow()

    private val _currentDateKey = MutableStateFlow(TimeFormatter.todayKey())
    val currentDateKey: StateFlow<String> = _currentDateKey.asStateFlow()

    init {
        // Database writes, history recovery and live broadcasts must be ordered together.
        scope.launch {
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Transition -> processTransition(command.context, command.boundary)
                        is Command.Recover -> recover(command.context)
                        is Command.Clear -> {
                            clearHistoryInternal(command.context)
                            command.result.complete(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ScreenStateHolder", "Failed to persist screen state", e)
                    if (command is Command.Clear) command.result.completeExceptionally(e)
                }
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun checkDateRollover() {
        val today = TimeFormatter.todayKey()
        if (_currentDateKey.value != today) _currentDateKey.value = today
    }

    fun refreshDateKey() {
        _currentDateKey.value = TimeFormatter.todayKey()
    }

    fun splitSessionByDay(session: ScreenSession): List<ScreenSession> {
        val startDateKey = TimeFormatter.dateKey(session.startTime)
        val endDateKey = TimeFormatter.dateKey(session.endTime)
        if (startDateKey == endDateKey) return listOf(session)

        val result = mutableListOf<ScreenSession>()
        var curStart = session.startTime
        while (curStart < session.endTime) {
            val nextDayStart = TimeFormatter.getStartOfDay(curStart) + 24 * 3600_000L
            val curEnd = minOf(session.endTime, nextDayStart)
            result.add(
                ScreenSession(
                    type = session.type,
                    startTime = curStart,
                    endTime = curEnd,
                    durationMs = (curEnd - curStart).coerceAtLeast(0L),
                    dateKey = TimeFormatter.dateKey(curStart)
                )
            )
            curStart = nextDayStart
        }
        return result
    }

    @Synchronized
    fun initialize(context: Context) {
        if (trackingState != null) return
        val now = System.currentTimeMillis()
        val currentPowerState = readPowerState(context, true)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastOffEndMs = prefs.getLong(KEY_LAST_OFF_END, 0L).takeIf { it in 1..now } ?: 0L
        lastOnEndMs = prefs.getLong(KEY_LAST_ON_END, 0L).takeIf { it in 1..now } ?: 0L
        _lastScreenOffDuration.value = prefs.getLong(KEY_LAST_OFF_DURATION, -1L)
            .takeIf { it >= 0L && lastOffEndMs > 0L }
        _lastScreenOnDuration.value = prefs.getLong(KEY_LAST_ON_DURATION, -1L)
            .takeIf { it >= 0L && lastOnEndMs > 0L }
        val savedStart = prefs.getLong(KEY_START_MS, 0L)
        val validSavedStart = savedStart in 1..now
        val state = if (validSavedStart) {
            TrackingState(prefs.getBoolean(KEY_SCREEN_ON, currentPowerState), savedStart)
        } else {
            TrackingState(currentPowerState, now)
        }
        trackingState = state
        _isScreenOn.value = state.screenOn
        _stateStartTime.value = state.startMs
        refreshDateKey()
        if (!validSavedStart) persistCheckpoint(context, state)
    }

    /** Enqueue system broadcasts in receipt order; do not block the broadcast thread on Room. */
    fun onScreenStateChanged(context: Context, newIsScreenOn: Boolean) {
        commands.trySend(Command.Transition(
            context.applicationContext,
            ScreenBoundary(newIsScreenOn, System.currentTimeMillis())
        ))
    }

    /** Reconcile both missing broadcasts and transitions recorded while the process was dead. */
    fun requestRecovery(context: Context) {
        commands.trySend(Command.Recover(context.applicationContext))
    }

    suspend fun clearHistory(context: Context) {
        val completed = CompletableDeferred<Unit>()
        commands.send(Command.Clear(context.applicationContext, completed))
        completed.await()
    }

    private suspend fun processTransition(context: Context, boundary: ScreenBoundary) {
        val current = trackingState ?: return
        val replay = ScreenTimeline.replay(
            current.screenOn, current.startMs, listOf(boundary), System.currentTimeMillis()
        )
        applyReplay(context, replay)
    }

    private suspend fun recover(context: Context) {
        var current = trackingState ?: return
        val dao = AppDatabase.getInstance(context).screenSessionDao()
        val lastCompleted = dao.getLastCompletedSessionSync()
        val queryTime = System.currentTimeMillis()

        // A prior process may have committed Room but died before committing preferences.
        if (lastCompleted != null && lastCompleted.endTime > current.startMs &&
            lastCompleted.endTime <= queryTime
        ) {
            current = TrackingState(!lastCompleted.isScreenOn, lastCompleted.endTime)
        }

        // A session split at midnight has multiple Room rows, but one full duration.
        // Keep the cached full duration when Room's last fragment ends at the same time.
        val lastOff = dao.getLastSessionByTypeSync("SCREEN_OFF")
        if (lastOff != null && lastOff.endTime > lastOffEndMs) {
            _lastScreenOffDuration.value = lastOff.durationMs
            lastOffEndMs = lastOff.endTime
        }
        val lastOn = dao.getLastSessionByTypeSync("SCREEN_ON")
        if (lastOn != null && lastOn.endTime > lastOnEndMs) {
            _lastScreenOnDuration.value = lastOn.durationMs
            lastOnEndMs = lastOn.endTime
        }

        val history = ScreenHistorySource.read(context, current.startMs, queryTime)
        var boundaries = history.orEmpty()

        if (queryTime - current.startMs > ScreenHistorySource.MAX_LOOKBACK_MS) {
            // We cannot assign the uncovered days to either state without evidence.
            // Start from the first available transition, not the stale checkpoint.
            if (boundaries.isNotEmpty()) {
                val first = boundaries.first()
                current = TrackingState(first.screenOn, first.timestampMs)
                boundaries = boundaries.drop(1)
            } else {
                current = TrackingState(readPowerState(context, current.screenOn), queryTime)
            }
        }

        var replay = ScreenTimeline.replay(current.screenOn, current.startMs, boundaries, queryTime)
        val actualState = readPowerState(context, replay.screenOn)
        if (replay.screenOn != actualState) {
            val observedAt = System.currentTimeMillis()
            replay = if (boundaries.isEmpty() && observedAt - replay.startMs > 2 * 60_000L) {
                // A long unobserved gap with no events has an unknown transition time.
                // Do not manufacture an hours-long session from that gap.
                replay.copy(screenOn = actualState, startMs = observedAt)
            } else {
                ScreenTimeline.replay(
                    replay.screenOn, replay.startMs,
                    listOf(ScreenBoundary(actualState, observedAt)), observedAt
                ).let { updated ->
                    updated.copy(completed = replay.completed + updated.completed)
                }
            }
        }
        applyReplay(context, replay)
        // Also checkpoint durations restored from Room when there was no new transition.
        trackingState?.let { persistCheckpoint(context, it) }
    }

    private suspend fun applyReplay(context: Context, replay: ScreenReplay) {
        val current = trackingState ?: return
        if (replay.screenOn == current.screenOn && replay.startMs == current.startMs &&
            replay.completed.isEmpty()
        ) return

        val sessions = replay.completed.flatMap { interval ->
            splitSessionByDay(ScreenSession(
                type = if (interval.screenOn) "SCREEN_ON" else "SCREEN_OFF",
                startTime = interval.startMs,
                endTime = interval.endMs,
                durationMs = interval.endMs - interval.startMs,
                dateKey = TimeFormatter.dateKey(interval.startMs)
            ))
        }
        if (sessions.isNotEmpty()) {
            AppDatabase.getInstance(context).screenSessionDao().insertAll(sessions)
        }
        for (interval in replay.completed) {
            val duration = interval.endMs - interval.startMs
            if (interval.screenOn) {
                _lastScreenOnDuration.value = duration
                lastOnEndMs = interval.endMs
            } else {
                _lastScreenOffDuration.value = duration
                lastOffEndMs = interval.endMs
            }
        }
        val updated = TrackingState(replay.screenOn, replay.startMs)
        trackingState = updated
        _isScreenOn.value = updated.screenOn
        _stateStartTime.value = updated.startMs
        refreshDateKey()
        persistCheckpoint(context, updated)
    }

    private suspend fun clearHistoryInternal(context: Context) {
        AppDatabase.getInstance(context).screenSessionDao().clearAll()
        val newState = TrackingState(readPowerState(context, _isScreenOn.value), System.currentTimeMillis())
        trackingState = newState
        _isScreenOn.value = newState.screenOn
        _stateStartTime.value = newState.startMs
        _lastScreenOffDuration.value = null
        _lastScreenOnDuration.value = null
        lastOffEndMs = 0L
        lastOnEndMs = 0L
        refreshDateKey()
        persistCheckpoint(context, newState)
    }

    private fun persistCheckpoint(context: Context, state: TrackingState) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SCREEN_ON, state.screenOn)
            .putLong(KEY_START_MS, state.startMs)
            .putLong(KEY_LAST_OFF_DURATION, _lastScreenOffDuration.value ?: -1L)
            .putLong(KEY_LAST_ON_DURATION, _lastScreenOnDuration.value ?: -1L)
            .putLong(KEY_LAST_OFF_END, lastOffEndMs)
            .putLong(KEY_LAST_ON_END, lastOnEndMs)
            .commit()
        if (!saved) Log.w("ScreenStateHolder", "Screen state checkpoint could not be saved")
    }

    private fun readPowerState(context: Context, fallback: Boolean): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: fallback

    fun simulateStateToggle(context: Context) {
        onScreenStateChanged(context, !_isScreenOn.value)
    }
}
