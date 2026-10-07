package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppUsageInfo
import com.example.data.BatteryRepository
import com.example.data.HourlyScreenStat
import com.example.data.LiveBatteryInfo
import com.example.data.ScreenSession
import com.example.data.ScreenStateRepository
import com.example.data.UsagePeriod
import com.example.data.UsageStatsRepository
import com.example.service.ScreenStateHolder
import com.example.service.ScreenTrackerService
import com.example.util.KeepAliveHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val screenRepo = ScreenStateRepository(application)
    private val usageRepo = UsageStatsRepository(application)
    private val batteryRepo = BatteryRepository(application)

    val isScreenOn: StateFlow<Boolean> = ScreenStateHolder.isScreenOn
    val stateStartTimeMs: StateFlow<Long> = ScreenStateHolder.stateStartTime
    val currentDurationMs: StateFlow<Long> = ScreenStateHolder.currentDurationMs
    val isServiceRunning: StateFlow<Boolean> = ScreenStateHolder.isServiceRunning

    val lastScreenOffDurationMs: StateFlow<Long?> = ScreenStateHolder.lastScreenOffDuration
    val lastScreenOnDurationMs: StateFlow<Long?> = ScreenStateHolder.lastScreenOnDuration

    val liveBattery: StateFlow<LiveBatteryInfo> = batteryRepo.liveBattery

    val todayTotalScreenOnMs: StateFlow<Long> = screenRepo.getTodayTotalScreenOnMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayTotalScreenOffMs: StateFlow<Long> = screenRepo.getTodayTotalScreenOffMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayWakeCount: StateFlow<Int> = screenRepo.getTodayWakeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allSessions: StateFlow<List<ScreenSession>> = screenRepo.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _hourlyStats = MutableStateFlow<List<HourlyScreenStat>>(emptyList())
    val hourlyStats: StateFlow<List<HourlyScreenStat>> = _hourlyStats.asStateFlow()

    private val _appUsageList = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val appUsageList: StateFlow<List<AppUsageInfo>> = _appUsageList.asStateFlow()

    private val _isAppUsageLoading = MutableStateFlow(false)
    val isAppUsageLoading: StateFlow<Boolean> = _isAppUsageLoading.asStateFlow()

    private val _currentPeriod = MutableStateFlow(UsagePeriod.TODAY)
    val currentPeriod: StateFlow<UsagePeriod> = _currentPeriod.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(false)
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    private val _isBatteryIgnoring = MutableStateFlow(false)
    val isBatteryIgnoring: StateFlow<Boolean> = _isBatteryIgnoring.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(true)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    init {
        refreshPermissions()
        refreshHourlyStats()
        loadAppUsageStats()
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        _hasUsagePermission.value = usageRepo.hasUsageStatsPermission()
        _isBatteryIgnoring.value = KeepAliveHelper.isIgnoringBatteryOptimizations(app)
        _hasNotificationPermission.value = KeepAliveHelper.hasNotificationPermission(app)
        viewModelScope.launch {
            batteryRepo.recordBatterySnapshot()
        }
    }

    fun refreshHourlyStats() {
        viewModelScope.launch {
            val stats = screenRepo.getHourlyBreakdown()
            _hourlyStats.value = stats
        }
    }

    fun setUsagePeriod(period: UsagePeriod) {
        _currentPeriod.value = period
        loadAppUsageStats()
    }

    fun loadAppUsageStats() {
        viewModelScope.launch {
            _isAppUsageLoading.value = true
            _hasUsagePermission.value = usageRepo.hasUsageStatsPermission()
            val drainPct = liveBattery.value.todayTotalDrainPct.toFloat().coerceAtLeast(12f)
            val list = usageRepo.getAppUsageStats(_currentPeriod.value, todayScreenOnDrainPct = drainPct)
            _appUsageList.value = list
            _isAppUsageLoading.value = false
        }
    }

    fun toggleService(enable: Boolean) {
        val app = getApplication<Application>()
        if (enable) {
            ScreenTrackerService.start(app)
        } else {
            ScreenTrackerService.stop(app)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            screenRepo.clearHistory()
            refreshHourlyStats()
        }
    }

    fun injectSampleData() {
        viewModelScope.launch {
            screenRepo.injectSampleDemoData()
            refreshHourlyStats()
        }
    }
}
