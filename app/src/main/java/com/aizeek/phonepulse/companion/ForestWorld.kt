package com.aizeek.phonepulse.companion

import kotlin.random.Random

enum class RewardKind { ITEM, STORY, POSTCARD, SCENERY, FRIEND, MATERIAL }
data class JourneyReward(val kind: RewardKind, val reference: String, val title: String)
data class ForestLetter(val id: String, val journeyId: Long, val placeId: String, val title: String,
                        val message: String, val receivedAt: Long, val artwork: Int, val read: Boolean = false)
data class FriendMeeting(val journeyId: Long, val time: Long, val story: String, val visiting: Boolean = false)
data class ForestFriend(val id: String, val meetings: List<FriendMeeting>)
data class FriendDefinition(val id: String, val name: String, val species: String, val description: String)
data class ForestPlace(val id: String, val name: String, val events: List<String>, val captions: List<String>, val friendId: String)

object ForestWorld {
    val friends = listOf(
        FriendDefinition("pip", "笃笃", "啄木鸟", "会修屋顶，也爱收集树木的声音。"),
        FriendDefinition("otto", "阿沐", "水獭", "住在石桥旁，记得每条溪流的方向。"),
        FriendDefinition("hazel", "栗栗", "松鼠", "总把惊喜藏在最意想不到的地方。")
    )
    val places = listOf(
        ForestPlace("trail", "蕨叶小径", listOf(
            "我沿着蕨叶走了一小段路。露珠落在帽檐上，像搭了一班免费的车。",
            "栗栗把一粒橡果当成了我的行李。我说它太小了，她又认真挑了一粒更大的。",
            "路边的树桩上长了一圈小蘑菇。我坐在旁边，听风讲完了一个故事。",
            "树叶盖住了路标。栗栗爬上枝头指路，还特意提醒我别踩到她的新仓库。"),
            listOf("蕨叶上的早晨", "小径尽头的光"), "hazel"),
        ForestPlace("creek", "水獭石桥", listOf(
            "阿沐在桥下修一艘叶子船。我们放它顺流而下，看它平安通过了三颗石头。",
            "我在桥边听阿沐唱歌。唱到第二段时，溪水好像也跟着轻了下来。",
            "桥上有人留下了一杯薄荷茶。阿沐说，这是给每个迷路旅人的。",
            "阿沐教我辨认圆石上的水纹。原来一颗石头，也记得很远以前的雨。"),
            listOf("石桥下的溪流", "暮色里的小船"), "otto"),
        ForestPlace("mushroom", "蘑菇雨林", listOf(
            "雨停之前，我躲在一朵大蘑菇下。笃笃路过，把两片干叶子留给我垫脚。",
            "笃笃敲了三下树干，说那是森林里的问好。我照着敲，惊醒了一只打盹的甲虫。",
            "蘑菇伞面上滚着雨珠。我没忍住碰了一下，它们排着队滑进了草丛。",
            "我和笃笃找到了一截空心木。风穿过去的时候，竟然像一支很低很低的笛子。"),
            listOf("雨后的蘑菇伞", "苔藓上的小灯"), "pip"),
        ForestPlace("meadow", "风铃草地", listOf(
            "草地上有一块适合野餐的平石。栗栗分了我半块饼干，另一半她留给了明天。",
            "我躺在风铃草旁看云。一朵像帽子，一朵像我们的屋顶，还有一朵什么都不像。",
            "栗栗想给每一朵花取名字。取到第七朵时，我们决定先休息一下。",
            "一阵风吹过，花朵全朝同一个方向点头。我也点了一下，觉得自己被欢迎了。"),
            listOf("草地上的午后", "风铃草与白云"), "hazel"),
        ForestPlace("lake", "月光湖畔", listOf(
            "湖水很安静。阿沐给我看一颗发亮的圆石，说那是月亮忘在岸边的纽扣。",
            "我们数了很久的星星，数到一半就忘了。阿沐说，喜欢它们不需要数清。",
            "芦苇轻轻摇晃，湖里有另一片天空。我坐到月亮爬上树梢才想起回家。",
            "阿沐在沙地画了一条鱼。浪过来把它带走了，我们都觉得它一定游得很好。"),
            listOf("湖中的月亮", "芦苇间的星光"), "otto"),
        ForestPlace("ridge", "松风山坡", listOf(
            "笃笃带我爬上山坡。从这里看，森林像一床软被子，我们的家藏在里面。",
            "山坡上捡到一根漂亮的羽毛。笃笃说，那也许是一封还没有写字的信。",
            "松树的影子慢慢变长。我和笃笃商量，回去给门口装一盏温暖的小灯。",
            "云从山坡旁经过，我举起帽子向它打招呼。笃笃笑着说，它下次会带雨来。"),
            listOf("松风吹过山坡", "远处森林的家"), "pip")
    )
    fun friend(id: String) = friends.firstOrNull { it.id == id }
    fun place(id: String) = places.firstOrNull { it.id == id } ?: places.first()

    fun choose(state: CompanionState, minutes: Long, random: Random): Pair<ForestPlace, Int> {
        val reachable = if (minutes < 30) places.take(3) else places
        val unused = reachable.filter { place -> state.journeys.none { it.eventId.startsWith("${place.id}:") } }
        val place = (unused.ifEmpty { reachable.filter { it.name != state.journeys.firstOrNull()?.place }.ifEmpty { reachable } }).random(random)
        val seen = state.journeys.filter { it.eventId.startsWith("${place.id}:") }.map { it.eventId }.toSet()
        val events = place.events.indices.filter { "${place.id}:$it" !in seen }.ifEmpty { place.events.indices.toList() }
        return place to events.random(random)
    }

    fun letter(recordId: Long, place: ForestPlace, event: Int, endTime: Long) = ForestLetter(
        "letter:$recordId", recordId, place.id, place.captions[event % 2], place.events[event], endTime, event % 2)

    fun meet(state: CompanionState, place: ForestPlace, recordId: Long, endTime: Long): List<ForestFriend> {
        val previous = state.friends.firstOrNull { it.id == place.friendId }
        val definition = friend(place.friendId)!!
        val visit = previous != null && state.home.stage >= HomeStage.CABIN && previous.meetings.size % 3 == 2
        val story = when {
            visit -> "${definition.name}来家里坐了坐，还在窗边留下了一片漂亮的叶子。"
            previous != null -> "又遇见了${definition.name}。我们聊起上次走过的路，约好下次再见。"
            else -> "认识了${definition.species}${definition.name}。${definition.description}"
        }
        val updated = ForestFriend(place.friendId, (previous?.meetings.orEmpty() + FriendMeeting(recordId, endTime, story, visit)))
        return state.friends.filterNot { it.id == place.friendId } + updated
    }
}
