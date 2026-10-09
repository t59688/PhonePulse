package com.aizeek.phonepulse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aizeek.phonepulse.companion.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForestArtworkInstrumentedTest {
    @Test fun allCollectiblesHaveVisibleArtwork() {
        CompanionCatalog.items.forEach { item ->
            val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            try {
                CompanionArtwork.item(Canvas(bitmap), item.id, 160f)
                val pixels = IntArray(160 * 160)
                bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 160)
                assertTrue("${item.id} must have visible artwork", pixels.count { Color.alpha(it) > 0 } > 300)
                if (item.id == "books") assertTrue("book stack must include its bottom spine",
                    pixels.slice(100 * 160 until 120 * 160).count { Color.alpha(it) > 0 } > 300)
            } finally { bitmap.recycle() }
        }
    }
    @Test fun svgRabbitAndEveryHouseStageRenderOnAndroid() {
        val bitmap = Bitmap.createBitmap(400, 420, Bitmap.Config.ARGB_8888)
        try {
            val state = CompanionState(equipped = mapOf("HAT" to "explorer_hat", "CAPE" to "explorer_cape"))
            HomeStage.entries.forEach { stage ->
                ForestArtwork.home(Canvas(bitmap), 400f, 420f, state.copy(home = HomeState(stage = stage)), night = false)
                assertNotEquals(Color.TRANSPARENT, bitmap.getPixel(200, 300))
            }
        } finally { bitmap.recycle() }
    }
}
