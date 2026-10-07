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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aizeek.phonepulse.ui.screens.AppUsageScreen
import com.aizeek.phonepulse.ui.screens.HistoryTimelineScreen
import com.aizeek.phonepulse.ui.screens.KeepAliveScreen
import com.aizeek.phonepulse.ui.screens.OverviewScreen
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.ElectricViolet
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.NeonEmerald
import com.aizeek.phonepulse.ui.theme.SurfaceDark
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.ui.theme.TextTertiary
import com.aizeek.phonepulse.util.KeepAliveHelper

enum class ScreenTab(val title: String, val icon: ImageVector) {
    OVERVIEW("实时概览", Icons.Default.Dashboard),
    APP_USAGE("应用统计", Icons.Default.Apps),
    HISTORY("状态明细", Icons.Default.History),
    KEEP_ALIVE("保活守护", Icons.Default.Security)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(ScreenTab.OVERVIEW) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == LifecycleEventObserver { _, _ -> }.run { Lifecycle.Event.ON_RESUME }) {
                viewModel.refreshPermissions()
                viewModel.loadAppUsageStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // State flows
    val isScreenOn by viewModel.isScreenOn.collectAsState()
    val stateStartTimeMs by viewModel.stateStartTimeMs.collectAsState()
    val currentDurationMs by viewModel.currentDurationMs.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    val lastScreenOffDurationMs by viewModel.lastScreenOffDurationMs.collectAsState()
    val lastScreenOnDurationMs by viewModel.lastScreenOnDurationMs.collectAsState()
    val liveBattery by viewModel.liveBattery.collectAsState()
    val todayTotalScreenOnMs by viewModel.todayTotalScreenOnMs.collectAsState()
    val todayTotalScreenOffMs by viewModel.todayTotalScreenOffMs.collectAsState()
    val todayWakeCount by viewModel.todayWakeCount.collectAsState()

    val hourlyStats by viewModel.hourlyStats.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()

    val appUsageList by viewModel.appUsageList.collectAsState()
    val isAppUsageLoading by viewModel.isAppUsageLoading.collectAsState()
    val currentPeriod by viewModel.currentPeriod.collectAsState()

    val hasUsagePermission by viewModel.hasUsagePermission.collectAsState()
    val isBatteryIgnoring by viewModel.isBatteryIgnoring.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()

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
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
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
                        OverviewScreen(
                            isScreenOn = isScreenOn,
                            stateStartTimeMs = stateStartTimeMs,
                            isServiceRunning = isServiceRunning,
                            lastScreenOffDurationMs = lastScreenOffDurationMs,
                            lastScreenOnDurationMs = lastScreenOnDurationMs,
                            todayTotalScreenOnMs = todayTotalScreenOnMs,
                            todayTotalScreenOffMs = todayTotalScreenOffMs,
                            todayWakeCount = todayWakeCount,
                            batteryInfo = liveBattery,
                            hourlyStats = hourlyStats,
                            recentSessions = allSessions,
                            onNavigateToHistory = { currentTab = ScreenTab.HISTORY }
                        )
                    }
                    ScreenTab.APP_USAGE -> {
                        AppUsageScreen(
                            hasPermission = hasUsagePermission,
                            isLoading = isAppUsageLoading,
                            usageList = appUsageList,
                            currentPeriod = currentPeriod,
                            onPeriodSelected = { viewModel.setUsagePeriod(it) },
                            onRequestPermission = { KeepAliveHelper.openUsageAccessSettings(context) },
                            onRefresh = { viewModel.loadAppUsageStats() }
                        )
                    }
                    ScreenTab.HISTORY -> {
                        HistoryTimelineScreen(
                            sessions = allSessions,
                            onClearHistory = { viewModel.clearAllHistory() }
                        )
                    }
                    ScreenTab.KEEP_ALIVE -> {
                        KeepAliveScreen(
                            isServiceRunning = isServiceRunning,
                            isBatteryIgnoring = isBatteryIgnoring,
                            hasUsagePermission = hasUsagePermission,
                            hasNotificationPermission = hasNotificationPermission,
                            onToggleService = { viewModel.toggleService(it) },
                            onRequestBatteryOptimization = { KeepAliveHelper.requestIgnoreBatteryOptimizations(context) },
                            onRequestUsagePermission = { KeepAliveHelper.openUsageAccessSettings(context) },
                            onRequestNotificationPermission = { KeepAliveHelper.openAppNotificationSettings(context) },
                            onInjectSampleData = { viewModel.injectSampleData() }
                        )
                    }
                }
            }
        }
    }
}
