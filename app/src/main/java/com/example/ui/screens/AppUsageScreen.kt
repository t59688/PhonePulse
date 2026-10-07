package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppUsageInfo
import com.example.data.UsagePeriod
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderDark
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevatedDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.TimeFormatter

enum class AppSortMode {
    BY_DURATION,
    BY_BATTERY
}

@Composable
fun AppUsageScreen(
    hasPermission: Boolean,
    isLoading: Boolean,
    usageList: List<AppUsageInfo>,
    currentPeriod: UsagePeriod,
    onPeriodSelected: (UsagePeriod) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(AppSortMode.BY_DURATION) }

    val filteredList = remember(usageList, searchQuery, sortMode) {
        val base = if (searchQuery.isBlank()) usageList
        else usageList.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        when (sortMode) {
            AppSortMode.BY_DURATION -> base.sortedByDescending { it.totalTimeInForegroundMs }
            AppSortMode.BY_BATTERY -> base.sortedByDescending { it.estimatedBatteryDrainPct }
        }
    }

    val totalForegroundTimeMs = remember(usageList) {
        usageList.sumOf { it.totalTimeInForegroundMs }
    }

    val totalEstimatedDrainPct = remember(usageList) {
        usageList.sumOf { it.estimatedBatteryDrainPct.toDouble() }.toFloat()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "应用时长与耗电",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "各 App 前台运行时间与预估电量消耗",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, CircleShape)
                        .clickable { onRefresh() }
                        .testTag("refresh_usage_stats_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Permission notice banner if not granted
        if (!hasPermission) {
            item {
                PermissionRequestBanner(onRequestPermission = onRequestPermission)
            }
        }

        // Period filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    UsagePeriod.TODAY to "今日 (Today)",
                    UsagePeriod.LAST_24_HOURS to "近 24 小时",
                    UsagePeriod.LAST_7_DAYS to "近 7 天"
                ).forEach { (period, label) ->
                    val isSelected = currentPeriod == period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else SurfaceDark)
                            .border(
                                1.dp,
                                if (isSelected) NeonCyan else BorderDark,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onPeriodSelected(period) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("period_chip_${period.name}")
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) NeonCyan else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Hero Summary Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                NeonCyan.copy(alpha = 0.08f),
                                ElectricViolet.copy(alpha = 0.08f),
                                SurfaceDark
                            )
                        )
                    )
                    .border(1.dp, BorderDark, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "前台 App 总时长",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TimeFormatter.formatDurationChinese(totalForegroundTimeMs),
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "应用累计耗电约 ${String.format("%.1f", totalEstimatedDrainPct)}% · 已统计 ${usageList.size} 款应用",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Sort mode toggle & Search row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索系统应用...", color = TextTertiary, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("app_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Sort toggle button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                        .clickable {
                            sortMode = if (sortMode == AppSortMode.BY_DURATION) AppSortMode.BY_BATTERY else AppSortMode.BY_DURATION
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                        .testTag("toggle_sort_mode"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (sortMode == AppSortMode.BY_DURATION) "⏱ 按时长" else "⚡ 按耗电",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(32.dp))
                }
            }
        } else if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (!hasPermission) "请授权使用情况访问权限后查看" else "当前时段内未获取到系统前台运行记录",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.packageName }) { app ->
                AppUsageItemCard(app = app)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppUsageItemCard(
    app: AppUsageInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("app_usage_card_${app.packageName}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Cached Real App Icon (Rendered with 0ms overhead)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevatedDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (app.iconBitmap != null) {
                            Image(
                                bitmap = app.iconBitmap,
                                contentDescription = app.appName,
                                modifier = Modifier.size(38.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (app.lastTimeUsedMs > 0) "最近: ${TimeFormatter.formatDateTime(app.lastTimeUsedMs)}" else app.packageName,
                            color = TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Time Duration & Estimated Battery Drain
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TimeFormatter.formatDurationCompact(app.totalTimeInForegroundMs),
                        color = NeonCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "~${String.format("%.1f", app.estimatedBatteryDrainPct)}% (${app.estimatedMah}mAh)",
                            color = AmberWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Percentage bar
            val safeFraction = (app.percentageOfTotal / 100f).coerceIn(0.02f, 1f)
            LinearProgressIndicator(
                progress = { safeFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = NeonCyan,
                trackColor = SurfaceElevatedDark
            )
        }
    }
}

@Composable
private fun PermissionRequestBanner(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AmberWarning.copy(alpha = 0.1f))
            .border(1.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("usage_permission_banner")
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "需要应用使用情况访问权限",
                    color = AmberWarning,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Android 系统需要授权「有权查看使用情况的应用」后，ScreenPulse 才能获取每个 APP 的真实名称、图标和前台实际运行时间。",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberWarning,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_usage_permission_btn")
            ) {
                Text("一键前往系统设置授权", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
