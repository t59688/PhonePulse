package com.aizeek.phonepulse.ui.components

import android.graphics.Paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.aizeek.phonepulse.data.AppUsageTimeline
import com.aizeek.phonepulse.ui.theme.BorderDark
import com.aizeek.phonepulse.ui.theme.ElectricViolet
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.SurfaceElevatedDark
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

@Composable
fun AppUsageTimelineCard(packageName: String, loadTimeline: suspend (String) -> AppUsageTimeline?) {
    var timeline by remember(packageName) { mutableStateOf<AppUsageTimeline?>(null) }
    var error by remember(packageName) { mutableStateOf<String?>(null) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var refreshKey by remember(packageName) { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(packageName, lifecycleOwner, refreshKey) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loading = true
            error = null
            try { timeline = loadTimeline(packageName) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { timeline = null; error = e.message ?: "无法读取使用事件，请重试" }
            loading = false
            awaitCancellation()
        }
    }
    Column(Modifier.fillMaxWidth().background(SurfaceElevatedDark, RoundedCornerShape(20.dp))
        .border(1.dp, BorderDark, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今日使用分布", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { refreshKey++ }, enabled = !loading) { Text("刷新", color = NeonCyan) }
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = NeonCyan)
        else timeline?.let { AppUsageChart(it) }
            ?: Text(error ?: "暂无可用的今日使用事件", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun AppUsageChart(timeline: AppUsageTimeline) {
    var selectedIndex by remember(timeline) { mutableIntStateOf(
        timeline.hours.indexOfLast { it.durationMs > 0 }.coerceAtLeast(0)) }
    val duration = (timeline.endMs - timeline.startMs).coerceAtLeast(1)
    val density = LocalDensity.current
    val axisPaint = remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TextSecondary.toArgb()
        textSize = with(density) { 10.sp.toPx() }
    } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("app_usage_chart")) {
        Text("00:00 — ${TimeFormatter.formatShortTime(timeline.endMs)} · 每小时前台使用分钟数",
            color = TextSecondary, fontSize = 12.sp)
        Text("今日已记录 ${TimeFormatter.formatDurationChinese(timeline.totalDurationMs)} · ${timeline.sessions.size} 个使用时段",
            color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row {
            Column(Modifier.width(32.dp).height(160.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("60分", "30分", "0分").forEach { Text(it, color = TextSecondary, fontSize = 10.sp) }
            }
            Canvas(Modifier.weight(1f).height(160.dp).testTag("app_usage_hour_bars")
                .semantics { contentDescription = "今日每小时使用柱状图，点击柱子查看对应小时用量" }
                .pointerInput(timeline) {
                    detectTapGestures { offset ->
                        val time = timeline.startMs + ((offset.x.toDouble() / size.width).coerceIn(0.0, 1.0) * duration).toLong()
                        selectedIndex = timeline.hours.indexOfFirst { time >= it.startMs && time < it.endMs }
                            .takeIf { it >= 0 } ?: timeline.hours.lastIndex.coerceAtLeast(0)
                    }
                }) {
                val inset = 4.dp.toPx()
                val graphHeight = size.height - inset * 2
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = inset + graphHeight * fraction
                    drawLine(BorderDark, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                timeline.hours.forEachIndexed { index, hour ->
                    val left = (hour.startMs - timeline.startMs).toFloat() / duration * size.width
                    val right = (hour.endMs - timeline.startMs).toFloat() / duration * size.width
                    if (index == selectedIndex) drawRect(ElectricViolet.copy(alpha = 0.12f),
                        Offset(left, 0f), Size(right - left, size.height))
                    val barHeight = (hour.durationMs / 3_600_000f).coerceIn(0f, 1f) * graphHeight
                    val gap = minOf(3.dp.toPx(), (right - left) / 4)
                    if (barHeight > 0) drawRoundRect(if (index == selectedIndex) ElectricViolet else NeonCyan,
                        Offset(left + gap, inset + graphHeight - barHeight), Size((right - left - gap * 2).coerceAtLeast(0f), barHeight),
                        CornerRadius(2.dp.toPx()))
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(start = 32.dp).height(18.dp)
            .semantics { contentDescription = "时间轴，从 00:00 到 ${TimeFormatter.formatShortTime(timeline.endMs)}" }) {
            val baseline = -axisPaint.ascent()
            axisPaint.textAlign = Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText("00:00", 0f, baseline, axisPaint)
            val step = ((timeline.hours.size + 3) / 4).coerceAtLeast(1)
            timeline.hours.forEachIndexed { index, hour ->
                val x = (hour.startMs - timeline.startMs).toFloat() / duration * size.width
                if (index > 0 && index % step == 0 && x > 40.dp.toPx() && size.width - x > 40.dp.toPx()) {
                    axisPaint.textAlign = Paint.Align.CENTER
                    drawContext.canvas.nativeCanvas.drawText(TimeFormatter.formatShortTime(hour.startMs), x, baseline, axisPaint)
                }
            }
            axisPaint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(TimeFormatter.formatShortTime(timeline.endMs), size.width, baseline, axisPaint)
        }
        timeline.hours.getOrNull(selectedIndex)?.let { hour ->
            Text("${TimeFormatter.formatShortTime(hour.startMs)}–${TimeFormatter.formatShortTime(hour.endMs)}：${TimeFormatter.formatDurationChinese(hour.durationMs)}",
                color = TextPrimary, fontSize = 13.sp, modifier = Modifier.testTag("app_usage_selected_hour"))
        }
        Text("使用时段 · 亮色为前台使用", color = TextSecondary, fontSize = 11.sp)
        Canvas(Modifier.fillMaxWidth().height(14.dp).testTag("app_usage_session_band")
            .semantics { contentDescription = "00:00 到查询时刻的前台使用时间带，共 ${timeline.sessions.size} 个时段" }) {
            drawRoundRect(BorderDark, cornerRadius = CornerRadius(3.dp.toPx()))
            timeline.sessions.forEach { session ->
                val left = (session.startMs - timeline.startMs).toFloat() / duration * size.width
                val right = (session.endMs - timeline.startMs).toFloat() / duration * size.width
                drawRect(NeonCyan, Offset(left, 0f), Size(right - left, size.height))
            }
        }
        Text(if (timeline.sessions.isEmpty()) "系统暂未提供可还原的今日前台使用事件。"
            else "点击柱子查看用量。仅统计可还原的系统前台事件，未保留的事件无法补齐。",
            color = TextSecondary, fontSize = 11.sp)
    }
}
