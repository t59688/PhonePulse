package com.aizeek.phonepulse.companion

import java.util.Calendar

enum class PromptAction { OPEN_HOME, OPEN_ITEMS, OPEN_JOURNAL, CLAIM_TASK, START_TRACKING }
data class CompanionPrompt(val key: String, val badge: String, val title: String, val message: String,
                           val label: String, val action: PromptAction, val target: String? = null)
data class CompanionCelebration(val journey: Journey?, val level: Int, val levelUp: Boolean)

object CompanionPrompts {
    fun celebration(state: CompanionState): CompanionCelebration? {
        val latest = state.journeys.firstOrNull()?.takeIf { it.id > state.seenJourneyId && it.itemId != null }
        val levelUp = state.level > state.seenLevel
        return if (latest != null || levelUp) CompanionCelebration(latest, state.level, levelUp) else null
    }

    fun acknowledge(state: CompanionState, journeyId: Long, level: Int) = state.copy(
        seenJourneyId = maxOf(state.seenJourneyId, minOf(journeyId, state.journeys.maxOfOrNull { it.id } ?: 0)),
        seenLevel = maxOf(state.seenLevel, minOf(level, state.level)))

    fun home(state: CompanionState, serviceRunning: Boolean, now: Long): CompanionPrompt {
        val latest = state.journeys.firstOrNull()
        if (latest != null && latest.id > state.seenJourneyId) {
            val item = CompanionCatalog.find(latest.itemId)
            return CompanionPrompt("return:${latest.id}", "探险归来", "${state.name}回来了！",
                item?.let { "我带回了「${it.name}」，要一起看看吗？" } ?: "这次带回一段森林见闻，想讲给你听。",
                if (item == null) "听听旅途故事" else "看看这次的礼物",
                if (item == null) PromptAction.OPEN_JOURNAL else PromptAction.OPEN_ITEMS, item?.id)
        }
        if (state.level > state.seenLevel) return CompanionPrompt("level:${state.level}", "成长时刻",
            "我们又更熟悉一点了", "Lv. ${state.level}",
            "看看伙伴的成长", PromptAction.OPEN_HOME)
        if (state.taskDay == CompanionRules.day(now)) {
            val task = listOf("rest60" to 60, "rest20" to 20).firstOrNull {
                it.first !in state.claimedTasks && state.longestRestMs >= it.second * 60_000L
            }
            if (task != null) return CompanionPrompt("task:${state.taskDay}:${task.first}", "成长可领取",
            "休息任务完成啦", "成长 +15",
                "领取成长 +15", PromptAction.CLAIM_TASK, task.first)
        }
        if (!serviceRunning) return CompanionPrompt("tracking", "等你出发", "${state.name}正在等你",
            "一起出发吧。", "开启记录", PromptAction.START_TRACKING)
        if (!state.visited) return CompanionPrompt("welcome", "初次见面", "你好，我是${state.name}",
            "很高兴见到你。", "认识你的伙伴", PromptAction.OPEN_HOME)
        val wearable = CompanionCatalog.items.firstOrNull {
            it.kind != ItemKind.TREASURE && (state.inventory[it.id] ?: 0) > 0 && state.equipped[it.kind.name] == null
        }
        if (wearable != null) return CompanionPrompt("dress:${wearable.id}", "试试新装扮", "给旅途换个新模样",
            "试试「${wearable.name}」？",
            if (wearable.kind == ItemKind.HOME) "布置我的小窝" else "给伙伴试穿", PromptAction.OPEN_ITEMS, wearable.id)
        if (state.ownedCount > 0 && state.showcase.isEmpty()) return CompanionPrompt("showcase", "分享旅行印记",
            "把喜欢的收获摆出来", "这份风景，想和朋友分享。", "挑选收藏", PromptAction.OPEN_ITEMS)
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..10 -> "早上好，今天也陪着你" to "今天想去哪里？"
            in 11..17 -> "忙完这一段，歇一会吧" to "坐一会吧。"
            else -> "今天辛苦啦，我在这里" to "晚安，好梦。"
        }
        return CompanionPrompt("company", "安静陪伴", greeting.first, greeting.second,
            "和${state.name}打个招呼", PromptAction.OPEN_HOME)
    }
}
