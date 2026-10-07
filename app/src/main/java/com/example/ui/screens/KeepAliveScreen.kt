package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun KeepAliveScreen(
    isServiceRunning: Boolean,
    isBatteryIgnoring: Boolean,
    hasUsagePermission: Boolean,
    hasNotificationPermission: Boolean,
    onToggleService: (Boolean) -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onInjectSampleData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedVendorGuide by remember { mutableStateOf(false) }

    // Calculate score
    val passedCount = listOf(
        isServiceRunning,
        isBatteryIgnoring,
        hasUsagePermission,
        hasNotificationPermission
    ).count { it }
    val score = (passedCount * 25)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Header
            Column {
                Text(
                    text = "后台保活与权限中心",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "多重守护机制，防止系统后台休眠杀死",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Health Score Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                if (score >= 75) NeonEmerald.copy(alpha = 0.12f) else AmberWarning.copy(alpha = 0.12f),
                                SurfaceElevatedDark,
                                SurfaceDark
                            )
                        )
                    )
                    .border(
                        1.dp,
                        if (score >= 75) NeonEmerald.copy(alpha = 0.4f) else AmberWarning.copy(alpha = 0.4f),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(20.dp)
                    .testTag("keep_alive_score_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "保活防杀等级",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$score",
                                color = if (score >= 75) NeonEmerald else AmberWarning,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " / 100",
                                color = TextTertiary,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (score == 100) "🛡️ 所有保活机制就绪，守护极其稳定"
                            else "⚠️ 建议补齐未配置项，避免熄屏被杀",
                            color = if (score == 100) NeonEmerald else AmberWarning,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                if (score >= 75) NeonEmerald.copy(alpha = 0.15f)
                                else AmberWarning.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (score >= 75) NeonEmerald else AmberWarning,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }

        // Section Title: Core Protections
        item {
            Text(
                text = "核心保活防杀机制清单",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Item 1: 前台常驻通知服务
        item {
            KeepAliveItemCard(
                title = "前台常驻通知服务 (Foreground Service)",
                subtitle = "提升进程优先级至最高层级，防止系统内存回收",
                isPassed = isServiceRunning,
                icon = Icons.Default.Security,
                trailingContent = {
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { onToggleService(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = SurfaceElevatedDark
                        ),
                        modifier = Modifier.testTag("toggle_foreground_service")
                    )
                }
            )
        }

        // Item 2: 电池优化白名单
        item {
            KeepAliveItemCard(
                title = "忽略电池优化 (Doze 白名单)",
                subtitle = "允许应用在熄屏深度睡眠时持续监听唤醒与熄屏广播",
                isPassed = isBatteryIgnoring,
                icon = Icons.Default.BatteryChargingFull,
                trailingContent = {
                    if (isBatteryIgnoring) {
                        PassedBadge()
                    } else {
                        ActionButton(text = "去豁免", onClick = onRequestBatteryOptimization)
                    }
                }
            )
        }

        // Item 3: 应用使用情况访问权限
        item {
            KeepAliveItemCard(
                title = "应用使用情况访问权限 (AppOps)",
                subtitle = "支持精确统计每一个 App 的前台实际运行时间",
                isPassed = hasUsagePermission,
                icon = Icons.Default.TrendingUp,
                trailingContent = {
                    if (hasUsagePermission) {
                        PassedBadge()
                    } else {
                        ActionButton(text = "去授权", onClick = onRequestUsagePermission)
                    }
                }
            )
        }

        // Item 4: 通知权限
        item {
            KeepAliveItemCard(
                title = "常驻通知推送权限",
                subtitle = "实时在状态栏展示当前亮屏与熄屏计时器",
                isPassed = hasNotificationPermission,
                icon = Icons.Default.Notifications,
                trailingContent = {
                    if (hasNotificationPermission) {
                        PassedBadge()
                    } else {
                        ActionButton(text = "去开启", onClick = onRequestNotificationPermission)
                    }
                }
            )
        }

        // Item 5: 开机自启动守护
        item {
            KeepAliveItemCard(
                title = "开机自启广播接收器",
                subtitle = "已动态配置 BOOT_COMPLETED，手机重启自动恢复计时",
                isPassed = true,
                icon = Icons.Default.PowerSettingsNew,
                trailingContent = {
                    PassedBadge(label = "已就绪")
                }
            )
        }

        // Vendor Keep-Alive Guide Accordion
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                    .clickable { expandedVendorGuide = !expandedVendorGuide }
                    .padding(16.dp)
                    .testTag("vendor_guide_accordion")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📱 各品牌手机防杀后台指南", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            imageVector = if (expandedVendorGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }

                    AnimatedVisibility(visible = expandedVendorGuide) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            VendorTipItem(vendor = "小米 / Redmi (MIUI / HyperOS)", tip = "在最近任务中长按锁定本应用；在应用信息中开启「自启动」，并将省电策略设为「无限制」。")
                            VendorTipItem(vendor = "华为 / 荣耀 (HarmonyOS)", tip = "设置 → 应用启动管理 → 关闭本应用的自动管理，改为手动管理并允许「自启动、关联启动、后台活动」。")
                            VendorTipItem(vendor = "OPPO / OnePlus (ColorOS)", tip = "多任务后台界面下拉应用卡片加锁；在电池设置中开启「允许完全后台行为」。")
                            VendorTipItem(vendor = "vivo / iQOO (OriginOS)", tip = "设置 → 电池 → 后台高耗电 → 允许 ScreenPulse 在后台高耗电运行。")
                            VendorTipItem(vendor = "三星 (One UI)", tip = "设置 → 电池和设备维护 → 电池 → 后台使用限制 → 将本应用加入「从不休眠的应用程序」。")
                        }
                    }
                }
            }
        }

        // Demo Data Injection
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "注入体验示例数据", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "快速生成完整的亮屏、熄屏与图表数据以供预览", color = TextTertiary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = onInjectSampleData,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceElevatedDark,
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("inject_sample_data_btn")
                    ) {
                        Text("一键注入", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KeepAliveItemCard(
    title: String,
    subtitle: String,
    isPassed: Boolean,
    icon: ImageVector,
    trailingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPassed) NeonEmerald.copy(alpha = 0.14f)
                            else AmberWarning.copy(alpha = 0.14f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPassed) NeonEmerald else AmberWarning,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            trailingContent()
        }
    }
}

@Composable
private fun PassedBadge(label: String = "已配置") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NeonEmerald.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = NeonEmerald,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = NeonEmerald,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = AmberWarning,
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VendorTipItem(vendor: String, tip: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = vendor, color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = tip, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
