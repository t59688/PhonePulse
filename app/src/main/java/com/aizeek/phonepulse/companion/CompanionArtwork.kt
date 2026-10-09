package com.aizeek.phonepulse.companion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/** Static artwork shared by Compose and exported postcards; no frame loop or bitmap assets. */
object CompanionArtwork {
    private val cream = Color.rgb(244, 230, 206)
    private val ink = Color.rgb(39, 51, 50)

    fun scene(canvas: Canvas, width: Float, height: Float, state: CompanionState,
              sleepy: Boolean = false, charging: Boolean = false, greeting: Boolean = false) {
        canvas.save()
        canvas.scale(width / 400f, height / 270f)
        canvas.clipRect(0f, 0f, 400f, 270f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, 270f,
            intArrayOf(Color.rgb(24, 48, 49), Color.rgb(15, 29, 35)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 400f, 270f, paint)
        paint.shader = null
        fun oval(l: Float, t: Float, r: Float, b: Float, color: Int) {
            paint.color = color; canvas.drawOval(l, t, r, b, paint)
        }
        fun line(x: Float, y: Float, ex: Float, ey: Float, color: Int, stroke: Float = 2f) {
            paint.color = color; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x, y, ex, ey, paint)
        }
        // A quiet moon, distant hills and hand-drawn woodland silhouettes.
        oval(308f, 29f, 337f, 58f, Color.rgb(221, 217, 181))
        oval(299f, 22f, 326f, 50f, Color.rgb(24, 47, 48))
        oval(-80f, 159f, 273f, 340f, Color.rgb(35, 60, 55))
        oval(166f, 162f, 499f, 359f, Color.rgb(29, 51, 49))
        listOf(37f to 142f, 76f to 184f, 347f to 170f, 372f to 130f).forEach { (x, y) ->
            line(x, y + 43, x, y - 40, Color.rgb(71, 91, 75), 3f)
            paint.color = Color.rgb(52, 80, 67)
            canvas.drawPath(Path().apply {
                moveTo(x, y - 65); lineTo(x - 25, y); lineTo(x + 25, y); close()
            }, paint)
            canvas.drawPath(Path().apply {
                moveTo(x, y - 42); lineTo(x - 30, y + 20); lineTo(x + 30, y + 20); close()
            }, paint)
        }
        listOf(53f to 50f, 98f to 81f, 174f to 30f, 245f to 65f, 360f to 88f, 285f to 127f).forEach { (x, y) ->
            oval(x, y, x + 3, y + 3, Color.rgb(155, 173, 151))
        }
        oval(123f, 225f, 274f, 245f, Color.rgb(16, 31, 32))
        canvas.save()
        canvas.translate(130f, 53f)
        pet(canvas, state, sleepy, charging, greeting)
        canvas.restore()
        state.equipped[ItemKind.HOME.name]?.let {
            canvas.save(); canvas.translate(294f, 173f); canvas.scale(0.7f, 0.7f)
            item(canvas, it, 72f); canvas.restore()
        }
        // Low plants in the foreground frame the pet rather than covering it.
        listOf(94f to 241f, 300f to 249f).forEach { (x, y) ->
            line(x, y, x - 8, y - 26, Color.rgb(133, 159, 125), 3f)
            oval(x - 20, y - 30, x - 7, y - 14, Color.rgb(113, 149, 117))
            oval(x - 6, y - 22, x + 8, y - 7, Color.rgb(133, 159, 125))
        }
        canvas.restore()
    }

