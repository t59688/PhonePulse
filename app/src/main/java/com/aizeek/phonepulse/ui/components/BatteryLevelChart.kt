package com.aizeek.phonepulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.NeonEmerald
import com.aizeek.phonepulse.ui.theme.SurfaceDark
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextTertiary
import com.aizeek.phonepulse.util.TimeFormatter

@Composable
fun BatteryLevelChart(records: List<BatteryRecord>, modifier: Modifier = Modifier) {
    val first = records.firstOrNull()
    val last = records.lastOrNull()
    val segments = remember(records) { records.zipWithNext() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        SurfaceElevatedDark.copy(alpha = 0.95f),
                        SurfaceDark
                    )
                )
            )
            .border(1.dp, BorderDark, RoundedCornerShape(26.dp))
            .padding(20.dp)
            .testTag("battery_level_chart")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                        )
                    }
                    Text("今日电量变化", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartLegendSwatch(color = NeonCyan, label = "放电 / 使用")
                    ChartLegendSwatch(color = NeonEmerald, label = "充电")
                }
            }

            if (first == null || last == null) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("暂无电量记录，等待采集", color = TextTertiary, fontSize = 13.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark.copy(alpha = 0.6f))
                        .border(1.dp, BorderDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${TimeFormatter.formatShortTime(first.timestamp)} ${first.percentage}% → " +
                            "${TimeFormatter.formatShortTime(last.timestamp)} ${last.percentage}%",
                        color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }

                Row(Modifier.fillMaxWidth().height(180.dp)) {
                    Column(Modifier.width(36.dp).height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        listOf("100%", "50%", "0%").forEach {
                            Text(it, color = TextTertiary, fontSize = 10.sp)
                        }
                    }
                    Canvas(Modifier.weight(1f).height(180.dp).semantics {
                        contentDescription = "今日电量曲线，${records.size} 个采样，" +
                            "从 ${first.percentage}% 到 ${last.percentage}%"
                    }) {
                        val inset = 5.dp.toPx()
                        val plotHeight = (size.height - inset * 2).coerceAtLeast(0f)
                        val plotWidth = (size.width - inset * 2).coerceAtLeast(0f)
                        val bottomY = inset + plotHeight

                        listOf(0f, 0.5f, 1f).forEach { fraction ->
                            val y = inset + plotHeight * fraction
                            drawLine(
                                color = BorderDark.copy(alpha = 0.7f),
                                start = Offset(inset, y),
                                end = Offset(inset + plotWidth, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val duration = (last.timestamp - first.timestamp).coerceAtLeast(1L)
                        fun point(record: BatteryRecord) = Offset(
                            inset + (record.timestamp - first.timestamp).toFloat() / duration * plotWidth,
                            inset + (1f - record.percentage.coerceIn(0, 100) / 100f) * plotHeight
                        )

                        if (records.size >= 2) {
                            val areaPath = androidx.compose.ui.graphics.Path().apply {
                                val startPt = point(records.first())
                                moveTo(startPt.x, startPt.y)
                                for (i in 1 until records.size) {
                                    val pt = point(records[i])
                                    lineTo(pt.x, pt.y)
                                }
                                lineTo(point(records.last()).x, bottomY)
                                lineTo(startPt.x, bottomY)
                                close()
                            }
                            val fillBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    (if (last.isCharging) NeonEmerald else NeonCyan).copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                startY = inset,
                                endY = bottomY
                            )
                            drawPath(areaPath, fillBrush)
                        }

                        segments.forEach { (previous, next) ->
                            drawLine(
                                if (previous.isCharging) NeonEmerald else NeonCyan,
                                point(previous), point(next), 2.5.dp.toPx(), StrokeCap.Round
                            )
                        }

                        val lastPt = point(last)
                        val lastColor = if (last.isCharging) NeonEmerald else NeonCyan
                        drawCircle(lastColor.copy(alpha = 0.25f), 7.dp.toPx(), lastPt)
                        drawCircle(lastColor, 3.5.dp.toPx(), lastPt)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(TimeFormatter.formatShortTime(first.timestamp), color = TextTertiary, fontSize = 10.sp)
                    if (last.timestamp > first.timestamp) {
                        Text(TimeFormatter.formatShortTime(first.timestamp + (last.timestamp - first.timestamp) / 2),
                            color = TextTertiary, fontSize = 10.sp)
                        Text(TimeFormatter.formatShortTime(last.timestamp), color = TextTertiary, fontSize = 10.sp)
                    }
                }
                if (records.size == 1) {
                    Text("仅有一个采样点，继续记录后显示变化曲线", color = TextTertiary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ChartLegendSwatch(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
