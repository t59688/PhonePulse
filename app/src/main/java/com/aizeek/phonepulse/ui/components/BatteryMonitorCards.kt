package com.aizeek.phonepulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.battery.*
import com.aizeek.phonepulse.ui.theme.*
import com.aizeek.phonepulse.util.TimeFormatter
import java.util.Locale

@Composable
private fun MonitorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceElevatedDark)
        .border(1.dp, BorderDark, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable private fun MonitorText(text: String) { Text(text, color = TextSecondary, fontSize = 12.sp) }
private fun number(value: Double): String = String.format(Locale.getDefault(), "%.0f", value)
private fun percent(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
private fun duration(value: Long?): String = value?.let { TimeFormatter.formatDurationChinese(it) } ?: "暂无可靠估算"
private fun measuredCharge(cycle: BatteryCycle): String =
    if (cycle.measuredMs > 0) "${number(cycle.netMah)} mAh" else "暂无有效测量"
private fun coverage(cycle: BatteryCycle): String {
    val total = cycle.measuredMs.toDouble() + cycle.missingMs
    return if (total > 0) "${percent(cycle.measuredMs / total * 100)}%" else "暂无测量"
}

@Composable
fun BatteryMeasurementCard(state: BatteryMonitorUiState) {
    MonitorCard("实时监测") {
        Text("净电流 ${state.currentUa?.takeIf { it.isFinite() }?.let {
            String.format(Locale.getDefault(), "%+.0f mA", it / 1000)
        } ?: "暂不可用"}", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        MonitorText("正值为流入电池，负值为电池放电；这是电池净电流。")
        MonitorText(if (state.isRunning) "持续监测中" else "监测未运行 · 开启保活守护可持续记录")
        MonitorText(state.lastSampleTime?.let { "最近更新 ${TimeFormatter.formatDateTime(it)}" } ?: "等待首次采样")
        if (state.lastSampleTime != null && state.currentUa == null) {
            MonitorText("设备当前未提供有效电流读数，电量记录仍可查看；容量估算需要有效测量。")
        }
        state.error?.let { Text("监测提示：$it", color = AmberWarning, fontSize = 12.sp) }
        state.activeCycle?.let { cycle ->
            HorizontalDivider(color = BorderDark)
            MonitorText("本次${if (cycle.charging) "充电" else "放电"} ${cycle.startPct}% → ${cycle.endPct}%")
            MonitorText("已测净电荷 ${measuredCharge(cycle)} · 覆盖率 ${coverage(cycle)}")
            MonitorText("测量 ${duration(cycle.measuredMs)} · 缺失 ${duration(cycle.missingMs)}")
            if (cycle.measuredMs > 0) MonitorText("亮屏 ${number(cycle.screenOnMah)} mAh · 熄屏 ${number(cycle.screenOffMah)} mAh")
            if (cycle.charging && cycle.to100Mah != null) {
                MonitorText("首次达到 100% 时累计 ${number(cycle.to100Mah)} mAh")
                if (cycle.netMah.isFinite() && cycle.to100Mah.isFinite()) {
                    MonitorText("此后净充入 ${number(cycle.netMah - cycle.to100Mah)} mAh")
                }
                MonitorText("满电后的继续充入量仅作记录，不能据此判断危险过充。")
            }
        } ?: MonitorText("尚无正在记录的充放电会话")
    }
}

@Composable
fun BatteryHealthCard(state: BatteryMonitorUiState) {
    val health = state.health
    val points = remember(state.cycles, state.settings.calibrationRevision) { state.cycles.filter {
        it.endTime != null && it.charging && !it.excluded && (!it.socDiscontinuity || it.fullChargeMah != null) &&
        it.calibrationRevision == state.settings.calibrationRevision && it.rejectionReason == null &&
        it.estimatedCapacityMah?.let { value -> value.isFinite() && value > 0 } == true }
        .sortedBy { it.startTime } }
    MonitorCard("容量与电池健康") {
        Text("容量保持率 ${health.healthPct?.let { "${percent(it)}%" } ?: "待估算"}", color = NeonEmerald,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (health.healthPct?.let { it > 125 || it < 60 } == true) {
            Text("结果偏离标称容量，请核对设计容量和校准；不应仅据此判断损坏。", color = AmberWarning, fontSize = 12.sp)
        }
        MonitorText("设计容量 ${state.settings.designCapacityMah?.let { "${number(it)} mAh" } ?: "尚未设置"}")
        MonitorText("估算实际容量 ${health.capacityMah?.let { "${number(it)} mAh" } ?: "暂无可靠估算"}")
        MonitorText("有效样本 ${health.acceptedCount} 次")
        if (health.acceptedCount >= 5) MonitorText("当前估算采用最近 5 次有效充电记录")
        MonitorText("样本离散程度 ${health.spreadPct?.let { "${percent(it)}%" } ?: "待更多样本"}")
        MonitorText(if (health.acceptedCount >= 3 && health.spreadPct?.let { it <= 10 } == true)
            "多次测量较一致；结果仍是估算值。" else "初步测量：样本较少或波动较大，请继续正常使用。")
        if (health.capacityMah == null) MonitorText("暂无健康数据通常是有效充电样本不足，并不代表电池损坏。较大电量跨度和充分测量覆盖有助于估算。")
        if (state.settings.designCapacityMah == null) MonitorText("填写设备标称设计容量后，才能计算容量保持率。")
        if (points.isEmpty()) MonitorText("容量趋势：等待有效充电记录") else {
            val description = "容量趋势，${points.size} 次有效充电记录：" + points.joinToString("；") {
                "${TimeFormatter.formatDateTime(it.startTime)}，${number(it.estimatedCapacityMah!!)} mAh"
            }
            Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = description }) {
                val values = points.map { it.estimatedCapacityMah!!.toFloat() }
                val min = values.min() * .95f
                val max = (values.max() * 1.05f).coerceAtLeast(min + 1)
                val coords = values.mapIndexed { index, value ->
                    Offset(if (values.size == 1) size.width / 2 else 8.dp.toPx() + index.toFloat() / (values.size - 1) * (size.width - 16.dp.toPx()),
                        size.height - 8.dp.toPx() - (value - min) / (max - min) * (size.height - 16.dp.toPx()))
                }
                coords.zipWithNext().forEach { (a, b) -> drawLine(NeonEmerald, a, b, 2.dp.toPx()) }
                coords.forEach { drawCircle(NeonEmerald, 3.dp.toPx(), it) }
            }
            MonitorText("有效充电估算趋势 · ${number(points.first().estimatedCapacityMah!!)} → ${number(points.last().estimatedCapacityMah!!)} mAh")
        }
    }
}

