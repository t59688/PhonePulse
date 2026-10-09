package com.aizeek.phonepulse.companion

enum class ItemKind(val label: String) { TREASURE("纪念物"), HAT("帽子"), SCARF("围巾"), HOME("家具"), CAPE("斗篷"), HAND("随身物") }
enum class Rarity(val label: String, val color: Long) {
    COMMON("寻常", 0xFFA7C9B5), RARE("珍稀", 0xFFADC7EF), LEGENDARY("奇遇", 0xFFE8C78F)
}

data class CompanionItem(val id: String, val name: String, val kind: ItemKind, val rarity: Rarity, val story: String)

object CompanionCatalog {
    val items = listOf(
        CompanionItem("leaf", "初秋的叶子", ItemKind.TREASURE, Rarity.COMMON, "落在小路上的第一片秋天。"),
        CompanionItem("stone", "溪边圆石", ItemKind.TREASURE, Rarity.COMMON, "流水把棱角磨成了温柔的形状。"),
        CompanionItem("acorn", "橡果帽", ItemKind.HAT, Rarity.COMMON, "松鼠送来的礼物，戴上就能出发。"),
        CompanionItem("scarf", "苔绿围巾", ItemKind.SCARF, Rarity.COMMON, "森林的颜色，刚好围住一阵晚风。"),
        CompanionItem("plant", "森林盆栽", ItemKind.HOME, Rarity.COMMON, "带一点绿意回家，它会一直陪着你。"),
        CompanionItem("letter", "森林来信", ItemKind.TREASURE, Rarity.RARE, "信上写着：慢一点，也会遇见好风景。"),
        CompanionItem("moon", "月光石", ItemKind.TREASURE, Rarity.RARE, "在湖边捡到的，像藏着一小片月光。"),
        CompanionItem("flower", "铃兰花冠", ItemKind.HAT, Rarity.RARE, "风经过时，仿佛听见很轻的铃声。"),
        CompanionItem("rose_scarf", "暮色围巾", ItemKind.SCARF, Rarity.RARE, "把晚霞留在肩头，下次远行也不怕冷。"),
        CompanionItem("lamp", "蘑菇小灯", ItemKind.HOME, Rarity.RARE, "为每一次回家留一盏暖暖的灯。"),
        CompanionItem("star", "星星碎片", ItemKind.TREASURE, Rarity.LEGENDARY, "走了很远，才遇见这颗落在森林里的星星。"),
        CompanionItem("crown", "星旅王冠", ItemKind.HAT, Rarity.LEGENDARY, "献给愿意给生活留一点空白的你。"),
        CompanionItem("window", "星空窗", ItemKind.HOME, Rarity.LEGENDARY, "不用走出小窝，也能看见遥远的夜空。"),
        CompanionItem("explorer_hat", "探险帽", ItemKind.HAT, Rarity.COMMON, "一起出发的第一顶帽子。"),
        CompanionItem("explorer_cape", "麦芽斗篷", ItemKind.CAPE, Rarity.COMMON, "柔软的麦芽色，是熟悉的旅伴。"),
        CompanionItem("sage_cape", "森林斗篷", ItemKind.CAPE, Rarity.RARE, "裁缝用森林的颜色缝了一件新衣。"),
        CompanionItem("rose_cape", "晚霞斗篷", ItemKind.CAPE, Rarity.RARE, "把一抹晚霞披在肩上。"),
        CompanionItem("map", "手绘地图", ItemKind.HAND, Rarity.COMMON, "桥边的水獭画的小路，还标了一家面包店。"),
        CompanionItem("cup", "蓝陶杯", ItemKind.HOME, Rarity.COMMON, "杯底刻着一片小叶子。"),
        CompanionItem("books", "森林故事集", ItemKind.HOME, Rarity.RARE, "里面夹着朋友们留下的书签。"),
        CompanionItem("rug", "苔绿地毯", ItemKind.HOME, Rarity.COMMON, "赤脚踩上去也暖暖的。"),
        CompanionItem("basket", "野餐篮", ItemKind.HAND, Rarity.COMMON, "篮子里可以放下一整个下午。")
    )
    fun find(id: String?) = items.firstOrNull { it.id == id }
}

data class Journey(
    val id: Long, val startTime: Long, val endTime: Long, val durationMs: Long,
    val itemId: String?, val place: String, val guess: String, val confirmed: String? = null,
    val rewards: List<JourneyReward> = emptyList(), val eventId: String = "", val friendId: String? = null
)

data class CompanionState(
    val joinedAt: Long = 0, val cursor: Long = 0, val name: String = "松松",
    val inventory: Map<String, Int> = emptyMap(), val equipped: Map<String, String> = emptyMap(),
    val showcase: List<String> = emptyList(), val journeys: List<Journey> = emptyList(),
    val growth: Int = 0, val totalJourneys: Int = 0, val totalRestMs: Long = 0,
    val rewardDay: String = "", val rewardCount: Int = 0,
    val taskDay: String = "", val longestRestMs: Long = 0, val claimedTasks: Set<String> = emptySet(),
    val visited: Boolean = false, val seenJourneyId: Long = 0, val seenLevel: Int = 1,
    val home: HomeState = HomeState(), val letters: List<ForestLetter> = emptyList(),
    val friends: List<ForestFriend> = emptyList()
) {
    val level get() = 1 + growth / 100
    val growthInLevel get() = growth % 100
    val ownedCount get() = inventory.count { it.value > 0 }
}

data class CompanionUiState(
    val data: CompanionState = CompanionState(), val loaded: Boolean = false,
    val error: String? = null, val observation: String? = null, val observing: Boolean = false
)
