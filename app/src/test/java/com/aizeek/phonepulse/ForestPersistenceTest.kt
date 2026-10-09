package com.aizeek.phonepulse

import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.ScreenSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForestPersistenceTest {
    @Test fun `repeated build request cannot consume the next stages materials`() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.aizeek.phonepulse.data.AppDatabase::class.java).build()
        try {
            var saved = CompanionState(joinedAt = 1, home = HomeState(wood = 50, stone = 50, fiber = 50))
            var failed = false
            val repository = CompanionRepository(db.screenSessionDao(), object : CompanionStore {
                override fun read() = saved
                override fun write(state: CompanionState) { if (failed) error("disk full"); saved = state }
            })
            repository.refresh()
            failed = true
            repository.build(HomeStage.CLEARING)
            assertEquals(HomeStage.CLEARING, saved.home.stage)
            assertEquals(50, saved.home.wood)
            failed = false
            repository.build(HomeStage.CLEARING)
            val built = saved
            repository.build(HomeStage.CLEARING)
            assertEquals(built, saved)
            assertEquals(HomeStage.CAMP, saved.home.stage)
            assertEquals(48, saved.home.wood)
        } finally { db.close() }
    }
    @Test fun `all travel rewards letters friends and home survive a restart`() {
        val session = ScreenSession(1, "SCREEN_OFF", 1_000, 10_801_000, 10_800_000, "1970-01-01")
        val settled = CompanionRules.settle(CompanionState(joinedAt = 1), listOf(session), session.endTime)
        val camp = settled.copy(home = ForestHome.build(settled.home))
        val foundation = camp.copy(home = ForestHome.build(camp.home))
        val cabin = foundation.copy(home = ForestHome.build(foundation.home))
        assertEquals(HomeStage.CABIN, cabin.home.stage)
        val framed = ForestHome.frame(cabin, cabin.letters.single().id)
        assertEquals(framed, PreferencesCompanionStore.decode(PreferencesCompanionStore.encode(framed)))
    }

    @Test fun `old companion preferences are not read by the new version`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("forest_companion", 0).edit().putString("state", "invalid old data").commit()
        context.getSharedPreferences("forest_rabbit_v2", 0).edit().clear().commit()
        assertNull(PreferencesCompanionStore(context).read())
    }
}
