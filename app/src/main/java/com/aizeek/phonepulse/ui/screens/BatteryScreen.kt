package com.aizeek.phonepulse.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.battery.BatteryMonitorSettings
import com.aizeek.phonepulse.battery.BatteryMonitorUiState
import com.aizeek.phonepulse.data.AppBatteryUsage
import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.AppUsageTimeline
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.data.UsagePeriod
import com.aizeek.phonepulse.data.estimateAppBatteryUsage
import com.aizeek.phonepulse.ui.components.BatteryHealthCard
import com.aizeek.phonepulse.ui.components.BatteryLevelChart
import com.aizeek.phonepulse.ui.components.BatteryMaintenanceCard
import com.aizeek.phonepulse.ui.components.BatteryMeasurementCard
import com.aizeek.phonepulse.ui.components.BatterySessionsCard
import com.aizeek.phonepulse.ui.components.BatteryStatsCard
import com.aizeek.phonepulse.ui.theme.AmberWarning
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.ElectricViolet
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.NeonEmerald
import com.aizeek.phonepulse.ui.theme.SurfaceDark
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.ui.theme.TextTertiary
import com.aizeek.phonepulse.util.TimeFormatter

enum class BatterySubTab(val title: String, val icon: ImageVector) {
    MONITOR("实时耗电", Icons.Default.Bolt),
    HEALTH("电池健康", Icons.Default.Favorite),
    CARE("充电提醒", Icons.Default.Security),
    SESSIONS("充放记录", Icons.Default.History)
}

