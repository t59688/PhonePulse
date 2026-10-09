package com.aizeek.phonepulse

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.ui.screens.CompanionScreen
import com.aizeek.phonepulse.ui.theme.PhonePulseTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompanionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: CompanionState = CompanionState(), onEquip: (String) -> Unit = {},
                     onShowcase: (String) -> Unit = {}, onConfirm: (Long, String) -> Unit = { _, _ -> }) {
        compose.setContent { PhonePulseTheme {
            CompanionScreen(CompanionUiState(data = state, loaded = true), LiveBatteryInfo(percentage = 80), true,
                onBack = {}, onEquip = onEquip, onShowcase = onShowcase, onClaim = {}, onRename = {},
                onConfirm = onConfirm, onObserve = {}, onShare = {}, onRetry = {})
        } }
    }

    @Test fun `locked collectible shows discovery state with no equipment action`() {
        show()
        compose.onNodeWithTag("companion_content").performScrollToNode(hasTestTag("companion_page_1"))
        compose.onNodeWithTag("companion_page_1").performClick()
        compose.onNodeWithTag("companion_content").performScrollToNode(hasTestTag("item_acorn"))
        compose.onNodeWithTag("item_acorn").performClick()
        compose.onNodeWithText("尚未发现的宝物").assertIsDisplayed()
        compose.onNodeWithText("给松松穿戴").assertDoesNotExist()
    }

    @Test fun `owned scarf can be equipped and added to showcase`() {
        var equipped: String? = null
        var showcased: String? = null
        show(CompanionState(inventory = mapOf("scarf" to 1)), { equipped = it }, { showcased = it })
        compose.onNodeWithTag("companion_content").performScrollToNode(hasTestTag("companion_page_1"))
        compose.onNodeWithTag("companion_page_1").performClick()
        compose.onNodeWithTag("companion_content").performScrollToNode(hasTestTag("item_scarf"))
        compose.onNodeWithTag("item_scarf").performClick()
        compose.onNodeWithText("给松松穿戴").performClick()
        assertEquals("scarf", equipped)
        compose.onNodeWithText("加入展示柜（0 / 6）").performClick()
        assertEquals("scarf", showcased)
    }

    @Test fun `journal observations are guesses and can be corrected`() {
        var confirmed: Pair<Long, String>? = null
        show(CompanionState(journeys = listOf(Journey(9, 0, 3_600_000, 3_600_000, "leaf", "森林", "可能在工作"))),
            onConfirm = { id, label -> confirmed = id to label })
        compose.onNodeWithTag("companion_content").performScrollToNode(hasTestTag("companion_page_2"))
        compose.onNodeWithTag("companion_page_2").performClick()
        compose.onNodeWithTag("companion_content").performScrollToNode(hasText("工作"))
        compose.onNodeWithText("工作").performClick()
        assertEquals(9L to "工作", confirmed)
        compose.onNodeWithText("可能在工作").assertExists()
        compose.onNodeWithText("依据：时段与连续熄屏时长 · 只是猜测").assertDoesNotExist()
    }
}
