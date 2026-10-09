package com.aizeek.phonepulse.companion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import java.util.Calendar

/** Shared static illustration for the home, travel album and exported cards. */
object ForestArtwork {
    private class Brush(val canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        fun color(value: String) { paint.color = Color.parseColor(value); paint.shader = null; paint.style = Paint.Style.FILL }
        fun oval(x: Float, y: Float, w: Float, h: Float, color: String) { color(color); canvas.drawOval(x, y, x + w, y + h, paint) }
        fun rect(x: Float, y: Float, w: Float, h: Float, color: String, radius: Float = 0f) {
            color(color); canvas.drawRoundRect(x, y, x + w, y + h, radius, radius, paint)
        }
        fun path(color: String, block: Path.() -> Unit) { color(color); canvas.drawPath(Path().apply(block), paint) }
        fun line(x: Float, y: Float, endX: Float, endY: Float, color: String, width: Float = 2f) {
            color(color); paint.strokeWidth = width; canvas.drawLine(x, y, endX, endY, paint)
        }
        fun gradient(w: Float, h: Float, top: String, bottom: String) {
            paint.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor(top), Color.parseColor(bottom), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        }
        fun tree(x: Float, y: Float, scale: Float, color: String) {
            canvas.save(); canvas.translate(x, y); canvas.scale(scale, scale)
            rect(-5f, -50f, 10f, 105f, "#776956", 4f)
            path(color) { moveTo(0f, -145f); quadTo(-15f, -82f, -46f, -53f); lineTo(-28f, -54f)
                quadTo(-37f, -28f, -60f, 0f); quadTo(0f, 22f, 60f, 0f); lineTo(28f, -54f)
                lineTo(45f, -52f); quadTo(13f, -89f, 0f, -145f); close() }
            line(0f, -90f, 0f, -5f, "#9dad85", 1.2f)
            canvas.restore()
        }
        fun flower(x: Float, y: Float, color: String) {
            line(x, y, x - 2, y - 12, "#698963")
            oval(x - 7, y - 17, 10f, 7f, color); oval(x - 3, y - 20, 7f, 10f, color)
            oval(x - 3, y - 16, 4f, 4f, "#ddba72")
        }
    }

