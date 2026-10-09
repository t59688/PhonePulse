package com.aizeek.phonepulse.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface CompanionStore {
    fun read(): CompanionState?
    fun write(state: CompanionState)
}

class PreferencesCompanionStore(context: Context) : CompanionStore {
    private val prefs = context.getSharedPreferences("forest_companion", Context.MODE_PRIVATE)
    override fun read(): CompanionState? = prefs.getString("state", null)?.let(::decode)
    override fun write(state: CompanionState) {
        // Inventory, equipment and settlement cursor must survive together.
        check(prefs.edit().putString("state", encode(state)).commit()) { "陪伴记录保存失败，请重试" }
    }

    companion object {
        fun encode(state: CompanionState): String = JSONObject().apply {
            put("version", 1); put("joinedAt", state.joinedAt); put("cursor", state.cursor); put("name", state.name)
            put("inventory", JSONObject(state.inventory)); put("equipped", JSONObject(state.equipped))
            put("showcase", JSONArray(state.showcase)); put("growth", state.growth)
            put("totalJourneys", state.totalJourneys); put("totalRestMs", state.totalRestMs)
            put("rewardDay", state.rewardDay); put("rewardCount", state.rewardCount)
            put("taskDay", state.taskDay); put("longestRestMs", state.longestRestMs)
            put("claimedTasks", JSONArray(state.claimedTasks.toList()))
            put("visited", state.visited); put("seenJourneyId", state.seenJourneyId); put("seenLevel", state.seenLevel)
            put("journeys", JSONArray().apply { state.journeys.forEach { trip ->
                put(JSONObject().apply {
                    put("id", trip.id); put("start", trip.startTime); put("end", trip.endTime)
                    put("duration", trip.durationMs); put("item", trip.itemId ?: JSONObject.NULL)
                    put("place", trip.place); put("guess", trip.guess); put("confirmed", trip.confirmed ?: JSONObject.NULL)
                })
            } })
        }.toString()

        fun decode(raw: String): CompanionState {
            val json = JSONObject(raw)
            require(json.getInt("version") == 1) { "陪伴记录版本暂不受支持" }
            val inventory = json.getJSONObject("inventory")
            val equipped = json.getJSONObject("equipped")
            fun strings(key: String) = json.getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) } }
            val trips = json.getJSONArray("journeys")
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
                journeys = (0 until trips.length()).map { index -> trips.getJSONObject(index).let {
                    Journey(it.getLong("id"), it.getLong("start"), it.getLong("end"), it.getLong("duration"),
                        if (it.isNull("item")) null else it.getString("item"), it.getString("place"), it.getString("guess"),
                        if (it.isNull("confirmed")) null else it.getString("confirmed"))
                } }
            )
        }
    }
}
