package com.aizeek.phonepulse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.AppBatteryUsage
import com.aizeek.phonepulse.data.AppUsageTimeline
import com.aizeek.phonepulse.data.UsagePeriod
import com.aizeek.phonepulse.data.estimateAppBatteryUsage
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.ui.components.BatteryLevelChart
import com.aizeek.phonepulse.ui.components.BatteryStatsCard
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.util.TimeFormatter

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
    loadTimeline: suspend (String) -> AppUsageTimeline? = { null }
) {
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val estimates = remember(usageList, records) { estimateAppBatteryUsage(usageList, records)
        .sortedWith(compareByDescending<AppBatteryUsage> { it.estimatedDrainPct ?: -1f }
            .thenByDescending { it.app.totalTimeInForegroundMs }) }
    val selected = estimates.firstOrNull { it.app.packageName == selectedPackage }
    if (selected != null) {
        AppDetailScreen(selected.app, UsagePeriod.TODAY, selected, onBack = { selectedPackage = null }, loadTimeline = loadTimeline)
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("battery_statistics_list"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("电量统计", color = TextPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("设备电量变化与亮屏 / 熄屏耗电", color = TextSecondary, fontSize = 12.sp)
        }
        item { BatteryLevelChart(records = records) }
        item { BatteryStatsCard(batteryInfo = batteryInfo) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("应用耗电 · 今日", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh, enabled = !isLoading) { Text("刷新", color = NeonCyan) }
            }
            Text("按已记录的亮屏放电与今日前台时长占比估算，不含后台精确归因。点击应用查看详情。",
                color = TextSecondary, fontSize = 12.sp)
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
        item {
            Text("曲线仅展示已记录的电量，开启保活守护可持续采集。",
                color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun AppBatteryItemCard(usage: AppBatteryUsage, onClick: () -> Unit) {
    val app = usage.app
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceElevatedDark)
        .border(1.dp, BorderDark, RoundedCornerShape(18.dp)).clickable(onClick = onClick)
        .testTag("app_battery_card_${app.packageName}").padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app.iconBitmap != null) Image(app.iconBitmap, null, Modifier.size(40.dp))
        else Icon(Icons.Default.Apps, null, Modifier.size(40.dp), tint = NeonCyan)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(app.appName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("前台 ${TimeFormatter.formatDurationCompact(app.totalTimeInForegroundMs)}", color = TextSecondary, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatEstimatedDrain(usage.estimatedDrainPct), color = NeonCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("估算耗电", color = TextSecondary, fontSize = 11.sp)
        }
    }
}
