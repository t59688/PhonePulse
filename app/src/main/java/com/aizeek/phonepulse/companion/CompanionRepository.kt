package com.aizeek.phonepulse.companion

import com.aizeek.phonepulse.data.ScreenSessionDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CompanionRepository(private val dao: ScreenSessionDao, private val store: CompanionStore) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(CompanionUiState())
    val state = mutable.asStateFlow()

    suspend fun refresh() = action {
        val now = System.currentTimeMillis()
        val previous = if (mutable.value.loaded) mutable.value.data else store.read()
            ?: CompanionState(joinedAt = now, cursor = dao.getLatestSessionIdSync() ?: 0L,
                inventory = mapOf("explorer_hat" to 1, "explorer_cape" to 1),
                equipped = mapOf("HAT" to "explorer_hat", "CAPE" to "explorer_cape"))
        val settled = CompanionRules.settle(previous, dao.getSessionsAfter(previous.cursor), now)
        val today = CompanionRules.day(now)
        val next = if (settled.taskDay == today) settled else settled.copy(
            taskDay = today, longestRestMs = 0, claimedTasks = emptySet())
        if (!mutable.value.loaded || next != previous) store.write(next)
        mutable.update { it.copy(data = next, loaded = true, error = null) }
    }

    suspend fun equip(id: String) = update { CompanionRules.equip(it, id) }
    suspend fun build(expectedStage: HomeStage) = update { state ->
        if (state.home.stage != expectedStage) return@update state
        check(ForestHome.canBuild(state.home)) { "材料还不够，等下一次旅行带回来吧。" }
        state.copy(home = ForestHome.build(state.home))
    }
    suspend fun place(id: String, slot: HomeSlot) = update { ForestHome.place(it, id, slot) }
    suspend fun removeFurniture(slot: HomeSlot) = update { it.copy(home = it.home.copy(furniture = it.home.furniture - slot.name)) }
    suspend fun readLetter(id: String) = update { it.copy(letters = it.letters.map { letter -> if (letter.id == id) letter.copy(read = true) else letter }) }
    suspend fun frame(id: String) = update { ForestHome.frame(it, id) }
    suspend fun roof(color: String) = update {
        require(color in listOf("terracotta", "moss", "slate"))
        it.copy(home = it.home.copy(roof = color))
    }
    suspend fun visit() = update { it.copy(visited = true) }
    suspend fun acknowledge(journeyId: Long, level: Int) = update { CompanionPrompts.acknowledge(it, journeyId, level) }
    suspend fun showcase(id: String) = update { CompanionRules.toggleShowcase(it, id) }
    suspend fun claim(task: String) = update { CompanionRules.claimTask(it, task, System.currentTimeMillis()) }
    suspend fun rename(name: String) = update {
        require(name.trim().length in 1..12) { "名字请保持在 1～12 个字之间" }
        it.copy(name = name.trim())
    }
    suspend fun confirm(journeyId: Long, label: String) = update { data ->
        require(label in listOf("睡眠", "工作", "放松", "其他"))
        data.copy(journeys = data.journeys.map { if (it.id == journeyId) it.copy(confirmed = label) else it })
    }

    private suspend fun update(transform: (CompanionState) -> CompanionState) = action {
        check(mutable.value.loaded) { "陪伴记录尚未载入，请重试" }
        val next = transform(mutable.value.data)
        if (next != mutable.value.data) store.write(next)
        mutable.update { it.copy(data = next, error = null) }
    }

    private suspend fun action(block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        lock.withLock {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.e("Companion", "Companion action failed", e)
                mutable.update { it.copy(error = e.message ?: "陪伴记录暂时无法更新，请重试") }
            }
        }
    }

    fun observation(text: String?, loading: Boolean = false) {
        mutable.update { it.copy(observation = text, observing = loading) }
    }
}
