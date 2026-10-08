package com.aizeek.phonepulse.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aizeek.phonepulse.ui.screens.AppUsageScreen
import com.aizeek.phonepulse.ui.screens.BatteryScreen
import com.aizeek.phonepulse.ui.screens.HistoryTimelineScreen
import com.aizeek.phonepulse.ui.screens.OverviewScreen
import com.aizeek.phonepulse.ui.screens.SettingsScreen
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.CoralRose
import com.aizeek.phonepulse.ui.theme.ElectricViolet
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.NeonEmerald
import com.aizeek.phonepulse.ui.theme.SurfaceDark
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.ui.theme.TextTertiary
import com.aizeek.phonepulse.util.KeepAliveHelper
import com.aizeek.phonepulse.ui.components.UpdateHost
import androidx.compose.runtime.saveable.rememberSaveable

enum class ScreenTab(val title: String, val icon: ImageVector) {
    OVERVIEW("实时概览", Icons.Default.Dashboard),
    APP_USAGE("应用活跃", Icons.Default.Apps),
    BATTERY("电量统计", Icons.Default.BatteryFull),
    HISTORY("状态明细", Icons.Default.History),
    SETTINGS("系统设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    updateOpenRequest: Int = 0
) {
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    UpdateHost(viewModel.updates, showUpdates, onClose = { showUpdates = false }, openRequest = updateOpenRequest)
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(ScreenTab.OVERVIEW) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentTab, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (currentTab == ScreenTab.APP_USAGE || currentTab == ScreenTab.BATTERY) {
                val loadJob = viewModel.loadAppUsageStats(forBattery = currentTab == ScreenTab.BATTERY)
                try {
                    awaitCancellation()
                } finally {
                    viewModel.cancelAppUsageLoad(loadJob)
                }
            } else {
                awaitCancellation()
            }
        }
    }

    // State flows
    val isScreenOn by viewModel.isScreenOn.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val stateStartTimeMs by viewModel.stateStartTimeMs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    val hasUsagePermission by viewModel.hasUsagePermission.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val isBatteryIgnoring by viewModel.isBatteryIgnoring.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val updateState by viewModel.updates.state.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isScreenOn) NeonCyan else ElectricViolet)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PhonePulse",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isScreenOn) "· 亮屏中" else "· 熄屏中",
                            color = if (isScreenOn) NeonCyan else ElectricViolet,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                ),
                modifier = Modifier.border(0.dp, Color.Transparent)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                modifier = Modifier
                    .border(1.dp, BorderDark, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .testTag("main_navigation_bar")
            ) {
                ScreenTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Box {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (tab == ScreenTab.SETTINGS && updateState.hasUpdate) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(7.dp)
                                            .background(CoralRose, CircleShape)
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f),
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary
                        ),
                        modifier = Modifier.testTag("tab_${tab.name}")
                    )
                }
            }
        },
        containerColor = SurfaceDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    ScreenTab.OVERVIEW -> {
                        val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val hourlyStats by viewModel.hourlyStats.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val todayWakeCount by viewModel.todayWakeCount.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val todayTotalScreenOffMs by viewModel.todayTotalScreenOffMs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val todayTotalScreenOnMs by viewModel.todayTotalScreenOnMs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val lastScreenOnDurationMs by viewModel.lastScreenOnDurationMs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val lastScreenOffDurationMs by viewModel.lastScreenOffDurationMs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        OverviewScreen(
                            isScreenOn = isScreenOn,
                            stateStartTimeMs = stateStartTimeMs,
                            isServiceRunning = isServiceRunning,
                            lastScreenOffDurationMs = lastScreenOffDurationMs,
                            lastScreenOnDurationMs = lastScreenOnDurationMs,
                            todayTotalScreenOnMs = todayTotalScreenOnMs,
                            todayTotalScreenOffMs = todayTotalScreenOffMs,
                            todayWakeCount = todayWakeCount,
                            hourlyStats = hourlyStats,
                            recentSessions = recentSessions,
                            onNavigateToHistory = { currentTab = ScreenTab.HISTORY }
                        )
                    }
                    ScreenTab.APP_USAGE -> {
                        val currentPeriod by viewModel.currentPeriod.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val isAppUsageLoading by viewModel.isAppUsageLoading.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val appUsageList by viewModel.appUsageList.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val appBatteryUsage by viewModel.appBatteryUsage.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        AppUsageScreen(
                            hasPermission = hasUsagePermission,
                            isLoading = isAppUsageLoading,
                            usageList = appUsageList,
                            currentPeriod = currentPeriod,
                            onPeriodSelected = { viewModel.setUsagePeriod(it) },
                            onRequestPermission = { KeepAliveHelper.openUsageAccessSettings(context) },
                            onRefresh = { viewModel.loadAppUsageStats() },
                            batteryUsage = appBatteryUsage,
                            onOpenApp = { if (currentPeriod != com.aizeek.phonepulse.data.UsagePeriod.TODAY) viewModel.loadAppUsageStats(forBattery = true) },
                            loadTimeline = viewModel::loadAppUsageTimeline
                        )
                    }
                    ScreenTab.BATTERY -> {
                        val batteryMonitor by viewModel.batteryMonitor.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val todayBatteryRecords by viewModel.todayBatteryRecords.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val liveBattery by viewModel.liveBattery.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val todayAppUsageList by viewModel.todayAppUsageList.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        val isBatteryAppUsageLoading by viewModel.isBatteryAppUsageLoading.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        BatteryScreen(batteryInfo = liveBattery, records = todayBatteryRecords,
                            usageList = todayAppUsageList, hasUsagePermission = hasUsagePermission,
                            isLoading = isBatteryAppUsageLoading,
                            onRequestPermission = { KeepAliveHelper.openUsageAccessSettings(context) },
                            onRefresh = { viewModel.loadAppUsageStats(forBattery = true) },
                            loadTimeline = viewModel::loadAppUsageTimeline,
                            monitorState = batteryMonitor,
                            onSaveBatterySettings = { viewModel.saveBatterySettings(it) },
                            onExcludeBatteryCycle = { id, excluded -> viewModel.excludeBatteryCycle(id, excluded) },
                            onMuteBatteryAlarm = { viewModel.muteBatteryAlarm() })
                    }
                    ScreenTab.HISTORY -> {
                        val allSessions by viewModel.allSessions.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
                        HistoryTimelineScreen(
                            sessions = allSessions,
                            onClearHistory = { viewModel.clearAllHistory() }
                        )
                    }
                    ScreenTab.SETTINGS -> {
                        SettingsScreen(
                            updateRepository = viewModel.updates,
                            onCheckUpdates = { showUpdates = true },
                            isServiceRunning = isServiceRunning,
                            isBatteryIgnoring = isBatteryIgnoring,
                            hasUsagePermission = hasUsagePermission,
                            hasNotificationPermission = hasNotificationPermission,
                            onToggleService = { viewModel.toggleService(it) },
                            onRequestBatteryOptimization = { KeepAliveHelper.requestIgnoreBatteryOptimizations(context) },
                            onRequestUsagePermission = { KeepAliveHelper.openUsageAccessSettings(context) },
                            onRequestNotificationPermission = { KeepAliveHelper.openAppNotificationSettings(context) },
                            onRequestAutoStart = { KeepAliveHelper.openAutoStartSettings(context) }
                        )
                    }
                }
            }
        }
    }
}