@Composable
fun BatteryMaintenanceCard(state: BatteryMonitorUiState, onSave: (BatteryMonitorSettings) -> Unit, onMute: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    MonitorCard("充电养护与预计时间") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("目标电量提醒", color = TextPrimary, fontSize = 14.sp)
                MonitorText("达到 ${state.settings.chargeTargetPct}% 时提醒")
            }
            Switch(checked = state.settings.chargeAlarmEnabled,
                onCheckedChange = { onSave(state.settings.copy(chargeAlarmEnabled = it)) },
                modifier = Modifier.testTag("battery_alarm_switch").semantics { contentDescription = "目标电量提醒" })
        }
        MonitorText("提醒需要允许通知；不会自动停止充电。")
        TextButton(onClick = { editing = true }) { Text("编辑设置", color = NeonCyan) }
        state.activeCycle?.takeIf { it.charging && state.settings.chargeAlarmEnabled }?.let {
            if (it.alarmMuted) MonitorText("本次充电提醒已静音")
            else TextButton(onClick = onMute) { Text("本次充电静音", color = NeonCyan) }
        }
        val charging = state.activeCycle?.charging
        if (charging == true) {
            MonitorText("距目标 ${state.settings.chargeTargetPct}%：${duration(state.estimates.toTargetMs)}")
            MonitorText("距充满：${duration(state.estimates.toFullMs)}")
        } else if (charging == false) {
            MonitorText("持续亮屏剩余：${duration(state.estimates.screenOnRemainingMs)}")
            MonitorText("持续待机剩余：${duration(state.estimates.screenOffRemainingMs)}")
            MonitorText("按近期混合使用：${duration(state.estimates.mixedRemainingMs)}")
        } else MonitorText("预计时间：等待有效充放电测量")
        predictionSourceLabel(state.estimates.source)?.let { MonitorText("估算依据：$it") }
        MonitorText("预计时间随使用方式与充电速度变化，数据不足时暂不估算。")
    }
    if (editing) BatterySettingsDialog(state.settings, { editing = false }) { onSave(it); editing = false }
}

