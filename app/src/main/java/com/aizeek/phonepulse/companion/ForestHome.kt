package com.aizeek.phonepulse.companion

enum class HomeStage(val title: String) {
    CLEARING("林间空地"), CAMP("小小营地"), FOUNDATION("木屋地基"), CABIN("森林木屋"), COZY("温暖的家")
}
enum class HomeSlot(val title: String, val minimum: HomeStage) {
    ENTRY("门口", HomeStage.CAMP), GARDEN("庭院", HomeStage.CAMP),
    TABLE("桌面", HomeStage.CABIN), WINDOW("窗边", HomeStage.CABIN), WALL("墙面", HomeStage.CABIN)
}
data class HomeState(
    val stage: HomeStage = HomeStage.CLEARING, val wood: Int = 0, val stone: Int = 0, val fiber: Int = 0,
    val restMinutes: Long = 0, val materialDay: String = "", val dailyMinutes: Long = 0,
    val furniture: Map<String, String> = emptyMap(), val roof: String = "terracotta", val wallPicture: String? = null
)
data class BuildPlan(val stage: HomeStage, val wood: Int, val stone: Int, val fiber: Int, val invitation: String)

object ForestHome {
    fun next(home: HomeState): BuildPlan? = when (home.stage) {
        HomeStage.CLEARING -> BuildPlan(HomeStage.CAMP, 2, 1, 1, "搭一个遮雨的小营地")
        HomeStage.CAMP -> BuildPlan(HomeStage.FOUNDATION, 10, 5, 5, "铺地板，立起木屋的柱子")
        HomeStage.FOUNDATION -> BuildPlan(HomeStage.CABIN, 24, 12, 12, "装上屋顶，让这里成为家")
        HomeStage.CABIN -> BuildPlan(HomeStage.COZY, 24, 12, 12, "修好小径，给朋友留个座位")
        HomeStage.COZY -> null
    }
    fun canBuild(home: HomeState): Boolean = next(home)?.let {
        home.wood >= it.wood && home.stone >= it.stone && home.fiber >= it.fiber
    } ?: false

    fun build(home: HomeState): HomeState {
        val plan = next(home) ?: return home
        if (!canBuild(home)) return home
        return home.copy(stage = plan.stage, wood = home.wood - plan.wood,
            stone = home.stone - plan.stone, fiber = home.fiber - plan.fiber)
    }

    fun gather(home: HomeState, minutes: Long, date: String): HomeState {
        val daily = if (home.materialDay == date) home.dailyMinutes else 0
        val earned = minutes.coerceIn(0, 180).coerceAtMost((240 - daily).coerceAtLeast(0))
        // The remainder carries between trips; short trips never lose construction progress.
        val units = ((home.restMinutes + earned) / 10 - home.restMinutes / 10).toInt()
        return home.copy(wood = home.wood + units * 2, stone = home.stone + units, fiber = home.fiber + units,
            restMinutes = home.restMinutes + earned, materialDay = date, dailyMinutes = daily + earned)
    }

    fun place(state: CompanionState, itemId: String, slot: HomeSlot): CompanionState {
        val item = CompanionCatalog.find(itemId) ?: return state
        if (item.kind != ItemKind.HOME || (state.inventory[itemId] ?: 0) <= 0 || state.home.stage < slot.minimum) return state
        val placements = state.home.furniture.filterValues { it != itemId } + (slot.name to itemId)
        return state.copy(home = state.home.copy(furniture = placements))
    }

    fun frame(state: CompanionState, letterId: String): CompanionState {
        if (state.home.stage < HomeStage.CABIN || state.letters.none { it.id == letterId }) return state
        return state.copy(home = state.home.copy(wallPicture = letterId))
    }
}
