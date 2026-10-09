package com.aizeek.phonepulse

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.companion.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompanionShareTest {
    @Test fun `postcard renders owned accessories and is confined to the share cache`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = CompanionState(name = "小圆", inventory = mapOf("scarf" to 1),
            showcase = listOf("scarf"), equipped = mapOf("SCARF" to "scarf"),
            journeys = listOf(Journey(1, 0, 1_000, 1_000, "scarf", "森林", "在工作", "睡眠")))
        val file = CompanionShare.createPostcardFile(context, state)
        assertEquals(java.io.File(context.cacheDir, "companion_share").canonicalPath, file.parentFile!!.canonicalPath)
        val image = file.inputStream().use { BitmapFactory.decodeStream(it) }
        assertNotNull(image)
        assertTrue(image.width >= 600)
        image.recycle()
    }

    @Test fun `unowned item cannot be exported as a personal collectible`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            CompanionShare.createPostcardFile(context, CompanionState(), "crown")
            fail("expected unowned item rejection")
        } catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("已获得")) }
    }

    @Test fun `another share never overwrites a recipients previous postcard`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = CompanionShare.createPostcardFile(context, CompanionState(name = "松松"))
        val original = first.readBytes()
        val second = CompanionShare.createPostcardFile(context, CompanionState(name = "圆圆"))
        assertNotEquals(first.canonicalPath, second.canonicalPath)
        assertArrayEquals(original, first.readBytes())
    }
}
