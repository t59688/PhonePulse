package com.aizeek.phonepulse.companion

import java.util.Calendar

enum class PromptAction { OPEN_HOME, OPEN_ITEMS, OPEN_JOURNAL, CLAIM_TASK, START_TRACKING }
data class CompanionPrompt(val key: String, val badge: String, val title: String, val message: String,
                           val label: String, val action: PromptAction, val target: String? = null)
data class CompanionCelebration(val journey: Journey?, val level: Int, val levelUp: Boolean)

object CompanionPrompts {
    fun celebration(state: CompanionState): CompanionCelebration? {
        val latest = state.journeys.firstOrNull()?.takeIf { it.id > state.seenJourneyId }
        return latest?.let { CompanionCelebration(it, state.level, false) }
    }

    fun acknowledge(state: CompanionState, journeyId: Long, level: Int) = state.copy(
        seenJourneyId = maxOf(state.seenJourneyId, minOf(journeyId, state.journeys.maxOfOrNull { it.id } ?: 0)),
        seenLevel = maxOf(state.seenLevel, minOf(level, state.level)))

    fun home(state: CompanionState, serviceRunning: Boolean, now: Long): CompanionPrompt {
        val latest = state.journeys.firstOrNull()
        if (latest != null && latest.id > state.seenJourneyId) {
            return CompanionPrompt("return:${latest.id}", "探险归来", "${state.name}回来了！",
                "从${latest.place}带回了故事，还有写给你的信。", "看看路上的惊喜", PromptAction.OPEN_JOURNAL)
        }
        if (!serviceRunning) return CompanionPrompt("tracking", "等你出发", "${state.name}正在等你",
            "一起出发吧。", "开启记录", PromptAction.START_TRACKING)
        if (!state.visited) return CompanionPrompt("welcome", "初次见面", "你好，我是${state.name}",
            "一起在森林里，慢慢建一个家吧。", "认识你的伙伴", PromptAction.OPEN_HOME)
        if (ForestHome.canBuild(state.home)) return CompanionPrompt("build:${state.home.stage.name}", "建家的新一步",
            "材料攒够啦", ForestHome.next(state.home)!!.invitation, "回家搭起来", PromptAction.OPEN_HOME)
        val wearable = CompanionCatalog.items.firstOrNull {
            it.kind !in listOf(ItemKind.TREASURE, ItemKind.HOME) && (state.inventory[it.id] ?: 0) > 0 && state.equipped[it.kind.name] == null
        }
        if (wearable != null) return CompanionPrompt("dress:${wearable.id}", "试试新装扮", "给旅途换个新模样",
            "试试「${wearable.name}」？",
            if (wearable.kind == ItemKind.HOME) "布置我的小窝" else "给伙伴试穿", PromptAction.OPEN_ITEMS, wearable.id)
        if (state.letters.any { !it.read }) return CompanionPrompt("mailbox", "森林来信",
            "有一封信在等你", "不着急，等你有空再拆开。", "看看来信", PromptAction.OPEN_JOURNAL)
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
