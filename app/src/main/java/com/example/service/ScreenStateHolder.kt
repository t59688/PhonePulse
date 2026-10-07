package com.example.service

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.ScreenSession
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ScreenStateHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    private val _stateStartTime = MutableStateFlow(System.currentTimeMillis())
    val stateStartTime: StateFlow<Long> = _stateStartTime.asStateFlow()

    private val _currentDurationMs = MutableStateFlow(0L)
    val currentDurationMs: StateFlow<Long> = _currentDurationMs.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _lastScreenOffDuration = MutableStateFlow<Long?>(null)
    val lastScreenOffDuration: StateFlow<Long?> = _lastScreenOffDuration.asStateFlow()

    private val _lastScreenOnDuration = MutableStateFlow<Long?>(null)
    val lastScreenOnDuration: StateFlow<Long?> = _lastScreenOnDuration.asStateFlow()

    private var tickerRunning = false

    init {
        startTicker()
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val diff = (now - _stateStartTime.value).coerceAtLeast(0L)
                _currentDurationMs.value = diff
                delay(1000)
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
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

        val session = ScreenSession(
            type = previousType,
            startTime = previousStartTime,
            endTime = now,
            durationMs = durationMs,
            dateKey = TimeFormatter.dateKey(previousStartTime)
        )

        // Switch to new state
        _isScreenOn.value = newIsScreenOn
        _stateStartTime.value = now
        _currentDurationMs.value = 0L

        // Persist session to Room in background
        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(context).screenSessionDao()
                dao.insertSession(session)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Initializes state from DB and preferences
     */
    fun initialize(context: Context) {
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
     * Allows manual simulation of state change for preview/testing
     */
    fun simulateStateToggle(context: Context) {
        onScreenStateChanged(context, !_isScreenOn.value)
    }
}
