package com.aizeek.phonepulse

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aizeek.phonepulse.companion.*
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
class CompanionPromptsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `home introduces the companion and its first achievable journey`() {
        var clicked = false
        compose.setContent { PhonePulseTheme {
            CompanionHomeEntry(CompanionUiState(loaded = true), { clicked = true })
        } }
        compose.onNodeWithText("初次见面").assertIsDisplayed()
        compose.onNodeWithText("认识你的伙伴").performClick()
        assertTrue(clicked)
    }

    @Test fun `return proactively offers dress up and marks the exact presented event read`() {
        var ack: Pair<Long, Int>? = null
        var opened: CompanionPrompt? = null
        val state = CompanionState(inventory = mapOf("scarf" to 1),
            journeys = listOf(Journey(8, 0, 3_600_000, 3_600_000, "scarf", "森林", "去了一趟森林")))
        compose.setContent { PhonePulseTheme {
            CompanionCelebrationHost(CompanionUiState(state, loaded = true),
                onAcknowledge = { id, level -> ack = id to level }, onOpen = { opened = it }, onShare = {})
        } }
        compose.onNodeWithText("我回来啦！").assertIsDisplayed()
        compose.onNodeWithText("还带回了苔绿围巾").assertIsDisplayed()
        compose.onNodeWithText("去给松松试穿").performClick()
        assertEquals(8L to 1, ack)
        assertEquals("scarf", opened?.target)
    }
}
