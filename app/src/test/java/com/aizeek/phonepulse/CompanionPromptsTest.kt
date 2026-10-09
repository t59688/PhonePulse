package com.aizeek.phonepulse

import com.aizeek.phonepulse.companion.*
import org.junit.Assert.*
import org.junit.Test

class CompanionPromptsTest {
    private val now = 10_000_000L
    private val trip = Journey(8, 1_000, 3_601_000, 3_600_000, "scarf", "森林", "休息了一会")

    @Test fun `unread return takes priority and dismiss survives reopening`() {
        val state = CompanionState(journeys = listOf(trip), inventory = mapOf("scarf" to 1))
        assertEquals("return:8", CompanionPrompts.home(state, true, now).key)
        assertNotNull(CompanionPrompts.celebration(state))
        val read = CompanionPrompts.acknowledge(state, 8, 1)
        assertNull(CompanionPrompts.celebration(read))
        assertNotEquals("return:8", CompanionPrompts.home(read, true, now).key)
    }

    @Test fun `construction is offered instead of daily growth claims`() {
        val state = CompanionState(visited = true, home = HomeState(wood = 2, stone = 1, fiber = 1))
        val prompt = CompanionPrompts.home(state, true, now)
        assertEquals(PromptAction.OPEN_HOME, prompt.action)
        assertEquals("build:CLEARING", prompt.key)
        assertNotEquals(PromptAction.CLAIM_TASK, prompt.action)
    }

    @Test fun `onboarding tracking dress up and sharing prompts match actual state`() {
        assertEquals(PromptAction.START_TRACKING, CompanionPrompts.home(CompanionState(), false, now).action)
        assertEquals("welcome", CompanionPrompts.home(CompanionState(), true, now).key)
        val owned = CompanionState(visited = true, inventory = mapOf("scarf" to 1))
        assertEquals("dress:scarf", CompanionPrompts.home(owned, true, now).key)
        val dressed = CompanionRules.equip(owned, "scarf")
        assertEquals("company", CompanionPrompts.home(dressed, true, now).key)
    }

    @Test fun `acknowledging an old return never consumes a newer return`() {
        val state = CompanionState(growth = 110, seenLevel = 1, journeys = listOf(trip), seenJourneyId = 8)
        assertNull(CompanionPrompts.celebration(state))
        val read = CompanionPrompts.acknowledge(state, 8, 2)
        assertNull(CompanionPrompts.celebration(read))
        val newer = read.copy(journeys = listOf(trip.copy(id = 9)))
        assertNotNull(CompanionPrompts.celebration(newer))
        assertEquals(8L, CompanionPrompts.acknowledge(newer, 8, 2).seenJourneyId)
    }

    @Test fun `a story without an item still returns once and stays acknowledged`() {
        val state = CompanionState(journeys = listOf(trip.copy(itemId = null)))
        assertNotNull(CompanionPrompts.celebration(state))
        assertNull(CompanionPrompts.celebration(CompanionPrompts.acknowledge(state, 8, 1)))
    }
}
