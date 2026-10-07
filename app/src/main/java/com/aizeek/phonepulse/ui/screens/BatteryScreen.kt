package com.aizeek.phonepulse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.ui.components.BatteryLevelChart
import com.aizeek.phonepulse.ui.components.BatteryStatsCard
import com.aizeek.phonepulse.ui.theme.TextPrimary
import com.aizeek.phonepulse.ui.theme.TextSecondary

@Composable
fun BatteryScreen(
    batteryInfo: LiveBatteryInfo,
    records: List<BatteryRecord>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("电量统计", color = TextPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("设备电量变化与亮屏 / 熄屏耗电", color = TextSecondary, fontSize = 12.sp)
        }
        item { BatteryLevelChart(records = records) }
        item { BatteryStatsCard(batteryInfo = batteryInfo) }
        item {
            Text("曲线仅展示已记录的电量，开启保活守护可持续采集。",
                color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