    private fun pet(canvas: Canvas, state: CompanionState, sleepy: Boolean, charging: Boolean, greeting: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun oval(l: Float, t: Float, r: Float, b: Float, color: Int) {
            p.color = color; canvas.drawOval(l, t, r, b, p)
        }
        fun rounded(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
            p.color = color; canvas.drawRoundRect(l, t, r, b, radius, radius, p)
        }
        oval(17f, 0f, 56f, 92f, cream)
        oval(85f, 6f, 120f, 93f, cream)
        oval(27f, 11f, 45f, 69f, Color.rgb(216, 168, 149))
        oval(94f, 17f, 111f, 70f, Color.rgb(216, 168, 149))
        oval(27f, 102f, 115f, 182f, Color.rgb(215, 206, 184))
        oval(39f, 116f, 104f, 171f, cream)
        oval(26f, 169f, 65f, 188f, cream)
        oval(81f, 169f, 119f, 188f, cream)
        oval(7f, 53f, 133f, 145f, cream)
        oval(21f, 109f, 42f, 119f, Color.rgb(221, 175, 155))
        oval(99f, 109f, 120f, 119f, Color.rgb(221, 175, 155))
        if (sleepy) {
            p.color = ink; p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f; p.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(RectF(36f, 91f, 53f, 104f), 0f, 180f, false, p)
            canvas.drawArc(RectF(87f, 91f, 104f, 104f), 0f, 180f, false, p)
            p.style = Paint.Style.FILL
        } else {
            oval(40f, 91f, 48f, 102f, ink); oval(92f, 91f, 100f, 102f, ink)
            oval(42f, 92f, 44f, 95f, Color.WHITE); oval(94f, 92f, 96f, 95f, Color.WHITE)
        }
        p.color = Color.rgb(162, 114, 105)
        canvas.drawPath(Path().apply { moveTo(66f, 108f); lineTo(74f, 108f); lineTo(70f, 113f); close() }, p)
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 2f
        canvas.drawArc(RectF(61f, 109f, 70f, 118f), 0f, 155f, false, p)
        canvas.drawArc(RectF(70f, 109f, 79f, 118f), 25f, 155f, false, p)
        p.style = Paint.Style.FILL
        oval(if (greeting) 107f else 104f, if (greeting) 95f else 137f,
            if (greeting) 133f else 125f, if (greeting) 125f else 167f, cream)
        oval(14f, 136f, 35f, 164f, cream)
        state.equipped[ItemKind.SCARF.name]?.let { id ->
            val color = if (id == "rose_scarf") Color.rgb(191, 126, 129) else Color.rgb(117, 154, 126)
            rounded(26f, 129f, 115f, 143f, 7f, color)
            rounded(86f, 133f, 103f, 164f, 5f, color)
        }
        state.equipped[ItemKind.HAT.name]?.let { id ->
            canvas.save(); canvas.translate(39f, 29f); canvas.scale(0.87f, 0.65f)
            item(canvas, id, 72f); canvas.restore()
        }
        if (charging) {
            oval(45f, 150f, 88f, 178f, Color.rgb(193, 150, 110))
            rounded(43f, 146f, 89f, 154f, 4f, Color.rgb(229, 201, 155))
            oval(59f, 136f, 73f, 148f, Color.rgb(140, 170, 126))
        }
    }

