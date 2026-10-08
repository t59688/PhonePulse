package com.aizeek.phonepulse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.data.AppBatteryUsage
import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.AppUsageTimeline
import com.aizeek.phonepulse.ui.components.AppUsageTimelineCard
import com.aizeek.phonepulse.data.UsagePeriod
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.ElectricViolet
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.util.TimeFormatter
import java.util.Locale

@Composable
fun AppDetailScreen(app: AppUsageInfo, period: UsagePeriod, batteryUsage: AppBatteryUsage?, onBack: () -> Unit,
    loadTimeline: suspend (String) -> AppUsageTimeline? = { null }) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("app_detail_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回列表", tint = NeonCyan)
            }
            Column {
                Text("应用详情", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(when (period) {
                    UsagePeriod.TODAY -> "今日"
                    UsagePeriod.LAST_24_HOURS -> "近 24 小时"
                    UsagePeriod.LAST_7_DAYS -> "近 7 天"
                }, color = TextSecondary, fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).testTag("app_detail_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceElevatedDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (app.iconBitmap != null) Image(app.iconBitmap, null, Modifier.size(44.dp))
                    else Icon(Icons.Default.Apps, null, Modifier.size(44.dp), tint = NeonCyan)
                    Column(Modifier.weight(1f)) {
                        Text(app.appName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(app.packageName, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
            item { AppUsageTimelineCard(app.packageName, loadTimeline) }
            item { AppDataCard("前台使用时长", TimeFormatter.formatDurationChinese(app.totalTimeInForegroundMs),
                "当前所选时段内的前台活跃时间") }
            item { AppDataCard("前台活跃占比", String.format(Locale.getDefault(), "%.1f%%", app.percentageOfTotal),
                "占当前时段所有应用前台使用时长的比例", ElectricViolet) }
            item { AppDataCard("应用耗电数据", "未提供",
                "系统使用情况接口仅提供使用时长，无法据此测得各应用耗电；本页不按时长分摊设备电量。") }
            item { AppDataCard("最近使用", if (app.lastTimeUsedMs > 0) TimeFormatter.formatDateTime(app.lastTimeUsedMs) else "暂无记录",
                "来自系统应用使用情况统计") }
            if (app.launchCount > 0) item { AppDataCard("打开次数", "${app.launchCount} 次", "当前所选时段") }
            item { Text("使用时长不代表耗电大小。设备电量和净电荷在电池页单独记录。",
                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun AppDataCard(title: String, value: String, description: String, accent: Color = NeonCyan) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceElevatedDark)
        .border(1.dp, BorderDark, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(description, color = TextSecondary, fontSize = 12.sp)
    }
}

internal fun formatEstimatedDrain(value: Float?): String =
    value?.let { String.format(Locale.getDefault(), "≈%.1f%%", it) } ?: "待采集"
