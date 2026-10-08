package com.aizeek.phonepulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import com.aizeek.phonepulse.data.buildAppUsageTimeline
import com.aizeek.phonepulse.data.UsageTimelineEvent
import com.aizeek.phonepulse.data.UsageTimelineEventType
import com.aizeek.phonepulse.ui.components.AppUsageChart
import com.aizeek.phonepulse.util.TimeFormatter
import com.aizeek.phonepulse.ui.screens.BatteryScreen
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.BatteryRecord
import com.aizeek.phonepulse.data.UsagePeriod
import com.aizeek.phonepulse.ui.components.BatteryLevelChart
import com.aizeek.phonepulse.ui.screens.AppUsageScreen
import com.aizeek.phonepulse.ui.theme.PhonePulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StatisticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun `hour chart shows midnight range and responds to selected hour`() {
        val start = TimeFormatter.getStartOfDay(System.currentTimeMillis())
        val hour = 3_600_000L
        val timeline = buildAppUsageTimeline(start, start + 3 * hour, listOf(
            UsageTimelineEvent(start, UsageTimelineEventType.RESUME),
            UsageTimelineEvent(start + hour / 2, UsageTimelineEventType.PAUSE),
            UsageTimelineEvent(start + hour, UsageTimelineEventType.RESUME),
            UsageTimelineEvent(start + hour + hour / 4, UsageTimelineEventType.PAUSE)))
        compose.setContent { PhonePulseTheme { AppUsageChart(timeline) } }
        compose.onNodeWithText("00:00 — 03:00", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("app_usage_hour_bars").performTouchInput { click(Offset(width * 0.1f, height * 0.5f)) }
        compose.onNodeWithText("00:00–01:00：30分 0秒").assertIsDisplayed()
        compose.onNodeWithTag("app_usage_session_band").assertIsDisplayed()
    }

    @Test fun `empty usage events explain missing history`() {
        val start = TimeFormatter.getStartOfDay(System.currentTimeMillis())
        compose.setContent { PhonePulseTheme { AppUsageChart(buildAppUsageTimeline(start, start + 3_600_000, emptyList())) } }
        compose.onNodeWithText("系统暂未提供可还原的今日前台使用事件。").assertIsDisplayed()
    }

    @Test fun `battery statistics includes apps and opens detail cards`() {
        compose.setContent {
            PhonePulseTheme {
                BatteryScreen(batteryInfo = LiveBatteryInfo(), records = emptyList(),
                    usageList = listOf(AppUsageInfo("example.app", "Example", 60_000, 0)),
                    hasUsagePermission = true)
            }
        }
        compose.onNodeWithTag("battery_statistics_list").performScrollToNode(hasTestTag("app_battery_card_example.app"))
        compose.onNodeWithTag("app_battery_card_example.app").performClick()
        compose.onNodeWithTag("app_detail_screen").assertIsDisplayed()
        compose.onNodeWithText("前台使用时长").assertIsDisplayed()
        compose.onNodeWithTag("app_detail_screen").performScrollToNode(hasText("应用耗电数据"))
        compose.onNodeWithText("应用耗电数据").assertIsDisplayed()
        compose.onNodeWithText("未提供").assertIsDisplayed()
        compose.onNodeWithText("今日估算耗电").assertDoesNotExist()
        compose.onNodeWithTag("app_detail_back").performClick()
        compose.onNodeWithTag("app_detail_screen").assertDoesNotExist()
    }

    @Test fun `application activity opens its own detail without mixing periods`() {
        compose.setContent { PhonePulseTheme {
            AppUsageScreen(true, false, listOf(AppUsageInfo("example.app", "Example", 60_000, 0)),
                UsagePeriod.LAST_7_DAYS, {}, {}, {})
        } }
        compose.onNodeWithTag("app_usage_card_example.app").performScrollTo().performClick()
        compose.onNodeWithTag("app_detail_screen").assertIsDisplayed()
        compose.onNodeWithText("近 7 天").assertIsDisplayed()
        compose.onNodeWithText("前台使用时长").assertIsDisplayed()
    }

    @Test
    fun `application activity shows duration without mixing in estimated power`() {
        compose.setContent {
            PhonePulseTheme {
                AppUsageScreen(
                    hasPermission = true, isLoading = false,
                    usageList = listOf(AppUsageInfo("example.app", "Example", 60_000, 0,
                        estimatedBatteryDrainPct = 5f, estimatedMah = 225)),
                    currentPeriod = UsagePeriod.TODAY, onPeriodSelected = {},
                    onRequestPermission = {}, onRefresh = {}
                )
            }
        }
        compose.onNodeWithText("应用活跃").assertIsDisplayed()
        compose.onNodeWithText("1m").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("~5.0% (225mAh)").assertDoesNotExist()
        compose.onNodeWithText("应用总耗电", substring = true).assertDoesNotExist()
    }

    @Test
    fun `chart explains missing battery history`() {
        compose.setContent { PhonePulseTheme { BatteryLevelChart(emptyList()) } }
        compose.onNodeWithText("暂无电量记录，等待采集").assertIsDisplayed()
    }

    @Test
    fun `single sample remains visible without inventing a trend`() {
        val record = BatteryRecord(timestamp = 0, level = 42, percentage = 42,
            isCharging = false, plugType = "NONE", health = "GOOD", temperature = 25f,
            voltage = 4000, screenState = "SCREEN_ON", dateKey = "1970-01-01")
        compose.setContent { PhonePulseTheme { BatteryLevelChart(listOf(record)) } }
        compose.onNodeWithText("仅有一个采样点，继续记录后显示变化曲线").assertIsDisplayed()
        compose.onNodeWithText("42%", substring = true).assertIsDisplayed()
    }
}
