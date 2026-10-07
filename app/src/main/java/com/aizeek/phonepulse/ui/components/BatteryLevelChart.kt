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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextTertiary
import com.aizeek.phonepulse.util.TimeFormatter

@Composable
fun BatteryLevelChart(records: List<BatteryRecord>, modifier: Modifier = Modifier) {
    val first = records.firstOrNull()
    val last = records.lastOrNull()
    Column(
        modifier = modifier.fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(24.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
            .padding(20.dp).testTag("battery_level_chart"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("今日电量变化", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("● 放电 / 使用", color = NeonCyan, fontSize = 11.sp)
            Text("● 充电", color = NeonEmerald, fontSize = 11.sp)
        }
        if (first == null || last == null) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text("暂无电量记录，等待采集", color = TextTertiary, fontSize = 13.sp)
            }
        } else {
            Text(
                "${TimeFormatter.formatShortTime(first.timestamp)} ${first.percentage}% → " +
                    "${TimeFormatter.formatShortTime(last.timestamp)} ${last.percentage}%",
                color = TextPrimary, fontSize = 12.sp
            )
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
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        val y = inset + plotHeight * fraction
                        drawLine(BorderDark, Offset(inset, y), Offset(inset + plotWidth, y), 1.dp.toPx())
                    }
                    val duration = (last.timestamp - first.timestamp).coerceAtLeast(1L)
                    fun point(record: BatteryRecord) = Offset(
                        inset + (record.timestamp - first.timestamp).toFloat() / duration * plotWidth,
                        inset + (1f - record.percentage.coerceIn(0, 100) / 100f) * plotHeight
                    )
                    records.zipWithNext().forEach { (previous, next) ->
                        drawLine(
                            if (previous.isCharging) NeonEmerald else NeonCyan,
                            point(previous), point(next), 2.dp.toPx(), StrokeCap.Round
                        )
                    }
                    drawCircle(if (last.isCharging) NeonEmerald else NeonCyan, 4.dp.toPx(), point(last))
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