    fun home(canvas: Canvas, width: Float, height: Float, state: CompanionState, interior: Boolean = false,
             night: Boolean = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) !in 6..18) {
        canvas.save(); canvas.scale(width / 400f, height / 420f); canvas.clipRect(0f, 0f, 400f, 420f)
        val b = Brush(canvas)
        if (interior && state.home.stage >= HomeStage.CABIN) {
            interior(b, state)
        } else {
            b.gradient(400f, 420f, if (night) "#223d46" else "#d7dfc2", if (night) "#597966" else "#a8c0a0")
            b.oval(310f, 30f, 32f, 32f, if (night) "#f9e6ab" else "#f7edc6")
            if (night) listOf(30f to 40f, 135f to 66f, 234f to 20f, 275f to 77f, 367f to 103f).forEach { (x, y) -> b.oval(x, y, 2f, 2f, "#e1dbb6") }
            b.path(if (night) "#3c5b53" else "#88a48a") { moveTo(0f, 130f); quadTo(65f, 83f, 130f, 151f); quadTo(245f, 75f, 400f, 129f); lineTo(400f, 420f); lineTo(0f, 420f); close() }
            listOf(18f to 159f, 65f to 185f, 330f to 183f, 391f to 140f).forEach { (x, y) -> b.tree(x, y, 0.88f, if (night) "#365649" else "#6f8c70") }
            b.path(if (night) "#708362" else "#c4cc9f") { moveTo(0f, 268f); quadTo(165f, 192f, 400f, 266f); lineTo(400f, 420f); lineTo(0f, 420f); close() }
            b.oval(67f, 248f, 300f, 100f, if (night) "#96a17a" else "#e2d6ae")
            when (state.home.stage) {
                HomeStage.CLEARING -> {
                    b.rect(168f, 260f, 95f, 21f, "#adba92", 10f)
                    b.line(181f, 270f, 249f, 270f, "#e6d9bc", 3f)
                    b.rect(247f, 230f, 24f, 30f, "#a88b64", 7f)
                    b.rect(217f, 221f, 21f, 28f, "#a88b64", 5f)
                    b.oval(210f, 209f, 36f, 14f, "#d0ae7d")
                }
                HomeStage.CAMP -> {
                    b.path("#bfa572") { moveTo(135f, 260f); lineTo(213f, 155f); lineTo(298f, 263f); close() }
                    b.path("#eddbad") { moveTo(213f, 155f); lineTo(247f, 263f); lineTo(298f, 263f); close() }
                    b.path("#7d795a") { moveTo(180f, 260f); lineTo(213f, 190f); lineTo(242f, 260f); close() }
                    b.line(213f, 149f, 213f, 268f, "#735e4b", 4f)
                }
                HomeStage.FOUNDATION -> {
                    b.rect(140f, 257f, 170f, 18f, "#a17e59", 4f)
                    for (i in 0..6) b.line(145f, 262f + i * 2, 300f, 262f + i * 2, "#d0b281", 1f)
                    listOf(151f, 235f, 298f).forEach { b.line(it, 257f, it, 159f, "#997a58", 8f) }
                    b.line(151f, 159f, 298f, 159f, "#997a58", 8f)
                    b.line(235f, 159f, 252f, 123f, "#997a58", 6f); b.line(252f, 123f, 298f, 159f, "#997a58", 6f)
                }
                HomeStage.CABIN, HomeStage.COZY -> cabin(b, state, night)
            }
            if (state.home.stage >= HomeStage.CAMP) {
                b.line(91f, 278f, 91f, 234f, "#887350", 5f)
                b.rect(74f, 210f, 37f, 28f, "#c38a6c", 9f); b.line(80f, 222f, 104f, 222f, "#765b46", 2f)
                if (state.letters.any { !it.read }) { b.rect(109f, 203f, 3f, 18f, "#6f5b45"); b.rect(111f, 203f, 12f, 8f, "#ecd39d", 2f) }
            }
            if (state.home.stage == HomeStage.COZY) {
                b.path("#ddd0ac") { moveTo(226f, 267f); cubicTo(212f, 295f, 302f, 310f, 270f, 420f); lineTo(229f, 420f)
                    cubicTo(260f, 326f, 163f, 306f, 207f, 267f); close() }
                b.rect(315f, 264f, 62f, 9f, "#9e7f5a", 3f)
                b.line(323f, 271f, 320f, 286f, "#806b51", 4f); b.line(366f, 271f, 369f, 286f, "#806b51", 4f)
            }
            drawFurniture(b, state, false)
            RabbitArtwork.draw(canvas, state, 98f, 210f, 155f)
            val visitor = state.friends.firstOrNull { it.meetings.lastOrNull()?.visiting == true }
            if (visitor != null && state.home.stage >= HomeStage.CABIN) friend(canvas, visitor.id, 286f, 288f, 0.7f)
            b.tree(-5f, 283f, 1.6f, if (night) "#2b493c" else "#57795c")
            b.tree(409f, 310f, 1.5f, if (night) "#2e4c3e" else "#4b7057")
            for (i in 0..8) b.flower(28f + i * 43, 387f + (i % 3) * 8, if (i % 2 == 0) "#f0dcb8" else "#ccaa92")
            b.oval(20f, 342f, 12f, 7f, "#b9c597"); b.oval(367f, 355f, 14f, 8f, "#b9c597")
        }
        canvas.restore()
    }

    private fun cabin(b: Brush, state: CompanionState, night: Boolean) {
        b.rect(139f, 156f, 178f, 110f, "#bca17a", 3f)
        for (i in 0..7) b.line(139f, 168f + i * 13, 317f, 168f + i * 13, "#a88c65", 1.3f)
        b.path("#947858") { moveTo(317f, 156f); lineTo(341f, 141f); lineTo(341f, 249f); lineTo(317f, 266f); close() }
        val roofColor = when (state.home.roof) { "moss" -> "#6e8561"; "slate" -> "#7b8a92"; else -> "#b67c61" }
        b.path(roofColor) { moveTo(123f, 165f); lineTo(212f, 97f); lineTo(331f, 161f); close() }
        b.path("#8a6651") { moveTo(212f, 97f); lineTo(239f, 83f); lineTo(348f, 143f); lineTo(331f, 161f); close() }
        b.line(124f, 165f, 212f, 97f, "#765b49", 5f); b.line(212f, 97f, 331f, 161f, "#765b49", 5f)
        b.rect(258f, 101f, 16f, 27f, "#857462", 2f)
        b.rect(197f, 198f, 39f, 68f, "#796f51", 17f); b.rect(203f, 205f, 27f, 59f, "#8e865f", 13f)
        b.oval(222f, 234f, 4f, 4f, "#ebd4a0")
        listOf(153f, 260f).forEach { x ->
            b.rect(x, 186f, 36f, 38f, "#765e49", 5f); b.rect(x + 4, 190f, 28f, 30f, if (night) "#efd19a" else "#d5e2cb", 3f)
            b.line(x + 18, 190f, x + 18, 220f, "#9d835f", 3f); b.line(x + 4, 205f, x + 32, 205f, "#9d835f", 3f)
            b.rect(x - 3, 223f, 42f, 6f, "#8e7654", 2f)
        }
        b.rect(190f, 265f, 54f, 9f, "#ad9571", 3f)
    }

    private fun interior(b: Brush, state: CompanionState) {
        b.gradient(400f, 420f, "#c5ab84", "#e3d5b0")
        for (i in 0..12) b.line(0f, i * 22f, 400f, i * 22f, "#b69b75", 1.5f)
        b.rect(0f, 267f, 400f, 153f, "#a88e6c")
        for (i in 0..6) b.line(0f, 273f + i * 24, 400f, 273f + i * 24, "#8f775d", 2f)
        b.rect(246f, 64f, 94f, 107f, "#7d6c52", 18f)
        b.rect(253f, 71f, 80f, 93f, "#9db69a", 13f)
        b.oval(283f, 86f, 21f, 21f, "#f2e7bc"); b.tree(290f, 159f, 0.4f, "#678465")
        b.line(293f, 72f, 293f, 164f, "#b49a70", 4f); b.line(253f, 116f, 333f, 116f, "#b49a70", 4f)
        b.rect(239f, 170f, 108f, 10f, "#907655", 3f)
        b.rect(39f, 260f, 109f, 39f, "#997b59", 7f); b.rect(37f, 254f, 117f, 16f, "#e9dbb9", 8f)
        b.rect(40f, 244f, 33f, 12f, "#c9bd8f", 5f); b.line(46f, 290f, 46f, 316f, "#846a4f", 5f)
        b.rect(258f, 243f, 88f, 13f, "#8f7456", 5f)
        b.line(270f, 255f, 267f, 303f, "#8f7456", 7f); b.line(337f, 255f, 341f, 303f, "#8f7456", 7f)
        b.oval(72f, 324f, 230f, 60f, "#b9b588")
        if (state.showcase.isNotEmpty()) {
            b.rect(35f, 167f, 150f, 6f, "#8f7456", 2f)
            state.showcase.forEachIndexed { index, id ->
                b.canvas.save(); b.canvas.translate(35f + index * 24, 141f)
                CompanionArtwork.item(b.canvas, id, 25f); b.canvas.restore()
            }
        }
        state.home.wallPicture?.let { id -> state.letters.firstOrNull { it.id == id }?.let { letter ->
            b.rect(115f, 64f, 94f, 66f, "#82694e", 4f)
            b.canvas.save(); b.canvas.translate(121f, 70f); scenery(b.canvas, 82f, 54f, letter.placeId, letter.artwork); b.canvas.restore()
        } }
        drawFurniture(b, state, true)
        RabbitArtwork.draw(b.canvas, state, 150f, 204f, 176f)
    }

    private fun drawFurniture(b: Brush, state: CompanionState, inside: Boolean) {
        state.home.furniture.forEach { (slot, id) ->
            val position = when (slot) {
                "ENTRY" -> if (!inside) 244f to 268f else null
                "GARDEN" -> if (!inside) 294f to 300f else null
                "TABLE" -> if (inside) 284f to 210f else null
                "WINDOW" -> if (inside) 266f to 146f else null
                "WALL" -> if (inside) 50f to 110f else null
                else -> null
            } ?: return@forEach
            b.canvas.save(); b.canvas.translate(position.first, position.second)
            CompanionArtwork.item(b.canvas, id, 46f); b.canvas.restore()
        }
    }

    fun scenery(canvas: Canvas, width: Float, height: Float, placeId: String, variant: Int = 0) {
        canvas.save(); canvas.scale(width / 400f, height / 240f); canvas.clipRect(0f, 0f, 400f, 240f)
        val b = Brush(canvas)
        val night = placeId == "lake" || variant == 1
        b.gradient(400f, 240f, if (night) "#294954" else "#dce5c9", if (night) "#7d9a83" else "#a6bea0")
        b.oval(if (variant == 0) 298f else 74f, 28f, 29f, 29f, "#f0dcaa")
        b.path(if (night) "#547566" else "#94ad83") { moveTo(0f, 123f); quadTo(87f, 46f, 182f, 121f); quadTo(300f, 69f, 400f, 102f); lineTo(400f, 240f); lineTo(0f, 240f); close() }
        for (i in 0..7) b.tree(i * 59f - 12, 161f + i % 3 * 8, 0.6f, if (night) "#416352" else "#718d69")
        when (placeId) {
            "creek", "lake" -> {
                b.path(if (night) "#8eaea5" else "#b4cfc6") { moveTo(0f, 159f); quadTo(222f, 129f, 400f, 165f); lineTo(400f, 240f); lineTo(0f, 240f); close() }
                for (i in 0..5) b.line(35f + i * 52, 180f + i % 3 * 14, 62f + i * 52, 180f + i % 3 * 14, "#d4dfc5")
                if (placeId == "creek") {
                    b.path("#a99e83") { moveTo(79f, 178f); quadTo(204f, 63f, 335f, 178f); lineTo(335f, 201f)
                        quadTo(200f, 103f, 79f, 201f); close() }
                    b.line(79f, 178f, 79f, 199f, "#7f826a", 8f)
                } else for (i in 0..8) b.line(334f + i * 6, 240f, 327f + i * 7, 173f - i % 3 * 12, "#4b7059", 3f)
            }
            "mushroom" -> for (i in 0..4) {
                val x = 45f + i * 78; val y = 172f + i % 2 * 28
                b.rect(x, y, 11f, 45f, "#e4d8b3", 5f)
                b.path(if (variant == 0) "#c49273" else "#cdad7f") { moveTo(x - 29, y + 5); cubicTo(x - 24, y - 38, x + 35, y - 38, x + 41, y + 5); close() }
                b.oval(x - 12, y - 13, 8f, 6f, "#f2e2bd")
            }
            "meadow" -> for (i in 0..24) b.flower(10f + i * 17, 195f + i % 4 * 11, if (i % 3 == 0) "#c0abc5" else "#f2e3bf")
            "ridge" -> {
                b.path("#c5c998") { moveTo(0f, 240f); quadTo(232f, 112f, 400f, 155f); lineTo(400f, 240f); close() }
                b.tree(325f, 158f, 0.93f, "#52745e")
            }
            else -> {
                b.path("#ddd0a7") { moveTo(120f, 240f); quadTo(296f, 159f, 214f, 125f); quadTo(329f, 169f, 204f, 240f); close() }
                b.rect(97f, 181f, 48f, 29f, "#9c8562", 5f); b.oval(91f, 174f, 60f, 17f, "#c5aa7e")
            }
        }
        canvas.restore()
    }

    fun friend(canvas: Canvas, id: String, x: Float = 0f, y: Float = 0f, scale: Float = 1f) {
        canvas.save(); canvas.translate(x, y); canvas.scale(scale, scale)
        val b = Brush(canvas)
        val body = when (id) { "pip" -> "#b6bda2"; "otto" -> "#b69779"; else -> "#bc8e61" }
        if (id == "hazel") b.oval(3f, 24f, 35f, 51f, "#c29a6c")
        b.oval(22f, 32f, 43f, 47f, body); b.oval(18f, 12f, 48f, 41f, body)
        if (id != "pip") { b.oval(20f, 6f, 13f, 18f, body); b.oval(50f, 6f, 13f, 18f, body) }
        else {
            b.path("#bc806b") { moveTo(24f, 16f); lineTo(37f, 0f); lineTo(48f, 17f); close() }
            b.path("#d3ad71") { moveTo(63f, 29f); lineTo(79f, 34f); lineTo(63f, 39f); close() }
        }
        b.oval(32f, 47f, 27f, 24f, "#ead9b5")
        b.oval(32f, 28f, 4f, 5f, "#584b40"); b.oval(51f, 28f, 4f, 5f, "#584b40")
        b.oval(39f, 37f, 8f, 4f, "#66513f"); b.oval(18f, 75f, 52f, 5f, "#a8a88c")
        canvas.restore()
    }
}
