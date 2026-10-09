package com.aizeek.phonepulse

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aizeek.phonepulse.companion.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run FileProvider access checks on Android: its canonical path logic assumes Unix separators. */
@RunWith(AndroidJUnit4::class)
class CompanionShareInstrumentedTest {
    @Test fun postcardGrantsImageAccessWithoutUsageOrSleepDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = CompanionState(name = "小圆", inventory = mapOf("scarf" to 1),
            showcase = listOf("scarf"), equipped = mapOf("SCARF" to "scarf"),
            journeys = listOf(Journey(1, 0, 1_000, 1_000, "scarf", "森林", "在工作", "睡眠")))
        val intent = CompanionShare.postcard(context, state)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
        assertNull(intent.getStringExtra(Intent.EXTRA_TEXT))
        val uri = intent.clipData!!.getItemAt(0).uri
        assertEquals("${context.packageName}.companion", uri.authority)
        assertEquals("content", uri.scheme)
        val image = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertNotNull(image)
        assertTrue(image.width >= 600)
        image.recycle()
        val updates = java.io.File(context.filesDir, "updates").apply { mkdirs() }
        val updateProbe = java.io.File.createTempFile("share-provider-probe-", ".bin", updates)
        try {
            updateProbe.writeBytes(byteArrayOf(1, 2, 3))
            val updateUri = FileProvider.getUriForFile(context, "${context.packageName}.updates", updateProbe)
            val bytes = context.contentResolver.openInputStream(updateUri)!!.use { it.readBytes() }
            assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
        } finally { updateProbe.delete() }
    }
}
