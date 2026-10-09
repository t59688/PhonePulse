package com.aizeek.phonepulse.companion

import com.aizeek.phonepulse.data.ScreenSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object CompanionRules {
    const val MIN_TRIP_MS = 10 * 60_000L
    const val DAILY_REWARDS = 5
    fun day(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))

    fun rareChance(durationMs: Long): Double = if (durationMs < MIN_TRIP_MS) 0.0 else
        0.08 + 0.47 * ((durationMs - MIN_TRIP_MS).toDouble() / (170 * 60_000L)).coerceIn(0.0, 1.0)

    fun place(durationMs: Long) = when {
        durationMs >= 180 * 60_000 -> "星光湖畔"
        durationMs >= 90 * 60_000 -> "雾林深处"
        durationMs >= 30 * 60_000 -> "苔藓森林"
        else -> "林间小路"
    }

    fun guess(startTime: Long, durationMs: Long): String {
        val hour = Calendar.getInstance().apply { timeInMillis = startTime }.get(Calendar.HOUR_OF_DAY)
        return when {
            (hour >= 21 || hour < 5) && durationMs >= 180 * 60_000 -> "你可能睡了个好觉，我去湖边看了看星星。"
            hour in 8..18 && durationMs >= 60 * 60_000 -> "这段时间很安静，你可能在忙工作，也可能出去走走了。"
            durationMs >= 30 * 60_000 -> "你给生活留了一点空白，我也遇见了一点新风景。"
            else -> "短短的休息，也足够我沿着小路走一走。"
        }
    }

    fun settle(state: CompanionState, records: List<ScreenSession>, now: Long): CompanionState {
        val fresh = records.filter { it.id > state.cursor && it.endTime <= now }
            .sortedWith(compareBy<ScreenSession> { it.startTime }.thenBy { it.id })
        if (fresh.isEmpty()) return state
        var result = state
        val groups = mutableListOf<ScreenSession>()
        for (session in fresh) {
            if (session.type != "SCREEN_OFF" || session.startTime < state.joinedAt ||
                session.endTime <= session.startTime || session.durationMs <= 0) continue
            val previous = groups.lastOrNull()
            // ScreenStateHolder splits one continuous session at midnight.
            if (previous != null && previous.endTime == session.startTime) {
                groups[groups.lastIndex] = previous.copy(id = maxOf(previous.id, session.id),
                    endTime = session.endTime, durationMs = session.endTime - previous.startTime)
            } else groups += session.copy(durationMs = session.endTime - session.startTime)
        }
        for (session in groups) {
            if (session.durationMs < MIN_TRIP_MS) continue
            val date = day(session.endTime)
            val count = if (result.rewardDay == date) result.rewardCount else 0
            val longest = if (result.taskDay == date) result.longestRestMs else 0L
            val random = Random(session.startTime xor session.endTime xor state.joinedAt)
            val rarityRoll = random.nextDouble()
            val chance = rareChance(session.durationMs)
            val rarity = when {
                rarityRoll < chance * 0.16 -> Rarity.LEGENDARY
                rarityRoll < chance -> Rarity.RARE
                else -> Rarity.COMMON
            }
            val pool = CompanionCatalog.items.filter { it.rarity == rarity }
            val item = if (count < DAILY_REWARDS) pool[random.nextInt(pool.size)] else null
            result = result.copy(
                inventory = if (item == null) result.inventory else result.inventory +
                    (item.id to ((result.inventory[item.id] ?: 0) + 1)),
                journeys = (listOf(Journey(session.id, session.startTime, session.endTime, session.durationMs,
                    item?.id, place(session.durationMs), guess(session.startTime, session.durationMs))) + result.journeys).take(60),
                growth = result.growth + if (item != null) 10 else 0,
                totalJourneys = result.totalJourneys + 1, totalRestMs = result.totalRestMs + session.durationMs,
                rewardDay = date, rewardCount = count + if (item != null) 1 else 0,
                taskDay = date, longestRestMs = maxOf(longest, session.durationMs),
                claimedTasks = if (result.taskDay == date) result.claimedTasks else emptySet()
            )
        }
        return result.copy(cursor = maxOf(state.cursor, fresh.maxOf { it.id }))
    }

    fun claimTask(state: CompanionState, task: String, now: Long): CompanionState {
        val target = when (task) { "rest20" -> 20 * 60_000L; "rest60" -> 60 * 60_000L; else -> return state }
        if (state.taskDay != day(now) || state.longestRestMs < target || task in state.claimedTasks) return state
        return state.copy(growth = state.growth + 15, claimedTasks = state.claimedTasks + task)
    }

    fun equip(state: CompanionState, itemId: String): CompanionState {
        val item = CompanionCatalog.find(itemId) ?: return state
        if ((state.inventory[itemId] ?: 0) <= 0 || item.kind == ItemKind.TREASURE) return state
        val slot = item.kind.name
        return state.copy(equipped = if (state.equipped[slot] == itemId) state.equipped - slot else state.equipped + (slot to itemId))
    }

    fun toggleShowcase(state: CompanionState, id: String): CompanionState {
        if ((state.inventory[id] ?: 0) <= 0) return state
        return when {
            id in state.showcase -> state.copy(showcase = state.showcase - id)
            state.showcase.size < 6 -> state.copy(showcase = state.showcase + id)
            else -> state
        }
    }
}
