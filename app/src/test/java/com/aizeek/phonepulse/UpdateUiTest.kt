package com.aizeek.phonepulse

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.ui.components.UpdateHost
import com.aizeek.phonepulse.ui.components.UpdateEntry
import com.aizeek.phonepulse.ui.components.UpdateDialog
import com.aizeek.phonepulse.ui.components.CompanionCelebrationHost
import com.aizeek.phonepulse.companion.CompanionState
import com.aizeek.phonepulse.companion.CompanionUiState
import com.aizeek.phonepulse.companion.Journey
import com.aizeek.phonepulse.ui.theme.PhonePulseTheme
import com.aizeek.phonepulse.update.GitHubRelease
import com.aizeek.phonepulse.update.ReleaseVersion
import com.aizeek.phonepulse.update.UpdateRepository
import com.aizeek.phonepulse.update.UpdateState
import com.aizeek.phonepulse.update.UpdatePhase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateUiTest {
    @get:Rule val compose = createComposeRule()
    private val release = GitHubRelease(ReleaseVersion(1, 1, 0), "新版本", "更新说明", "phonepulse-1.1.0-release.apk",
        "https://github.com/t59688/PhonePulse/releases/download/v1.1.0/phonepulse-1.1.0-release.apk", 100)

    @Test fun `finding update prompts consent and remind later keeps badge without downloading`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(UpdateRepository.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        val repository = UpdateRepository(context) { release }
        repository.check()
        compose.setContent {
            PhonePulseTheme {
                Box(Modifier.fillMaxSize()) {
                    UpdateHost(repository, false, {}, 0)
                    UpdateEntry(repository) {}
                }
            }
        }
        compose.onNodeWithText("下载更新").assertIsDisplayed()
        assertEquals(0, shadowOf(context.getSystemService(DownloadManager::class.java)).requestCount)
        compose.onNodeWithText("稍后").performClick()
        compose.onNodeWithTag("update_dialog").assertDoesNotExist()
        compose.onNodeWithTag("update_badge", useUnmergedTree = true).assertIsDisplayed()
        Unit
    }

    @Test fun `download progress supports cancellation`() {
        var cancellations = 0
        compose.setContent { PhonePulseTheme {
            UpdateDialog(UpdateState(UpdatePhase.DOWNLOADING, release, 40, 100), true,
                {}, {}, {}, { cancellations++ }, {})
        } }
        compose.onNodeWithText("取消下载").performClick()
        assertEquals(1, cancellations)
    }

    @Test fun `automatic update defers unread companion return until dismissed`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(UpdateRepository.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        val repository = UpdateRepository(context) { release }
        repository.check()
        val companion = CompanionUiState(CompanionState(inventory = mapOf("scarf" to 1),
            journeys = listOf(Journey(8, 0, 3_600_000, 3_600_000, "scarf", "森林", "去了一趟森林"))), loaded = true)
        compose.setContent { PhonePulseTheme {
            val visible = UpdateHost(repository, false, {}, 0)
            CompanionCelebrationHost(companion, { _, _ -> }, {}, {}, enabled = !visible)
        } }
        compose.onNodeWithTag("update_dialog").assertIsDisplayed()
        compose.onNodeWithTag("companion_celebration").assertDoesNotExist()
        compose.onNodeWithText("稍后").performClick()
        compose.onNodeWithTag("update_dialog").assertDoesNotExist()
        compose.onNodeWithText("我回来啦！").assertIsDisplayed()
        Unit
    }

    @Test fun `verified update offers system installation`() {
        var installations = 0
        compose.setContent { PhonePulseTheme {
            UpdateDialog(UpdateState(UpdatePhase.READY, release), true,
                {}, {}, {}, {}, { installations++ })
        } }
        compose.onNodeWithText("安装").performClick()
        assertEquals(1, installations)
    }
}
