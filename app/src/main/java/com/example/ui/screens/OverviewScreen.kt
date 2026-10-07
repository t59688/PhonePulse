package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HourlyScreenStat
import com.example.data.ScreenSession
import com.example.ui.components.ActivityBarChart
import com.example.ui.components.BentoStatCards
import com.example.ui.components.LivePulseHeroCard
import com.example.ui.theme.BorderDark
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.TimeFormatter

@Composable
fun OverviewScreen(
    isScreenOn: Boolean,
    stateStartTimeMs: Long,
    currentDurationMs: Long,
    isServiceRunning: Boolean,
    lastScreenOffDurationMs: Long?,
    lastScreenOnDurationMs: Long?,
    todayTotalScreenOnMs: Long,
    todayTotalScreenOffMs: Long,
    todayWakeCount: Int,
    hourlyStats: List<HourlyScreenStat>,
    recentSessions: List<ScreenSession>,
    onSimulateToggle: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // Live Hero Pulse Card
            LivePulseHeroCard(
                isScreenOn = isScreenOn,
                startTimeMs = stateStartTimeMs,
                durationMs = currentDurationMs,
                isServiceRunning = isServiceRunning,
                onSimulateToggle = onSimulateToggle
            )
        }

        item {
            // Bento Grid Stat Cards
            BentoStatCards(
                lastScreenOffDurationMs = lastScreenOffDurationMs,
                lastScreenOnDurationMs = lastScreenOnDurationMs,
                todayTotalScreenOnMs = todayTotalScreenOnMs,
                todayTotalScreenOffMs = todayTotalScreenOffMs,
                todayWakeCount = todayWakeCount
            )
        }

        item {
            // 24-Hour Activity Bar Chart
            ActivityBarChart(
                hourlyStats = hourlyStats
            )
        }

        item {
            // Recent Sessions Stream Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近状态日志",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onNavigateToHistory() }
                        .padding(4.dp)
                ) {
                    Text(
                        text = "查看全部",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View all history",
                        tint = NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Recent 3 items
        val displaySessions = recentSessions.take(4)
        if (displaySessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无状态日志，等待屏幕状态切换记录",
                        color = TextTertiary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(displaySessions.size) { index ->
                val session = displaySessions[index]
                RecentSessionRow(session = session)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RecentSessionRow(
    session: ScreenSession,
    modifier: Modifier = Modifier
) {
    val isScreenOn = session.isScreenOn
    val accentColor = if (isScreenOn) NeonCyan else ElectricViolet

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("recent_session_item_${session.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isScreenOn) Icons.Default.WbSunny else Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isScreenOn) "亮屏使用" else "熄屏休眠",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${TimeFormatter.formatTime(session.startTime)} - ${TimeFormatter.formatTime(session.endTime)}",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // Duration Pill
            Text(
                text = TimeFormatter.formatDurationCompact(session.durationMs),
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
