package com.aizeek.phonepulse

import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.ScreenSession
import org.junit.Assert.*
import org.junit.Test

class CompanionRulesTest {
    private fun off(id: Long, start: Long, minutes: Long) = ScreenSession(
        id, "SCREEN_OFF", start, start + minutes * 60_000, minutes * 60_000, "2026-10-09")

    @Test fun `rarity rises smoothly and caps after three hours`() {
        assertEquals(0.0, CompanionRules.rareChance(9 * 60_000), 0.0)
        assertTrue(CompanionRules.rareChance(60 * 60_000) > CompanionRules.rareChance(30 * 60_000))
        assertEquals(CompanionRules.rareChance(180 * 60_000), CompanionRules.rareChance(600 * 60_000), 0.0)
    }

    @Test fun `settlement merges midnight fragments and cannot reward twice`() {
        val first = off(1, 1_000, 60)
        val second = off(2, first.endTime, 70)
        val initial = CompanionState(joinedAt = 1_000)
        val result = CompanionRules.settle(initial, listOf(first, second), second.endTime)
        assertEquals(1, result.journeys.size)
        assertEquals(130 * 60_000L, result.journeys.single().durationMs)
        assertEquals(1, result.inventory.values.sum())
        assertEquals(result, CompanionRules.settle(result, listOf(first, second), second.endTime))
    }

    @Test fun `short sessions and sessions before adoption give no items`() {
        val sessions = listOf(off(1, 0, 60), off(2, 4_000_000, 9))
        val result = CompanionRules.settle(CompanionState(joinedAt = 4_000_000), sessions, 5_000_000)
        assertTrue(result.inventory.isEmpty())
    }

    @Test fun `daily cap prevents farming and rejects future records`() {
        val sessions = (1L..7L).map { off(it, it * 2_000_000, 20) }
        val result = CompanionRules.settle(CompanionState(joinedAt = 1), sessions, 20_000_000)
        assertEquals(5, result.inventory.values.sum())
        assertTrue(CompanionRules.settle(CompanionState(joinedAt = 1), sessions, 1_000).inventory.isEmpty())
    }

    @Test fun `daily rest tasks grant growth only once`() {
        val session = off(1, 1_000, 70)
        val result = CompanionRules.settle(CompanionState(joinedAt = 1), listOf(session), session.endTime)
        val claimed = CompanionRules.claimTask(result, "rest20", session.endTime)
        assertEquals(result.growth + 15, claimed.growth)
        assertEquals(claimed, CompanionRules.claimTask(claimed, "rest20", session.endTime))
        assertEquals(result, CompanionRules.claimTask(result, "invalid", session.endTime))
    }

    @Test fun `equipment is owned and one item per slot and can be removed`() {
        val state = CompanionState(inventory = mapOf("scarf" to 1, "rose_scarf" to 1, "leaf" to 1))
        assertEquals(state, CompanionRules.equip(state, "crown"))
        assertEquals(state, CompanionRules.equip(state, "leaf"))
        val equipped = CompanionRules.equip(CompanionRules.equip(state, "scarf"), "rose_scarf")
        assertEquals(mapOf("SCARF" to "rose_scarf"), equipped.equipped)
        assertTrue(CompanionRules.equip(equipped, "rose_scarf").equipped.isEmpty())
    }

    @Test fun `showcase has six unique owned items at most`() {
        val owned = CompanionCatalog.items.associate { it.id to 1 }
        var state = CompanionState(inventory = owned)
        CompanionCatalog.items.forEach { state = CompanionRules.toggleShowcase(state, it.id) }
        assertEquals(6, state.showcase.size)
        assertEquals(6, state.showcase.toSet().size)
        val first = state.showcase.first()
        assertEquals(5, CompanionRules.toggleShowcase(state, first).showcase.size)
    }
}