    fun item(canvas: Canvas, id: String, size: Float, silhouette: Boolean = false) {
        canvas.save(); canvas.scale(size / 80f, size / 80f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun color(c: Int) { p.color = if (silhouette) Color.rgb(65, 80, 81) else c }
        fun oval(l: Float, t: Float, r: Float, b: Float, c: Int) {
            color(c); canvas.drawOval(l, t, r, b, p)
        }
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float, c: Int) {
            color(c); canvas.drawRoundRect(l, t, r, b, radius, radius, p)
        }
        val sage = Color.rgb(147, 184, 144); val gold = Color.rgb(227, 192, 129)
        val blue = Color.rgb(174, 199, 229); val rose = Color.rgb(206, 150, 147)
        when (id) {
            "leaf" -> {
                color(sage); canvas.drawPath(Path().apply { moveTo(17f, 61f); cubicTo(7f, 22f, 42f, 10f, 64f, 16f)
                    cubicTo(71f, 47f, 38f, 70f, 17f, 61f); close() }, p)
                color(Color.rgb(74, 110, 89)); p.strokeWidth = 2f; canvas.drawLine(15f, 66f, 53f, 26f, p)
            }
            "stone", "moon" -> {
                oval(14f, 25f, 66f, 64f, if (id == "moon") blue else Color.rgb(162, 170, 159))
                oval(23f, 31f, 47f, 41f, if (id == "moon") Color.rgb(217, 230, 244) else Color.rgb(192, 198, 184))
                if (id == "moon") { color(gold); canvas.drawCircle(62f, 18f, 3f, p); canvas.drawCircle(17f, 18f, 2f, p) }
            }
            "acorn" -> {
                oval(18f, 31f, 64f, 67f, Color.rgb(194, 149, 97))
                rect(12f, 24f, 68f, 42f, 9f, Color.rgb(137, 115, 81))
                rect(35f, 12f, 44f, 29f, 4f, Color.rgb(137, 115, 81))
            }
            "scarf", "rose_scarf" -> {
                val c = if (id == "scarf") sage else rose
                rect(16f, 22f, 64f, 39f, 7f, c); rect(43f, 31f, 58f, 67f, 4f, c)
                color(cream); p.strokeWidth = 2f; canvas.drawLine(47f, 56f, 54f, 56f, p)
            }
            "plant" -> {
                rect(24f, 43f, 58f, 67f, 6f, Color.rgb(192, 148, 121))
                oval(15f, 19f, 41f, 43f, sage); oval(38f, 13f, 66f, 36f, Color.rgb(113, 160, 129))
                color(sage); p.strokeWidth = 4f; canvas.drawLine(40f, 25f, 40f, 49f, p)
            }
            "letter" -> {
                rect(12f, 23f, 68f, 60f, 5f, cream)
                color(Color.rgb(169, 157, 130)); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
                canvas.drawPath(Path().apply { moveTo(14f, 25f); lineTo(40f, 44f); lineTo(66f, 25f) }, p)
                p.style = Paint.Style.FILL; oval(35f, 38f, 46f, 49f, sage)
            }
            "flower" -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 5f; color(sage)
                canvas.drawOval(15f, 30f, 65f, 60f, p); p.style = Paint.Style.FILL
                listOf(20f to 34f, 37f to 29f, 55f to 35f).forEach { (x, y) ->
                    oval(x - 7, y - 8, x + 7, y + 7, cream); oval(x - 2, y - 2, x + 3, y + 3, gold)
                }
            }
            "lamp" -> {
                rect(34f, 31f, 47f, 61f, 5f, cream)
                oval(23f, 57f, 60f, 67f, Color.rgb(177, 145, 104))
                color(rose); canvas.drawPath(Path().apply { moveTo(9f, 38f); cubicTo(10f, 2f, 69f, 1f, 71f, 38f); close() }, p)
                oval(22f, 23f, 31f, 30f, cream); oval(44f, 16f, 54f, 24f, cream)
            }
            "star", "crown" -> {
                color(gold)
                val path = Path()
                if (id == "star") {
                    for (i in 0..9) {
                        val angle = -Math.PI / 2 + i * Math.PI / 5
                        val radius = if (i % 2 == 0) 29 else 13
                        val x = 40 + kotlin.math.cos(angle).toFloat() * radius
                        val y = 40 + kotlin.math.sin(angle).toFloat() * radius
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                } else {
                    path.moveTo(14f, 58f); path.lineTo(10f, 24f); path.lineTo(29f, 39f)
                    path.lineTo(40f, 17f); path.lineTo(51f, 39f); path.lineTo(70f, 24f); path.lineTo(66f, 58f)
                }
                path.close(); canvas.drawPath(path, p)
                oval(36f, 36f, 44f, 44f, cream)
            }
            "window" -> {
                rect(15f, 10f, 65f, 70f, 17f, Color.rgb(125, 143, 137))
                rect(20f, 15f, 60f, 65f, 14f, Color.rgb(41, 60, 83))
                oval(39f, 24f, 53f, 38f, gold)
                color(cream); canvas.drawCircle(28f, 29f, 2f, p); canvas.drawCircle(33f, 46f, 2f, p)
            }
        }
        canvas.restore()
    }
}
