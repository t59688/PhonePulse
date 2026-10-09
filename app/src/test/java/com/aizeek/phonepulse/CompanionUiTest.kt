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

    @Test fun `undiscovered collectibles do not turn the bag into a checklist`() {
        show()
        compose.onNodeWithTag("companion_page_2").performClick()
        compose.onNodeWithTag("item_acorn").assertDoesNotExist()
        compose.onNodeWithText("留一个位置，给下一次的小惊喜。").assertIsDisplayed()
        compose.onNodeWithText("给松松穿戴").assertDoesNotExist()
    }

    @Test fun `owned scarf requires accepting its outfit preview`() {
        var equipped: String? = null
        var showcased: String? = null
        show(CompanionState(inventory = mapOf("scarf" to 1)), { equipped = it }, { showcased = it })
        compose.onNodeWithTag("companion_page_2").performClick()
        compose.onNodeWithTag("companion_content").performScrollToNode(hasTestTag("item_scarf"))
        compose.onNodeWithTag("item_scarf").performClick()
        compose.onNodeWithText("试穿看看").performClick()
        assertNull(equipped)
        compose.onNodeWithText("就穿这件").performScrollTo().performClick()
        assertEquals("scarf", equipped)
        assertNull(showcased)
    }

    @Test fun `journey story can be read without classifying the users rest`() {
        var confirmed: Pair<Long, String>? = null
        show(CompanionState(journeys = listOf(Journey(9, 0, 3_600_000, 3_600_000, "leaf", "森林", "可能在工作"))),
            onConfirm = { id, label -> confirmed = id to label })
        compose.onNodeWithTag("companion_page_1").performClick()
        assertNull(confirmed)
        compose.onNodeWithText("可能在工作").assertExists()
        compose.onNodeWithText("依据：时段与连续熄屏时长 · 只是猜测").assertDoesNotExist()
    }
}
