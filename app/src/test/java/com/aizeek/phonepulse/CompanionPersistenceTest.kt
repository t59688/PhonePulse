package com.aizeek.phonepulse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.AppDatabase
import com.aizeek.phonepulse.data.ScreenSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompanionPersistenceTest {
    @Test fun `older saved inventory remains compatible when prompt history is absent`() {
        val state = CompanionState(inventory = mapOf("leaf" to 2))
        val old = org.json.JSONObject(PreferencesCompanionStore.encode(state)).apply {
            remove("visited"); remove("seenJourneyId"); remove("seenLevel")
        }
        val restored = PreferencesCompanionStore.decode(old.toString())
        assertEquals(state.inventory, restored.inventory)
        assertFalse(restored.visited)
        assertEquals(0L, restored.seenJourneyId)
        assertEquals(1, restored.seenLevel)
    }

    @Test fun `json round trip retains inventory equipment and confirmed stories`() {
        val state = CompanionState(joinedAt = 500, cursor = 7, name = "圆圆", inventory = mapOf("scarf" to 2),
            equipped = mapOf("SCARF" to "scarf"), showcase = listOf("scarf"), growth = 121,
            journeys = listOf(Journey(7, 1_000, 5_000, 4_000, "scarf", "林间", "猜测", "工作")),
            claimedTasks = setOf("rest20"))
        assertEquals(state, PreferencesCompanionStore.decode(PreferencesCompanionStore.encode(state)))
    }

    @Test fun `a failed save leaves cursor and rewards unchanged then retries once`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            var stored = CompanionState(joinedAt = now - 3_600_000)
            var fail = true
            val store = object : CompanionStore {
                override fun read() = stored
                override fun write(state: CompanionState) {
                    if (fail) error("disk full")
                    stored = state
                }
            }
            db.screenSessionDao().insertSession(ScreenSession(type = "SCREEN_OFF", startTime = now - 1_800_000,
                endTime = now - 1, durationMs = 1_799_999, dateKey = CompanionRules.day(now)))
            val repo = CompanionRepository(db.screenSessionDao(), store)
            repo.refresh()
            assertNotNull(repo.state.value.error)
            assertEquals(0L, stored.cursor)
            assertTrue(stored.inventory.isEmpty())
            fail = false
            repo.refresh()
            assertEquals(1, stored.inventory.values.sum())
            assertNull(repo.state.value.error)
            val restarted = CompanionRepository(db.screenSessionDao(), store)
            restarted.refresh()
            assertEquals(1, restarted.state.value.data.inventory.values.sum())
        } finally { db.close() }
    }

    @Test fun `new adoption skips existing history`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            db.screenSessionDao().insertSession(ScreenSession(type = "SCREEN_OFF", startTime = now - 7_200_000,
                endTime = now - 1, durationMs = 7_199_999, dateKey = CompanionRules.day(now)))
            var stored: CompanionState? = null
            val repo = CompanionRepository(db.screenSessionDao(), object : CompanionStore {
                override fun read() = stored
                override fun write(state: CompanionState) { stored = state }
            })
            repo.refresh()
            assertTrue(repo.state.value.loaded)
            assertEquals(setOf("explorer_hat", "explorer_cape"), repo.state.value.data.inventory.keys)
            assertTrue(repo.state.value.data.journeys.isEmpty())
            assertEquals(1L, repo.state.value.data.cursor)
        } finally { db.close() }
    }
}
