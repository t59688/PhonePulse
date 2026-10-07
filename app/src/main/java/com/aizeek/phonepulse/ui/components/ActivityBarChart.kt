package com.aizeek.phonepulse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.data.HourlyScreenStat
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.SurfaceDark
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.ui.theme.TextTertiary
import java.util.Calendar

@Composable
fun ActivityBarChart(
    hourlyStats: List<HourlyScreenStat>,
    modifier: Modifier = Modifier
) {
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    var selectedHour by remember { mutableStateOf<Int?>(currentHour) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
            .padding(20.dp)
            .testTag("activity_bar_chart")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "今日亮屏时段分布",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "24小时亮屏活跃度分布图",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }

                // Selected hour inspection label
                val selectedStat = hourlyStats.find { it.hour == selectedHour }
                if (selectedStat != null) {
                    val minutes = selectedStat.screenOnMinutes.toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevatedDark)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${selectedStat.hour}:00 · ${minutes}分钟",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bars Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Ensure 24 items
                val fullHours = if (hourlyStats.size >= 24) {
                    hourlyStats
                } else {
                    (0..23).map { h ->
                        hourlyStats.find { it.hour == h } ?: HourlyScreenStat(h, 0f)
                    }
                }

                fullHours.forEach { stat ->
                    val isCurrent = stat.hour == currentHour
                    val isSelected = stat.hour == selectedHour

                    // Max minutes in an hour is 60, normalize
                    val targetFraction = (stat.screenOnMinutes / 60f).coerceIn(0.06f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = tween(durationMillis = 600, delayMillis = stat.hour * 15),
                        label = "bar_height"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedHour = stat.hour },
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .fillMaxHeight(animatedFraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    when {
                                        isSelected -> NeonCyan
                                        isCurrent -> NeonCyan.copy(alpha = 0.8f)
                                        stat.screenOnMinutes > 0f -> NeonCyan.copy(alpha = 0.45f)
                                        else -> SurfaceElevatedDark
                                    }
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time axis labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach { label ->
                    Text(
                        text = label,
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
