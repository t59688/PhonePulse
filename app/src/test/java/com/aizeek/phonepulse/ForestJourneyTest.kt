package com.aizeek.phonepulse

import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.ScreenSession
import org.junit.Assert.*
import org.junit.Test

class ForestJourneyTest {
    private fun off(id: Long, start: Long, minutes: Long) = ScreenSession(
        id, "SCREEN_OFF", start, start + minutes * 60_000, minutes * 60_000, "1970-01-01")

    @Test fun `first rest brings a story and a letter as well as a gift`() {
        val record = off(1, 1_000, 10)
        val result = CompanionRules.settle(CompanionState(joinedAt = 1), listOf(record), record.endTime)
        assertFalse(result.journeys.single().guess.contains("可能"))
        assertTrue(result.journeys.single().rewards.size >= 3)
        assertEquals(1, result.letters.size)
        assertTrue(ForestHome.canBuild(result.home))
        assertEquals(result, CompanionRules.settle(result, listOf(record), record.endTime))
    }

    @Test fun `building starts on an empty clearing and consumes materials once`() {
        val initial = HomeState()
        assertEquals(HomeStage.CLEARING, initial.stage)
        assertEquals(initial, ForestHome.build(initial))
        val supplied = initial.copy(wood = 2, stone = 1, fiber = 1)
        val camp = ForestHome.build(supplied)
        assertEquals(HomeStage.CAMP, camp.stage)
        assertEquals(0, camp.wood)
        assertEquals(camp, ForestHome.build(camp))
    }

    @Test fun `materials advance deterministically while gifts are capped`() {
        val sessions = (1L..7L).map { off(it, it * 2_000_000, 20) }
        val result = CompanionRules.settle(CompanionState(joinedAt = 1), sessions, 20_000_000)
        assertEquals(5, result.inventory.values.sum())
        assertEquals(7, result.letters.size)
        assertTrue(result.home.wood > 0)
        assertEquals(140L, result.home.restMinutes)
        assertEquals(7, result.journeys.size)
    }

    @Test fun `long rests cap daily construction and do not punish a short interruption`() {
        val records = listOf(off(1, 1_000, 600), off(2, 40_000_000, 180))
        val result = CompanionRules.settle(CompanionState(joinedAt = 1), records, 55_000_000)
        assertEquals(240L, result.home.restMinutes)
        val short = off(3, 56_000_000, 2)
        val after = CompanionRules.settle(result, listOf(short), short.endTime)
        assertEquals(result.home, after.home)
        assertEquals(result.letters, after.letters)
    }

    @Test fun `collected letters and friend meetings survive travel history trimming`() {
        var state = CompanionState(joinedAt = 1)
        for (id in 1L..75L) {
            val record = off(id, id * 86_400_000, 30)
            state = CompanionRules.settle(state, listOf(record), record.endTime)
        }
        assertEquals(60, state.journeys.size)
        assertEquals(75, state.letters.size)
        assertEquals(75, state.letters.map { it.id }.distinct().size)
        assertTrue(state.friends.isNotEmpty())
        assertTrue(state.friends.any { it.meetings.size > 1 })
    }

    @Test fun `furniture requires owned item and available stage and replaces safely`() {
        val state = CompanionState(inventory = mapOf("plant" to 1, "lamp" to 1),
            home = HomeState(stage = HomeStage.CAMP))
        assertEquals(state, ForestHome.place(state, "lamp", HomeSlot.WALL))
        val placed = ForestHome.place(state, "plant", HomeSlot.GARDEN)
        assertEquals("plant", placed.home.furniture[HomeSlot.GARDEN.name])
        val moved = ForestHome.place(placed, "plant", HomeSlot.ENTRY)
        assertFalse(moved.home.furniture.containsKey(HomeSlot.GARDEN.name))
        assertEquals(1, moved.inventory["plant"])
    }
}