@Composable
private fun BatterySettingsDialog(settings: BatteryMonitorSettings, onDismiss: () -> Unit, onSave: (BatteryMonitorSettings) -> Unit) {
    var design by rememberSaveable { mutableStateOf(settings.designCapacityMah?.let(::number) ?: "") }
    var target by rememberSaveable { mutableStateOf(settings.chargeTargetPct.toString()) }
    var scale by rememberSaveable { mutableStateOf(settings.currentScale.toString()) }
    var enabled by rememberSaveable { mutableStateOf(settings.chargeAlarmEnabled) }
    var invert by rememberSaveable { mutableStateOf(settings.invertCurrent) }
    var cells by rememberSaveable { mutableStateOf(settings.cellFactor) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(modifier = Modifier.testTag("battery_settings_dialog"), onDismissRequest = onDismiss,
        title = { Text("电池设置") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(design, { design = it }, label = { Text("设计容量（mAh，可留空）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("battery_design_input"))
                MonitorText("使用设备厂商标称的电池容量，不确定时可留空。")
                OutlinedTextField(target, { target = it }, label = { Text("提醒目标（50–100%）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.testTag("battery_target_input"))
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(enabled, { enabled = it }); Text("开启目标提醒") }
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起测量校准" else "测量校准（高级）") }
                if (advanced) {
                    MonitorText("仅在确认设备读数口径后调整。更改校准会开始新会话，旧记录不会重新计算。")
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(invert, { invert = it }); Text("反转电流方向") }
                    OutlinedTextField(scale, { scale = it }, label = { Text("电流倍率（0.001–1000）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("battery_scale_input"))
                    MonitorText("电芯换算由你手动选择，应用不会自动识别双电芯。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(cells == 1, { cells = 1 }); Text("不换算")
                        RadioButton(cells == 2, { cells = 2 }); Text("双电芯 ×2")
                    }
                }
                }
            }
        }, confirmButton = { TextButton(onClick = {
            val capacity = design.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val targetValue = target.trim().toIntOrNull()
            val scaleValue = scale.trim().toDoubleOrNull()
            error = when {
                design.isNotBlank() && (capacity == null || !capacity.isFinite() || capacity !in 500.0..30000.0) -> "设计容量需为 500–30000 mAh，或留空"
                targetValue == null || targetValue !in 50..100 -> "目标电量需为 50–100%"
                scaleValue == null || !scaleValue.isFinite() || scaleValue !in 0.001..1000.0 -> "电流倍率需为 0.001–1000"
                else -> null
            }
            if (error == null) onSave(settings.copy(designCapacityMah = capacity, chargeTargetPct = targetValue!!,
                chargeAlarmEnabled = enabled, currentScale = scaleValue!!, invertCurrent = invert, cellFactor = cells))
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun BatterySessionsCard(cycles: List<BatteryCycle>, onExclude: (Long, Boolean) -> Unit) {
    MonitorCard("最近充放电记录") {
        if (cycles.isEmpty()) MonitorText("暂无会话记录，持续监测后会自动记录。")
        cycles.sortedByDescending { it.startTime }.take(10).forEach { cycle ->
            var expanded by rememberSaveable(cycle.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceDark)
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.testTag("battery_cycle_${cycle.id}")) {
                    Text("${if (cycle.charging) "充电" else "放电"} ${cycle.startPct}% → ${cycle.endPct}%${if (cycle.endTime == null) " · 进行中" else ""}", color = TextPrimary)
                    MonitorText("${TimeFormatter.formatDateTime(cycle.startTime)} · ${if (expanded) "收起详情" else "展开详情"}")
                }
                MonitorText(if (cycle.excluded) "已由你排除，不计入健康估算" else rejectionLabel(cycle.rejectionReason))
                if (expanded) {
                    MonitorText("结束 ${cycle.endTime?.let(TimeFormatter::formatDateTime) ?: "尚未结束"}")
                    MonitorText("净电荷 ${measuredCharge(cycle)} · 覆盖率 ${coverage(cycle)}")
                    MonitorText("有效测量 ${duration(cycle.measuredMs)} · 缺失 ${duration(cycle.missingMs)}")
                    if (cycle.measuredMs > 0) MonitorText("亮屏 ${number(cycle.screenOnMah)} mAh · 熄屏 ${number(cycle.screenOffMah)} mAh")
                    MonitorText("深度休眠 ${duration(cycle.deepSleepMs)} · 高电量停留 ${duration(cycle.highSocMs)}")
                    MonitorText("峰值温度 ${cycle.maxTemperatureC?.let { "${percent(it)}°C" } ?: "未知"}")
                    MonitorText("本次容量估算 ${cycle.estimatedCapacityMah?.let { "${number(it)} mAh" } ?: "暂无可靠估算"}")
                    if (cycle.to100Mah != null) {
                        MonitorText("首次达到 100% 时累计 ${number(cycle.to100Mah)} mAh")
                        if (cycle.netMah.isFinite() && cycle.to100Mah.isFinite()) {
                            MonitorText("此后净充入 ${number(cycle.netMah - cycle.to100Mah)} mAh")
                        }
                        MonitorText("满电后的继续充入量仅作记录，不能据此判断危险过充。")
                    }
                    if (cycle.endTime != null) TextButton(onClick = { onExclude(cycle.id, !cycle.excluded) }) {
                        Text(if (cycle.excluded) "重新纳入此记录" else "排除此记录", color = NeonCyan)
                    }
                }
            }
        }
    }
}

private fun predictionSourceLabel(source: String?): String? = when (source) {
    "SESSION" -> "本次充电速度"
    "SOC_HISTORY" -> "历史分电量区间充电速度"
    "HISTORY" -> "近期电量变化与使用时长"
    "MAH_HISTORY" -> "近期实测耗电与估算容量"
    else -> null
}

private fun rejectionLabel(reason: String?): String = when (reason) {
    null -> "满足条件的充电记录可用于容量估算"
    "NOT_CHARGING", "not_charging" -> "放电记录，不用于容量估算"
    "SMALL_SOC_CHANGE", "INSUFFICIENT_SOC", "insufficient_soc", "small_soc_delta" -> "电量变化不足，需要更大充电跨度"
    "LOW_COVERAGE", "low_coverage", "insufficient_coverage" -> "测量缺失较多，暂不用于容量估算"
    "SOC_DISCONTINUITY", "soc_discontinuity" -> "电量出现跳变，暂不用于容量估算"
    "INVALID_CAPACITY", "invalid_capacity" -> "容量读数异常，暂不用于容量估算"
    "INCOMPLETE", "incomplete" -> "记录不完整，暂不用于容量估算"
    "REBOOT" -> "设备重启中断了测量，暂不用于容量估算"
    "CALIBRATION_CHANGED" -> "测量校准已调整，旧会话已结束"
    "INCOMPLETE_RESTORE" -> "恢复时缺少完整记录，暂不用于容量估算"
    "NO_MEASUREMENT" -> "暂无有效电荷测量，暂不用于容量估算"
    "NON_POSITIVE_CHARGE" -> "未测得有效净充入电荷，暂不用于容量估算"
    else -> "此记录不满足可靠容量估算条件"
}
