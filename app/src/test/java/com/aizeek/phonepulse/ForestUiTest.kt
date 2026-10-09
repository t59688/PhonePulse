package com.aizeek.phonepulse

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.ui.screens.CompanionScreen
import com.aizeek.phonepulse.ui.theme.PhonePulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForestUiTest {
    @get:Rule val compose = createComposeRule()
    private fun show(state: CompanionState = CompanionState()) {
        compose.setContent { PhonePulseTheme {
            CompanionScreen(CompanionUiState(state, loaded = true), LiveBatteryInfo(percentage = 80), true,
                onBack = {}, onEquip = {}, onShowcase = {}, onClaim = {}, onRename = {},
                onConfirm = { _, _ -> }, onObserve = {}, onShare = {}, onRetry = {})
        } }
    }
    @Test fun `new home is an empty clearing without an interior entry`() {
        show()
        compose.onNodeWithText("林间空地").assertExists()
        compose.onNodeWithText("进屋坐坐").assertDoesNotExist()
        compose.onNodeWithTag("companion_page_1").performClick()
        compose.onNodeWithText("下次回来，听我讲路上的事。").assertExists()
    }
    @Test fun `bag only shows owned discoveries`() {
        show(CompanionState(inventory = mapOf("scarf" to 1)))
        compose.onNodeWithTag("companion_page_2").performClick()
        compose.onNodeWithTag("item_scarf").assertExists()
        compose.onNodeWithTag("item_crown").assertDoesNotExist()
    }
    @Test fun `cancelled outfit preview does not consume or equip an item`() {
        show(CompanionState(inventory = mapOf("scarf" to 1)))
        compose.onNodeWithTag("companion_page_2").performClick()
        compose.onNodeWithTag("item_scarf").performClick()
        compose.onNodeWithText("试穿看看").performClick()
        compose.onNodeWithText("取消试穿").performClick()
        compose.onNodeWithText("试穿看看").assertExists()
    }
}
