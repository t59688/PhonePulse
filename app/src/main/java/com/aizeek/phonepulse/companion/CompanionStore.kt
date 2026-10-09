package com.aizeek.phonepulse.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface CompanionStore {
    fun read(): CompanionState?
    fun write(state: CompanionState)
}

class PreferencesCompanionStore(context: Context) : CompanionStore {
    private val prefs = context.getSharedPreferences("forest_rabbit_v2", Context.MODE_PRIVATE)
    override fun read(): CompanionState? = prefs.getString("state", null)?.let(::decode)
    override fun write(state: CompanionState) {
        // Inventory, equipment and settlement cursor must survive together.
        check(prefs.edit().putString("state", encode(state)).commit()) { "陪伴记录保存失败，请重试" }
    }

    companion object {
        fun encode(state: CompanionState): String = JSONObject().apply {
            put("version", 2); put("joinedAt", state.joinedAt); put("cursor", state.cursor); put("name", state.name)
            put("inventory", JSONObject(state.inventory)); put("equipped", JSONObject(state.equipped))
            put("showcase", JSONArray(state.showcase)); put("growth", state.growth)
            put("totalJourneys", state.totalJourneys); put("totalRestMs", state.totalRestMs)
            put("rewardDay", state.rewardDay); put("rewardCount", state.rewardCount)
            put("taskDay", state.taskDay); put("longestRestMs", state.longestRestMs)
            put("claimedTasks", JSONArray(state.claimedTasks.toList()))
            put("visited", state.visited); put("seenJourneyId", state.seenJourneyId); put("seenLevel", state.seenLevel)
            put("home", JSONObject().apply {
                val home = state.home
                put("stage", home.stage.name); put("wood", home.wood); put("stone", home.stone); put("fiber", home.fiber)
                put("restMinutes", home.restMinutes); put("day", home.materialDay); put("daily", home.dailyMinutes)
                put("furniture", JSONObject(home.furniture)); put("roof", home.roof); put("picture", home.wallPicture ?: JSONObject.NULL)
            })
            put("letters", JSONArray().apply { state.letters.forEach { letter -> put(JSONObject().apply {
                put("id", letter.id); put("journey", letter.journeyId); put("place", letter.placeId)
                put("title", letter.title); put("message", letter.message); put("time", letter.receivedAt)
                put("artwork", letter.artwork); put("read", letter.read)
            }) } })
            put("friends", JSONArray().apply { state.friends.forEach { friend -> put(JSONObject().apply {
                put("id", friend.id); put("meetings", JSONArray().apply { friend.meetings.forEach { meeting -> put(JSONObject().apply {
                    put("journey", meeting.journeyId); put("time", meeting.time); put("story", meeting.story); put("visiting", meeting.visiting)
                }) } })
            }) } })
            put("journeys", JSONArray().apply { state.journeys.forEach { trip ->
                put(JSONObject().apply {
                    put("id", trip.id); put("start", trip.startTime); put("end", trip.endTime)
                    put("duration", trip.durationMs); put("item", trip.itemId ?: JSONObject.NULL)
                    put("place", trip.place); put("guess", trip.guess); put("confirmed", trip.confirmed ?: JSONObject.NULL)
                    put("event", trip.eventId); put("friend", trip.friendId ?: JSONObject.NULL)
                    put("rewards", JSONArray().apply { trip.rewards.forEach { reward -> put(JSONObject().apply {
                        put("kind", reward.kind.name); put("reference", reward.reference); put("title", reward.title)
                    }) } })
                })
            } })
        }.toString()

        fun decode(raw: String): CompanionState {
            val json = JSONObject(raw)
            require(json.getInt("version") == 2) { "陪伴记录版本暂不受支持" }
            val inventory = json.getJSONObject("inventory")
            val equipped = json.getJSONObject("equipped")
            fun strings(key: String) = json.getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) } }
            val trips = json.getJSONArray("journeys")
            val home = json.getJSONObject("home")
            val furniture = home.getJSONObject("furniture")
            fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map { array.getJSONObject(it) }
            return CompanionState(
                joinedAt = json.getLong("joinedAt"), cursor = json.getLong("cursor"), name = json.getString("name"),
                inventory = inventory.keys().asSequence().associateWith { inventory.getInt(it) },
                equipped = equipped.keys().asSequence().associateWith { equipped.getString(it) },
                showcase = strings("showcase"), growth = json.getInt("growth"),
                totalJourneys = json.getInt("totalJourneys"), totalRestMs = json.getLong("totalRestMs"),
                rewardDay = json.getString("rewardDay"), rewardCount = json.getInt("rewardCount"),
                taskDay = json.getString("taskDay"), longestRestMs = json.getLong("longestRestMs"),
                claimedTasks = strings("claimedTasks").toSet(),
                visited = json.optBoolean("visited", false), seenJourneyId = json.optLong("seenJourneyId", 0),
                seenLevel = json.optInt("seenLevel", 1),
                home = HomeState(HomeStage.valueOf(home.getString("stage")), home.getInt("wood"), home.getInt("stone"),
                    home.getInt("fiber"), home.getLong("restMinutes"), home.getString("day"), home.getLong("daily"),
                    furniture.keys().asSequence().associateWith { furniture.getString(it) }, home.getString("roof"),
                    if (home.isNull("picture")) null else home.getString("picture")),
                letters = objects(json.getJSONArray("letters")).map { ForestLetter(it.getString("id"), it.getLong("journey"),
                    it.getString("place"), it.getString("title"), it.getString("message"), it.getLong("time"), it.getInt("artwork"), it.getBoolean("read")) },
                friends = objects(json.getJSONArray("friends")).map { friend -> ForestFriend(friend.getString("id"),
                    objects(friend.getJSONArray("meetings")).map { FriendMeeting(it.getLong("journey"), it.getLong("time"), it.getString("story"), it.getBoolean("visiting")) }) },
                journeys = (0 until trips.length()).map { index -> trips.getJSONObject(index).let {
                    Journey(it.getLong("id"), it.getLong("start"), it.getLong("end"), it.getLong("duration"),
                        if (it.isNull("item")) null else it.getString("item"), it.getString("place"), it.getString("guess"),
                        if (it.isNull("confirmed")) null else it.getString("confirmed"),
                        objects(it.getJSONArray("rewards")).map { reward -> JourneyReward(RewardKind.valueOf(reward.getString("kind")), reward.getString("reference"), reward.getString("title")) },
                        it.getString("event"), if (it.isNull("friend")) null else it.getString("friend"))
                } }
            )
        }
    }
}
