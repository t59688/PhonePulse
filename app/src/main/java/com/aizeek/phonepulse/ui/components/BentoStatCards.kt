package com.aizeek.phonepulse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun BentoStatCards(
    lastScreenOffDurationMs: Long?,
    lastScreenOnDurationMs: Long?,
    todayTotalScreenOnMs: Long,
    todayTotalScreenOffMs: Long,
    todayWakeCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Title
        Text(
            text = "核心指标 · 屏幕状态统计",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )

        // Row 1: 上一次熄屏 vs 上一次亮屏 (Prominently requested by user)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card: 上一次熄屏持续时间
            MetricTile(
                title = "上一次熄屏",
                subtitle = "离屏休眠持续",
                value = lastScreenOffDurationMs?.let { TimeFormatter.formatDurationCompact(it) } ?: "暂无记录",
                detail = lastScreenOffDurationMs?.let { TimeFormatter.formatDurationChinese(it) } ?: "等待下次熄屏",
                icon = Icons.Default.Nightlight,
                accentColor = ElectricViolet,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_last_screen_off")
            )

            // Card: 上一次亮屏持续时间
            MetricTile(
                title = "上一次亮屏",
                subtitle = "连续使用时长",
                value = lastScreenOnDurationMs?.let { TimeFormatter.formatDurationCompact(it) } ?: "暂无记录",
                detail = lastScreenOnDurationMs?.let { TimeFormatter.formatDurationChinese(it) } ?: "记录采集中",
                icon = Icons.Default.Visibility,
                accentColor = NeonCyan,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_last_screen_on")
            )
        }

        // Row 2: 今日累计亮屏 & 唤醒次数
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 今日累计亮屏
            val onHours = todayTotalScreenOnMs / (1000f * 3600f)
            val progress = (onHours / 6f).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(800),
                label = "progress"
            )

            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(22.dp))
                    .padding(16.dp)
                    .testTag("card_today_screen_on")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日累计亮屏",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = TimeFormatter.formatDurationCompact(todayTotalScreenOnMs),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress bar compared to 6h reference
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NeonEmerald,
                        trackColor = SurfaceElevatedDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "参考目标 6h · 占比 ${(progress * 100).toInt()}%",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // 今日唤醒频次
            val avgDurationMs = if (todayWakeCount > 0) todayTotalScreenOnMs / todayWakeCount else 0L

            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(22.dp))
                    .padding(16.dp)
                    .testTag("card_today_wake_count")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日解锁",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(AmberWarning.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "$todayWakeCount 次",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (avgDurationMs > 0) "均次 ${TimeFormatter.formatDurationCompact(avgDurationMs)}" else "尚无均次",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    subtitle: String,
    value: String,
    detail: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.08f),
                        SurfaceDark
                    )
                )
            )
            .border(1.dp, BorderDark, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                color = TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = detail,
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}
