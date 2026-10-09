package com.aizeek.phonepulse.companion

import com.aizeek.phonepulse.R
import java.util.Locale
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
        val letter = state.letters.firstOrNull { it.id == itemId }
        require(itemId == null || letter != null || selected != null && (state.inventory[itemId] ?: 0) > 0) { "只能分享已获得的物品或明信片" }
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
            text("${context.getString(R.string.app_name).uppercase(Locale.getDefault())}  /  FOREST POSTCARD", 60f, 75f, 21f, sage)
            text(letter?.title ?: "${state.name}的森林小窝", 60f, 148f, 44f, cream, true)
            canvas.save(); canvas.translate(60f, 195f)
            if (letter != null) ForestArtwork.scenery(canvas, 780f, 468f, letter.placeId, letter.artwork)
            else ForestArtwork.home(canvas, 780f, 819f, state)
            canvas.restore()
            if (letter != null) {
                val paragraph = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = cream; textSize = 32f }
                val layout = StaticLayout.Builder.obtain(letter.message, 0, letter.message.length, paragraph, 760)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(14f, 1f).build()
                canvas.save(); canvas.translate(70f, 726f); layout.draw(canvas); canvas.restore()
                text("${state.name}  /  ${ForestWorld.place(letter.placeId).name}", 70f, 1090f, 25f, sage)
            } else {
                text("${state.home.stage.title}    ·    ${state.totalJourneys} 次远行", 60f, 1068f, 26f, sage)
                if (selected != null) {
                    canvas.save(); canvas.translate(60f, 1090f); CompanionArtwork.item(canvas, selected.id, 75f); canvas.restore()
                    text(selected.name, 155f, 1140f, 28f, cream, true)
                } else text("你去生活，我去看看森林。", 60f, 1130f, 27f, cream)
            }
            text(context.getString(R.string.app_name), 60f, 1200f, 19f, muted)
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
