package com.aizeek.phonepulse.companion

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object CompanionShare {
    suspend fun postcard(context: Context, state: CompanionState, itemId: String? = null): Intent {
        val file = createPostcardFile(context, state, itemId)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.companion", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("森林明信片", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun createPostcardFile(context: Context, state: CompanionState, itemId: String? = null): File = withContext(Dispatchers.IO) {
        val selected = CompanionCatalog.find(itemId)
        require(itemId == null || selected != null && (state.inventory[itemId] ?: 0) > 0) { "只能分享已获得的物品" }
        val bitmap = Bitmap.createBitmap(900, 1250, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(16, 29, 35))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun text(value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
                p.color = color; p.textSize = size
                p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                canvas.drawText(value, x, y, p)
            }
            val cream = Color.rgb(242, 232, 216); val muted = Color.rgb(173, 189, 185)
            val sage = Color.rgb(182, 211, 179)
            text("PHONEPULSE  /  FOREST POSTCARD", 60f, 75f, 21f, sage)
            text("${state.name}的森林小窝", 60f, 148f, 44f, cream, true)
            canvas.save(); canvas.translate(0f, 230f)
            CompanionArtwork.scene(canvas, 900f, 590f, state)
            canvas.restore()
            text("Lv. ${state.level}    ·    ${state.totalJourneys} 次远行    ·    ${state.ownedCount} 种收藏", 60f, 865f, 25f, sage)
            val display = if (selected != null) listOf(selected.id) else state.showcase.take(6)
            if (selected != null) {
                canvas.save(); canvas.translate(55f, 915f); CompanionArtwork.item(canvas, selected.id, 110f); canvas.restore()
                text(selected.name, 194f, 958f, 30f, cream, true)
                text("${selected.rarity.label} · ${selected.kind.label}", 194f, 1004f, 22f, sage)
            } else if (display.isEmpty()) {
                text("暂无收藏", 60f, 965f, 28f, cream)
            } else {
                display.forEachIndexed { index, id ->
                    val x = 60f + index * 130f
                    canvas.save(); canvas.translate(x, 927f); CompanionArtwork.item(canvas, id, 95f); canvas.restore()
                    text(CompanionCatalog.find(id)?.name.orEmpty(), x, 1060f, 16f, muted)
                }
            }
            text("PhonePulse", 60f, 1200f, 19f, muted)
            val directory = File(context.cacheDir, "companion_share").apply { check(isDirectory || mkdirs()) }
            val expiry = System.currentTimeMillis() - 7 * 24 * 3_600_000L
            directory.listFiles()?.filter { it.isFile && it.name.startsWith("forest-") && it.extension == "png" && it.lastModified() < expiry }
                ?.forEach { if (!it.delete()) android.util.Log.w("Companion", "Could not remove expired postcard") }
            // Every recipient keeps its own immutable image, even if another postcard is shared later.
            val file = File(directory, "forest-${UUID.randomUUID()}.png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file
        } finally { bitmap.recycle() }
    }
}
