package com.aizeek.phonepulse.service

import android.content.Context
import com.aizeek.phonepulse.data.AppDatabase
import com.aizeek.phonepulse.data.ScreenSession
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object ScreenStateHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    private val _currentDateKey = MutableStateFlow(TimeFormatter.todayKey())
    val currentDateKey: StateFlow<String> = _currentDateKey.asStateFlow()

    fun checkDateRollover() {
        val today = TimeFormatter.todayKey()
        if (_currentDateKey.value != today) {
            _currentDateKey.value = today
        }
    }

    fun refreshDateKey() {
        _currentDateKey.value = TimeFormatter.todayKey()
    }

    fun splitSessionByDay(session: ScreenSession): List<ScreenSession> {
        val startDateKey = TimeFormatter.dateKey(session.startTime)
        val endDateKey = TimeFormatter.dateKey(session.endTime)
        if (startDateKey == endDateKey) {
            return listOf(session)
        }

        val result = mutableListOf<ScreenSession>()
        var curStart = session.startTime
        while (curStart < session.endTime) {
            val curStartOfDay = TimeFormatter.getStartOfDay(curStart)
            val nextDayStart = curStartOfDay + 24 * 3600_000L
            val curEnd = minOf(session.endTime, nextDayStart)
            val dur = (curEnd - curStart).coerceAtLeast(0L)
            result.add(
                ScreenSession(
                    type = session.type,
                    startTime = curStart,
                    endTime = curEnd,
                    durationMs = dur,
                    dateKey = TimeFormatter.dateKey(curStart)
                )
            )
            curStart = nextDayStart
        }
        return result
    }

    /**
     * Called when screen state transitions (e.g. from SCREEN_OFF to SCREEN_ON or vice versa)
     */
    fun onScreenStateChanged(context: Context, newIsScreenOn: Boolean) {
        if (_isScreenOn.value == newIsScreenOn) return

        val now = System.currentTimeMillis()
        val previousStateWasOn = _isScreenOn.value
        val previousStartTime = _stateStartTime.value
        val durationMs = (now - previousStartTime).coerceAtLeast(0L)

        // Save last session
        val previousType = if (previousStateWasOn) "SCREEN_ON" else "SCREEN_OFF"
        if (previousStateWasOn) {
            _lastScreenOnDuration.value = durationMs
        } else {
            _lastScreenOffDuration.value = durationMs
        }

        val rawSession = ScreenSession(
            type = previousType,
            startTime = previousStartTime,
            endTime = now,
            durationMs = durationMs,
            dateKey = TimeFormatter.dateKey(previousStartTime)
        )
        val sessionsToInsert = splitSessionByDay(rawSession)

        // Switch to new state
        _isScreenOn.value = newIsScreenOn
        _stateStartTime.value = now
        refreshDateKey()

        // Persist session to Room in background
        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context).screenSessionDao()
                dao.insertAll(sessionsToInsert)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Initializes state from DB and preferences
     */
    fun initialize(context: Context) {
        refreshDateKey()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenCurrentlyOn = powerManager?.isInteractive ?: true
        _isScreenOn.value = isScreenCurrentlyOn
        _stateStartTime.value = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context).screenSessionDao()
                val lastOff = dao.getLastSessionByTypeSync("SCREEN_OFF")
                val lastOn = dao.getLastSessionByTypeSync("SCREEN_ON")
                if (lastOff != null) {
                    _lastScreenOffDuration.value = lastOff.durationMs
                }
                if (lastOn != null) {
                    _lastScreenOnDuration.value = lastOn.durationMs
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Synchronizes current screen state when service starts or receives system callbacks
     */
    fun syncScreenState(isCurrentlyOn: Boolean) {
        checkDateRollover()
        if (_isScreenOn.value != isCurrentlyOn) {
            _isScreenOn.value = isCurrentlyOn
            _stateStartTime.value = System.currentTimeMillis()
        }
    }

    /**
     * Allows manual simulation of state change for preview/testing
     */
    fun simulateStateToggle(context: Context) {
        onScreenStateChanged(context, !_isScreenOn.value)
    }
}
