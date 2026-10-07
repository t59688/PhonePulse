package com.aizeek.phonepulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
