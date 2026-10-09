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
        ForestArtwork.home(canvas, width, height, state)
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
            "explorer_hat" -> {
                oval(16f, 19f, 64f, 53f, Color.rgb(134, 109, 87))
                rect(10f, 45f, 70f, 55f, 5f, Color.rgb(168, 139, 102))
                rect(22f, 35f, 57f, 42f, 3f, gold)
            }
            "explorer_cape", "sage_cape", "rose_cape" -> {
                val c = when (id) { "sage_cape" -> sage; "rose_cape" -> rose; else -> gold }
                color(c); canvas.drawPath(Path().apply { moveTo(29f, 15f); lineTo(51f, 15f); lineTo(70f, 63f)
                    quadTo(40f, 75f, 10f, 63f); close() }, p)
                oval(26f, 12f, 53f, 27f, cream)
            }
            "map" -> {
                rect(13f, 13f, 67f, 65f, 4f, cream)
                color(sage); p.strokeWidth = 3f; canvas.drawLine(23f, 50f, 54f, 27f, p)
                oval(49f, 22f, 57f, 30f, rose)
            }
            "cup" -> {
                rect(18f, 26f, 55f, 62f, 8f, blue)
                p.style = Paint.Style.STROKE; color(blue); p.style = Paint.Style.STROKE; p.strokeWidth = 5f
                canvas.drawOval(48f, 31f, 67f, 52f, p); p.style = Paint.Style.FILL
                oval(18f, 21f, 55f, 31f, cream)
            }
            "books" -> {
                rect(13f, 47f, 67f, 60f, 3f, sage); rect(17f, 33f, 65f, 46f, 3f, rose)
                rect(12f, 19f, 64f, 32f, 3f, gold)
            }
            "rug" -> {
                oval(8f, 24f, 72f, 64f, sage); oval(17f, 30f, 63f, 57f, cream)
                oval(24f, 34f, 56f, 53f, sage)
            }
            "basket" -> {
                p.style = Paint.Style.STROKE; color(gold); p.style = Paint.Style.STROKE; p.strokeWidth = 5f
                canvas.drawArc(RectF(24f, 10f, 56f, 48f), 180f, 180f, false, p); p.style = Paint.Style.FILL
                rect(14f, 32f, 66f, 62f, 8f, gold)
                color(Color.rgb(162, 126, 82)); p.strokeWidth = 2f; canvas.drawLine(18f, 43f, 62f, 43f, p)
            }
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