@Composable
fun BatteryScreen(
    batteryInfo: LiveBatteryInfo,
    records: List<BatteryRecord>,
    modifier: Modifier = Modifier,
    usageList: List<AppUsageInfo> = emptyList(),
    hasUsagePermission: Boolean = false,
    isLoading: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onRefresh: () -> Unit = {},
    loadTimeline: suspend (String) -> AppUsageTimeline? = { null },
    monitorState: BatteryMonitorUiState = BatteryMonitorUiState(),
    onSaveBatterySettings: (BatteryMonitorSettings) -> Unit = {},
    onExcludeBatteryCycle: (Long, Boolean) -> Unit = { _, _ -> },
    onMuteBatteryAlarm: () -> Unit = {},
    initialTab: BatterySubTab = BatterySubTab.MONITOR
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val estimates = remember(usageList, records) {
        estimateAppBatteryUsage(usageList, records)
            .sortedByDescending { it.app.totalTimeInForegroundMs }
    }
    val selected = estimates.firstOrNull { it.app.packageName == selectedPackage }
    if (selected != null) {
        AppDetailScreen(
            app = selected.app,
            period = UsagePeriod.TODAY,
            batteryUsage = selected,
            onBack = { selectedPackage = null },
            loadTimeline = loadTimeline
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Sticky Header with Title and Real-time Status Chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonCyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (batteryInfo.isCharging) NeonEmerald else NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "电量统计",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (batteryInfo.isCharging) NeonEmerald.copy(alpha = 0.12f)
                        else NeonCyan.copy(alpha = 0.10f)
                    )
                    .border(
                        1.dp,
                        if (batteryInfo.isCharging) NeonEmerald.copy(alpha = 0.35f)
                        else NeonCyan.copy(alpha = 0.25f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (batteryInfo.percentage >= 0) {
                        "${batteryInfo.percentage}% · ${batteryInfo.plugType}"
                    } else "等待采样",
                    color = if (batteryInfo.isCharging) NeonEmerald else NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Sub-Tab Segmented Pill Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevatedDark)
                .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BatterySubTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val activeColor = when (tab) {
                    BatterySubTab.MONITOR -> NeonCyan
                    BatterySubTab.HEALTH -> NeonEmerald
                    BatterySubTab.CARE -> AmberWarning
                    BatterySubTab.SESSIONS -> ElectricViolet
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) activeColor.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(11.dp)
                        )
                        .clickable { selectedTab = tab }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) activeColor else TextSecondary
                        )
                        Text(
                            text = tab.title,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Animated Tab Content Viewport
        Crossfade(
            targetState = selectedTab,
            label = "battery_tab_crossfade",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                BatterySubTab.MONITOR -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("battery_statistics_list"),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                    ) {
                        item {
                            QuickJumpStrip(
                                healthPct = monitorState.health.healthPct,
                                chargeTargetPct = monitorState.settings.chargeTargetPct,
                                isAlarmEnabled = monitorState.settings.chargeAlarmEnabled,
                                cycleCount = monitorState.cycles.size,
                                onSelectTab = { selectedTab = it }
                            )
                        }
                        item { BatteryLevelChart(records = records) }
                        item(key = "battery_measurement") { BatteryMeasurementCard(monitorState) }
                        item { BatteryStatsCard(batteryInfo = batteryInfo) }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("应用使用 · 今日", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = onRefresh, enabled = !isLoading) { Text("刷新", color = NeonCyan) }
                            }
                        }
                        when {
                            !hasUsagePermission -> item {
                                Text("需要应用使用情况访问权限才能显示各应用统计。", color = TextSecondary, fontSize = 13.sp)
                                TextButton(onClick = onRequestPermission) { Text("前往授权", color = NeonCyan) }
                            }
                            isLoading -> item { CircularProgressIndicator(Modifier.padding(16.dp).size(28.dp), color = NeonCyan) }
                            estimates.isEmpty() -> item { Text("今日暂无应用使用记录", color = TextSecondary, fontSize = 13.sp) }
                            else -> items(estimates, key = { it.app.packageName }) { estimate ->
                                AppBatteryItemCard(estimate, onClick = { selectedPackage = estimate.app.packageName })
                            }
                        }
                    }
                }
                BatterySubTab.HEALTH -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                    ) {
                        item(key = "battery_health") {
                            BatteryHealthCard(monitorState)
                        }
                    }
                }
                BatterySubTab.CARE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                    ) {
                        item(key = "battery_maintenance") {
                            BatteryMaintenanceCard(monitorState, onSaveBatterySettings, onMuteBatteryAlarm)
                        }
                    }
                }
                BatterySubTab.SESSIONS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                    ) {
                        item(key = "battery_sessions") {
                            BatterySessionsCard(monitorState.cycles, onExcludeBatteryCycle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickJumpStrip(
    healthPct: Double?,
    chargeTargetPct: Int,
    isAlarmEnabled: Boolean,
    cycleCount: Int,
    onSelectTab: (BatterySubTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickJumpCard(
            modifier = Modifier.weight(1f),
            title = "健康度",
            value = healthPct?.let { "${it.toInt()}%" } ?: "待标定",
            badge = if (healthPct != null) "容量推算" else "需采样",
            accentColor = NeonEmerald,
            icon = Icons.Default.Favorite,
            onClick = { onSelectTab(BatterySubTab.HEALTH) }
        )
        QuickJumpCard(
            modifier = Modifier.weight(1f),
            title = "充电提醒",
            value = "${chargeTargetPct}%",
            badge = if (isAlarmEnabled) "已设提醒" else "未设提醒",
            accentColor = AmberWarning,
            icon = Icons.Default.Security,
            onClick = { onSelectTab(BatterySubTab.CARE) }
        )
        QuickJumpCard(
            modifier = Modifier.weight(1f),
            title = "充放记录",
            value = "${cycleCount}次",
            badge = "历史周期",
            accentColor = ElectricViolet,
            icon = Icons.Default.History,
            onClick = { onSelectTab(BatterySubTab.SESSIONS) }
        )
    }
}

@Composable
private fun QuickJumpCard(
    title: String,
    value: String,
    badge: String,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevatedDark)
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = badge,
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "›",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AppBatteryItemCard(usage: AppBatteryUsage, onClick: () -> Unit) {
    val app = usage.app
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceElevatedDark)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("app_battery_card_${app.packageName}")
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (app.iconBitmap != null) {
                    Image(app.iconBitmap, null, Modifier.size(42.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Apps, null, Modifier.size(22.dp), tint = NeonCyan)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(app.appName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("前台 ${TimeFormatter.formatDurationCompact(app.totalTimeInForegroundMs)}", color = TextSecondary, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("系统记录", color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("前台时长", color = TextSecondary, fontSize = 11.sp)
                }
            }

        }
    }
}
