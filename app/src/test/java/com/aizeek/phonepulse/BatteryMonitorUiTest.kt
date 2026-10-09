package com.aizeek.phonepulse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aizeek.phonepulse.battery.*
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.ui.components.*
import com.aizeek.phonepulse.ui.theme.PhonePulseTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryMonitorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `missing current and capacity are unknown`() {
        compose.setContent { PhonePulseTheme { BatteryMeasurementCard(BatteryMonitorUiState()) } }
        compose.onNodeWithText("净电流 暂不可用").assertIsDisplayed()
        compose.onNodeWithText("等待首次采样").assertIsDisplayed()
    }

    @Test fun `zero current is a valid reading`() {
        compose.setContent { PhonePulseTheme { BatteryMeasurementCard(BatteryMonitorUiState(currentUa = 0.0)) } }
        compose.onNodeWithText("净电流 +0 mA").assertIsDisplayed()
    }

    @Test fun `missing screen bucket measurements are not presented as zero charge`() {
        val cycle = BatteryCycle(charging = false, startTime = 0, lastTime = 30_000,
            lastElapsedMs = 30_000, startPct = 80, endPct = 80, missingMs = 30_000)
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatteryMeasurementCard(BatteryMonitorUiState(activeCycle = cycle))
            }
        } }
        compose.onAllNodesWithText("0 mAh").assertCountEquals(0)
    }

    @Test fun `missing system readings are shown as unknown`() {
        compose.setContent { PhonePulseTheme { BatteryStatsCard(LiveBatteryInfo()) } }
        compose.onNodeWithText("当前电量 暂不可用").assertExists()
        compose.onNodeWithText("电池温度: 暂不可用").assertExists()
        compose.onNodeWithText("系统电池状态: 未知 · 电压: 暂不可用").assertExists()
    }

    @Test fun `initial health explains insufficient samples`() {
        compose.setContent { PhonePulseTheme { BatteryHealthCard(BatteryMonitorUiState()) } }
        compose.onNodeWithText("估算实际容量 暂无可靠估算").assertIsDisplayed()
        compose.onNodeWithText("有效样本 0 次").assertIsDisplayed()
    }

    @Test fun `fragmented charging estimates are explicitly preliminary`() {
        val cycles = (1L..3L).map { id ->
            BatteryCycle(id = id, charging = true, startTime = id * 3_600_000,
                endTime = (id + 1) * 3_600_000, lastTime = (id + 1) * 3_600_000,
                lastElapsedMs = (id + 1) * 3_600_000, startPct = 50, endPct = 80,
                measuredMs = 3_600_000, netMah = 1200.0, rejectionReason = "SMALL_SOC_CHANGE")
        }
        val settings = BatteryMonitorSettings(designCapacityMah = 4000.0)
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatteryHealthCard(BatteryMonitorUiState(settings = settings, cycles = cycles,
                    health = BatteryEstimates.health(cycles, settings)))
            }
        } }
        compose.onNodeWithText("有效样本 3 次").assertExists()
        compose.onNodeWithText("短会话 3 次 · 大跨度 0 次").assertExists()
        compose.onNodeWithText("初步估算").assertExists()
        compose.onNodeWithText("样本较一致").assertDoesNotExist()
    }

    @Test fun `daily decline explains screen buckets and unattributed remainder`() {
        compose.setContent { PhonePulseTheme {
            BatteryStatsCard(LiveBatteryInfo(todayDrainKnown = true, todayTotalDrainPct = 9,
                totalScreenOnDrainPct = 4, totalScreenOffDrainPct = 2))
        } }
        compose.onNodeWithText("亮屏 4 · 熄屏 2 · 未归属 3 个百分点").assertExists()
    }

    @Test fun `health explains why the latest charge was rejected`() {
        val cycle = BatteryCycle(id = 1, charging = true, startTime = 0, endTime = 1000,
            lastTime = 1000, lastElapsedMs = 1000, startPct = 50, endPct = 80,
            rejectionReason = "SMALL_SOC_CHANGE")
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatteryHealthCard(BatteryMonitorUiState(cycles = listOf(cycle)))
            }
        } }
        compose.onNodeWithText("最近充电未计入：电量变化不足，需要更大充电跨度").assertExists()
    }

    @Test fun `confirmed full prefix remains visible in trend after maintenance soc change`() {
        val cycle = BatteryCycle(id = 1, charging = true, startTime = 1, endTime = 2,
            lastTime = 2, lastElapsedMs = 2, startPct = 10, endPct = 99,
            fullChargeMah = 3600.0, estimatedCapacityMah = 4000.0, socDiscontinuity = true)
        val settings = BatteryMonitorSettings(designCapacityMah = 5000.0)
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatteryHealthCard(BatteryMonitorUiState(settings = settings, cycles = listOf(cycle),
                    health = BatteryEstimates.health(listOf(cycle), settings)))
            }
        } }
        compose.onNodeWithText("有效样本 1 次").assertExists()
        compose.onNodeWithText("容量趋势：等待有效充电记录").assertDoesNotExist()
        compose.onNodeWithText("按记录顺序 · 4000 → 4000 mAh").performScrollTo().assertIsDisplayed()
    }

    @Test fun `confirmed full active capacity appears in trend before unplugging`() {
        val cycle = BatteryCycle(id = 1, charging = true, startTime = 1,
            lastTime = 2, lastElapsedMs = 2, startPct = 20, endPct = 100,
            fullChargeMah = 3200.0, estimatedCapacityMah = 4000.0)
        val settings = BatteryMonitorSettings(designCapacityMah = 4000.0)
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatteryHealthCard(BatteryMonitorUiState(settings = settings, activeCycle = cycle,
                    health = BatteryEstimates.health(listOf(cycle), settings)))
            }
        } }
        compose.onNodeWithText("有效样本 1 次").assertExists()
        compose.onNodeWithText("按记录顺序 · 4000 → 4000 mAh").performScrollTo().assertIsDisplayed()
    }

    @Test fun `unusual health estimate advises checking calibration`() {
        compose.setContent { PhonePulseTheme {
            BatteryHealthCard(BatteryMonitorUiState(health = BatteryHealthEstimate(healthPct = 130.0)))
        } }
        compose.onNodeWithText("容量异常，请核对设计容量和校准设置。").assertIsDisplayed()
    }

    @Test fun `charge after first full percentage is neutral measurement`() {
        val cycle = BatteryCycle(charging = true, startTime = 1, lastTime = 2, lastElapsedMs = 2,
            startPct = 10, endPct = 100, measuredMs = 1000, netMah = 4050.0, to100Mah = 4000.0)
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatteryMeasurementCard(BatteryMonitorUiState(activeCycle = cycle))
            }
        } }
        compose.onNodeWithText("此后净充入 50 mAh").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("满电后的继续充入量仅作记录，不能据此判断危险过充。").assertDoesNotExist()
    }

    @Test fun `legacy active cycle starting full cannot display fabricated first full charge`() {
        val cycle = BatteryCycle(charging = true, startTime = 1, lastTime = 2, lastElapsedMs = 2,
            startPct = 100, endPct = 100, to100Mah = 0.0)
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatteryMeasurementCard(BatteryMonitorUiState(activeCycle = cycle))
            }
        } }
        compose.onNodeWithText("首次达到 100% 时累计 0 mAh").assertDoesNotExist()
        compose.onNodeWithText("此后净充入 0 mAh").assertDoesNotExist()
    }

    @Test fun `system battery status is distinct from measured health`() {
        compose.setContent { PhonePulseTheme { BatteryStatsCard(LiveBatteryInfo()) } }
        compose.onNodeWithText("系统电池状态:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("健康度:", substring = true).assertDoesNotExist()
    }

    @Test fun `unobserved discharge rates are pending rather than zero`() {
        compose.setContent { PhonePulseTheme { BatteryStatsCard(LiveBatteryInfo()) } }
        compose.onAllNodesWithText("待统计").assertCountEquals(3)
        compose.onAllNodesWithText("~0.0%/h").assertCountEquals(0)
    }

    @Test fun `observed zero discharge is displayed as zero`() {
        compose.setContent { PhonePulseTheme {
            BatteryStatsCard(LiveBatteryInfo(screenOnRateKnown = true, screenOffRateKnown = true))
        } }
        compose.onAllNodesWithText("~0.0%/h").assertCountEquals(2)
    }

    @Test fun `reminder toggle saves settings`() {
        var saved: BatteryMonitorSettings? = null
        compose.setContent { PhonePulseTheme { BatteryMaintenanceCard(BatteryMonitorUiState(), { saved = it }, {}) } }
        compose.onNodeWithTag("battery_alarm_switch").performClick()
        assertEquals(true, saved?.chargeAlarmEnabled)
        assertEquals(80, saved?.chargeTargetPct)
    }

    @Test fun `invalid capacity keeps dialog open and valid capacity saves`() {
        var saved: BatteryMonitorSettings? = null
        compose.setContent { PhonePulseTheme { BatteryMaintenanceCard(BatteryMonitorUiState(), { saved = it }, {}) } }
        compose.onNodeWithText("编辑设置").performClick()
        compose.onNodeWithTag("battery_design_input").performTextReplacement("200")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("设计容量需为 500–30000 mAh，或留空").assertIsDisplayed()
        assertNull(saved)
        compose.onNodeWithTag("battery_design_input").performTextReplacement("5000")
        compose.onNodeWithText("保存").performClick()
        assertEquals(5000.0, saved?.designCapacityMah)
        compose.onNodeWithTag("battery_settings_dialog").assertDoesNotExist()
    }

    @Test fun `completed cycle can be excluded`() {
        var excluded: Pair<Long, Boolean>? = null
        val cycle = BatteryCycle(id = 7, charging = true, startTime = 1, endTime = 2, lastTime = 2,
            lastElapsedMs = 2, startPct = 10, endPct = 80)
        compose.setContent { PhonePulseTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BatterySessionsCard(listOf(cycle)) { id, value -> excluded = id to value }
            }
        } }
        compose.onNodeWithTag("battery_cycle_7").performClick()
        compose.onNodeWithText("排除此记录").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(7L to true, excluded)
    }
}
