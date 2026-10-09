package com.aizeek.phonepulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.AppBatteryUsage
import com.aizeek.phonepulse.data.estimateAppBatteryUsage
import com.aizeek.phonepulse.data.BatteryRepository
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.data.HourlyScreenStat
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.data.ScreenSession
import com.aizeek.phonepulse.data.ScreenStateRepository
import com.aizeek.phonepulse.data.UsagePeriod
import com.aizeek.phonepulse.data.UsageStatsRepository
import com.aizeek.phonepulse.service.ScreenStateHolder
import com.aizeek.phonepulse.service.ScreenTrackerService
import com.aizeek.phonepulse.util.KeepAliveHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import com.aizeek.phonepulse.update.UpdateRepository
import com.aizeek.phonepulse.PhonePulseApp
import com.aizeek.phonepulse.battery.BatteryMonitorSettings
import com.aizeek.phonepulse.battery.BatteryChargeAlarm
import kotlinx.coroutines.CancellationException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val companionDao = com.aizeek.phonepulse.data.AppDatabase.getInstance(application).screenSessionDao()
    private val companionRepository = com.aizeek.phonepulse.companion.CompanionRepository(
        companionDao, com.aizeek.phonepulse.companion.PreferencesCompanionStore(application))
    val companion = companionRepository.state

    // Collected only while the activity is resumed; Room invalidation handles the screen-event write race.
    suspend fun observeCompanion() {
        combine(companionDao.observeLatestSessionId(), ScreenStateHolder.currentDateKey) { id, day -> id to day }
            .collect { companionRepository.refresh() }
    }
    fun refreshCompanion() = viewModelScope.launch { companionRepository.refresh() }
    fun equipCompanion(id: String) = viewModelScope.launch { companionRepository.equip(id) }
    fun buildCompanionHome(stage: com.aizeek.phonepulse.companion.HomeStage) = viewModelScope.launch { companionRepository.build(stage) }
    fun placeCompanionItem(id: String, slot: com.aizeek.phonepulse.companion.HomeSlot) = viewModelScope.launch { companionRepository.place(id, slot) }
    fun removeCompanionFurniture(slot: com.aizeek.phonepulse.companion.HomeSlot) = viewModelScope.launch { companionRepository.removeFurniture(slot) }
    fun readCompanionLetter(id: String) = viewModelScope.launch { companionRepository.readLetter(id) }
    fun frameCompanionLetter(id: String) = viewModelScope.launch { companionRepository.frame(id) }
    fun colorCompanionRoof(color: String) = viewModelScope.launch { companionRepository.roof(color) }
    fun visitCompanion() = viewModelScope.launch { companionRepository.visit() }
    fun acknowledgeCompanion(id: Long, level: Int) = viewModelScope.launch { companionRepository.acknowledge(id, level) }
    fun showcaseCompanion(id: String) = viewModelScope.launch { companionRepository.showcase(id) }
    fun claimCompanionTask(id: String) = viewModelScope.launch { companionRepository.claim(id) }
    fun renameCompanion(name: String) = viewModelScope.launch { companionRepository.rename(name) }
    fun confirmCompanionJourney(id: Long, label: String) = viewModelScope.launch { companionRepository.confirm(id, label) }

    private var companionObservationJob: Job? = null
    fun cancelCompanionObservation() { companionObservationJob?.cancel() }
    fun observeTodayForCompanion(): Job {
        companionObservationJob?.takeIf { it.isActive }?.let { return it }
        return viewModelScope.launch {
            companionRepository.observation(null, loading = true)
            try {
                val now = System.currentTimeMillis()
                val access = usageRepo.hasUsageStatsPermission()
                val sessions = companionDao.getSessionsForDate(com.aizeek.phonepulse.util.TimeFormatter.dateKey(now)).first()
                val apps = if (access) usageRepo.getAppUsageStats(UsagePeriod.TODAY) else emptyList()
                val top = apps
                    .filter { it.packageName != getApplication<Application>().packageName }
                    .maxByOrNull { it.totalTimeInForegroundMs }
                companionRepository.observation(com.aizeek.phonepulse.companion.CompanionInsights.describe(sessions, top, now, access))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                android.util.Log.e("Companion", "Usage observation failed", e)
                companionRepository.observation("今天的应用记录暂时读不到，请稍后再试。")
            } finally {
                companionRepository.observation(companion.value.observation, loading = false)
            }
        }.also { companionObservationJob = it }
    }

    private val screenRepo = ScreenStateRepository(application)
    private val usageRepo = UsageStatsRepository(application)
    private val batteryRepo = BatteryRepository(application)
    private val batteryMonitorRepository = (application as PhonePulseApp).batteryMonitor
    val batteryMonitor = batteryMonitorRepository.state

    fun saveBatterySettings(settings: BatteryMonitorSettings) = viewModelScope.launch {
        try {
            batteryMonitorRepository.saveSettings(settings)
            if (!settings.chargeAlarmEnabled) BatteryChargeAlarm(getApplication()).cancel()
            if (ScreenStateHolder.isServiceRunning.value) ScreenTrackerService.start(getApplication())
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            batteryMonitorRepository.reportError("电池设置保存失败，请重试", action = true)
            android.util.Log.e("BatteryMonitor", "Failed to save settings", e)
        }
    }

    fun excludeBatteryCycle(id: Long, excluded: Boolean) = viewModelScope.launch {
        try { batteryMonitorRepository.setExcluded(id, excluded)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            batteryMonitorRepository.reportError("会话修改失败，请重试", action = true)
            android.util.Log.e("BatteryMonitor", "Failed to exclude cycle", e)
        }
    }

    fun muteBatteryAlarm() = viewModelScope.launch {
        try {
            batteryMonitorRepository.muteAlarm()
            BatteryChargeAlarm(getApplication()).cancel()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            batteryMonitorRepository.reportError("提醒静音失败，请重试", action = true)
            android.util.Log.e("BatteryMonitor", "Failed to mute alarm", e)
        }
    }
    private var usageLoadJob: Job? = null
    val updates = UpdateRepository(application)

    val isScreenOn: StateFlow<Boolean> = ScreenStateHolder.isScreenOn
    val stateStartTimeMs: StateFlow<Long> = ScreenStateHolder.stateStartTime
    val currentDurationMs: StateFlow<Long> = ScreenStateHolder.currentDurationMs
    val isServiceRunning: StateFlow<Boolean> = ScreenStateHolder.isServiceRunning

    val lastScreenOffDurationMs: StateFlow<Long?> = ScreenStateHolder.lastScreenOffDuration
    val lastScreenOnDurationMs: StateFlow<Long?> = ScreenStateHolder.lastScreenOnDuration

    val todayBatteryRecords: StateFlow<List<BatteryRecord>> = batteryRepo.getTodayBatteryRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveBattery: StateFlow<LiveBatteryInfo> = batteryRepo.observeBatteryRecords(todayBatteryRecords)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), batteryRepo.readCurrentBattery())

    val todayTotalScreenOnMs: StateFlow<Long> = screenRepo.getTodayTotalScreenOnMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayTotalScreenOffMs: StateFlow<Long> = screenRepo.getTodayTotalScreenOffMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayWakeCount: StateFlow<Int> = screenRepo.getTodayWakeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allSessions: StateFlow<List<ScreenSession>> = screenRepo.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSessions: StateFlow<List<ScreenSession>> = screenRepo.recentSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hourlyStats: StateFlow<List<HourlyScreenStat>> = screenRepo.getHourlyBreakdownFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _appUsageList = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val appUsageList: StateFlow<List<AppUsageInfo>> = _appUsageList.asStateFlow()

    private val _todayAppUsageList = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val todayAppUsageList = _todayAppUsageList.asStateFlow()
    val appBatteryUsage: StateFlow<List<AppBatteryUsage>> = combine(_todayAppUsageList, todayBatteryRecords) { apps, records ->
        estimateAppBatteryUsage(apps, records)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _isBatteryAppUsageLoading = MutableStateFlow(false)
    val isBatteryAppUsageLoading = _isBatteryAppUsageLoading.asStateFlow()

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
        ScreenStateHolder.refreshDateKey()
    }

    fun setUsagePeriod(period: UsagePeriod) {
        _currentPeriod.value = period
        loadAppUsageStats()
    }

    suspend fun loadAppUsageTimeline(packageName: String) = usageRepo.getTodayTimeline(packageName)

    fun loadAppUsageStats(forBattery: Boolean = false): Job {
        usageLoadJob?.cancel()
        val period = if (forBattery) UsagePeriod.TODAY else _currentPeriod.value
        val loading = if (forBattery) _isBatteryAppUsageLoading else _isAppUsageLoading
        _isAppUsageLoading.value = false
        _isBatteryAppUsageLoading.value = false
        val job = viewModelScope.launch {
            loading.value = true
            try {
                _hasUsagePermission.value = usageRepo.hasUsageStatsPermission()
                val list = usageRepo.getAppUsageStats(period)
                if (period == UsagePeriod.TODAY) _todayAppUsageList.value = list
                if (!forBattery) _appUsageList.value = list
            } finally {
                if (currentCoroutineContext().isActive) loading.value = false
            }
        }
        usageLoadJob = job
        return job
    }

    fun cancelAppUsageLoad(job: Job) {
        job.cancel()
        if (usageLoadJob === job) {
            _isAppUsageLoading.value = false
            _isBatteryAppUsageLoading.value = false
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
}
