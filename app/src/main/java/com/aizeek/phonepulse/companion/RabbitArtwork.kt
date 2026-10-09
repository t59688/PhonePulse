package com.aizeek.phonepulse.companion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.PathParser

/** Geometry generated from thridparty/04_taro_explorer.svg by tools/convert_rabbit.py. */
object RabbitArtwork {
    private class Layer(val group: String, data: String, val fill: String, val stroke: String, val width: Float) {
        val path = requireNotNull(PathParser.createPathFromPathData(data))
    }
    private val layers = listOf(
        Layer("ground_shadow", "M84,435a112,10 0 1,0 224,0a112,10 0 1,0 -224,0", "#d9c3ae", "none", 0f),
        Layer("ears_back", "M143 170 C133 134 119 91 128 54 C134 28 156 31 164 59 C173 93 173 137 169 169 Z", "#fff0d1", "#614b3f", 4.2f),
        Layer("ears_back", "M228 173 C245 147 283 125 305 126 C339 129 344 158 325 178 C306 198 266 194 246 187 Z", "#fff0d1", "#614b3f", 4.2f),
        Layer("ears_back", "M149 148 Q138 95 145 67 Q158 78 159 153 Z", "#efb4a4", "none", 0f),
        Layer("ears_back", "M254 175 Q304 141 321 150 Q314 173 257 183 Z", "#efb4a4", "none", 0f),
        Layer("body", "M131 305 C109 322 101 351 106 392 Q112 426 157 430 L245 430 Q286 429 290 393 C294 350 278 317 251 307 Z", "#ffedce", "#614b3f", 4.2f),
        Layer("body", "M142,371a60,49 0 1,0 120,0a60,49 0 1,0 -120,0", "#fff5e2", "none", 0f),
        Layer("feet", "M124 409 L163 411 L171 431 Q155 445 124 437 Q112 434 124 409 Z", "#856e63", "#614b3f", 4.2f),
        Layer("feet", "M226 412 L273 408 L284 430 Q262 443 237 438 Z", "#856e63", "#614b3f", 4.2f),
        Layer("feet", "M124 409 L163 411 L166 420 L122 420 Z", "#b28d76", "none", 0f),
        Layer("feet", "M231 413 L270 409 L278 420 L230 422 Z", "#b28d76", "none", 0f),
        Layer("arms", "M122 344 Q92 343 88 371 Q85 390 111 397 L146 380 Z", "#ffecd1", "#614b3f", 4.2f),
        Layer("arms", "M277 343 Q303 351 307 380 Q304 398 279 400 L250 379 Z", "#ffecd1", "#614b3f", 4.2f),
        Layer("head", "M109 222 C108 170 145 146 196 145 C257 145 286 180 290 229 Q304 262 286 286 Q267 315 214 321 L176 321 C124 315 93 293 101 257 Z", "#fff0d7", "#614b3f", 4.2f),
        Layer("head", "M119 268 C132 292 145 296 166 299", "none", "#fff8e9", 8f),
        Layer("face", "M140.5,242a5.5,7.5 0 1,0 11,0a5.5,7.5 0 1,0 -11,0", "#544640", "none", 0f),
        Layer("face", "M239.5,242a5.5,7.5 0 1,0 11,0a5.5,7.5 0 1,0 -11,0", "#544640", "none", 0f),
        Layer("face", "M146.7,239a1.3,1.3 0 1,0 2.6,0a1.3,1.3 0 1,0 -2.6,0", "#ffffff", "none", 0f),
        Layer("face", "M245.7,239a1.3,1.3 0 1,0 2.6,0a1.3,1.3 0 1,0 -2.6,0", "#ffffff", "none", 0f),
        Layer("face", "M108,267a17,8 0 1,0 34,0a17,8 0 1,0 -34,0", "#edaf9e", "none", 0f),
        Layer("face", "M249,267a17,8 0 1,0 34,0a17,8 0 1,0 -34,0", "#edaf9e", "none", 0f),
        Layer("face", "M190 260 Q196 254 203 260 Q196 269 194 267 Z", "#ba8272", "none", 0f),
        Layer("face", "M195 267 Q186 274 180 270 M195 267 Q204 275 210 269", "none", "#976c5d", 2.7f),
        Layer("face", "M172.2,275a1.8,1.8 0 1,0 3.6,0a1.8,1.8 0 1,0 -3.6,0", "#c69b7e", "none", 0f),
        Layer("face", "M216.2,275a1.8,1.8 0 1,0 3.6,0a1.8,1.8 0 1,0 -3.6,0", "#c69b7e", "none", 0f),
        Layer("clothes_cape", "M138 303 Q190 324 258 303 L272 341 Q282 371 285 390 Q267 401 245 393 Q199 407 153 394 Q125 400 109 385 L125 341 Z", "#e1ac63", "#614b3f", 4.2f),
        Layer("clothes_cape", "M143 305 Q190 326 253 306 L245 326 Q194 343 147 328 Z", "#f8d29a", "#614b3f", 4.2f),
        Layer("clothes_cape", "M194 327 L203 333 L207 342 L199 348 L190 341 Z", "#805942", "#614b3f", 2f),
        Layer("clothes_cape", "M128 374 Q159 393 189 382", "none", "#f0c187", 4f),
        Layer("clothes_cape", "M217 385 Q244 395 266 379", "none", "#f0c187", 4f),
        Layer("accessory_hat", "M140 163 C145 130 174 120 205 121 C239 119 255 140 257 159 Q200 179 140 163 Z", "#866d57", "#614b3f", 4.2f),
        Layer("accessory_hat", "M125 165 Q198 178 271 166 C271 183 129 188 125 165 Z", "#a88b66", "#614b3f", 4.2f),
        Layer("accessory_hat", "M173 134 Q196 129 220 135 L225 146 Q200 141 175 146 Z", "#c4a780", "none", 0f),
    )
    fun draw(canvas: Canvas, state: CompanionState, x: Float, y: Float, height: Float) {
        canvas.save(); canvas.translate(x, y); canvas.scale(height / 480f, height / 480f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        layers.forEach { layer ->
            if (layer.group == "accessory_hat" && state.equipped["HAT"] != "explorer_hat") return@forEach
            if (layer.group == "clothes_cape" && state.equipped["CAPE"] == null) return@forEach
            if (layer.fill != "none") {
                paint.style = Paint.Style.FILL
                val color = if (layer.group == "clothes_cape" && layer.fill in listOf("#e1ac63", "#f8d29a")) {
                    when (state.equipped["CAPE"]) {
                        "sage_cape" -> if (layer.fill == "#e1ac63") "#83a48a" else "#bdd1a6"
                        "rose_cape" -> if (layer.fill == "#e1ac63") "#c58e87" else "#edc0ac"
                        else -> layer.fill
                    }
                } else layer.fill
                paint.color = Color.parseColor(color); canvas.drawPath(layer.path, paint)
            }
            if (layer.stroke != "none" && layer.width > 0) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = layer.width
                paint.color = Color.parseColor(layer.stroke); canvas.drawPath(layer.path, paint)
            }
        }
        state.equipped["HAT"]?.takeIf { it != "explorer_hat" }?.let {
            canvas.save(); canvas.translate(130f, 103f); CompanionArtwork.item(canvas, it, 145f); canvas.restore()
        }
        state.equipped["SCARF"]?.let {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor(if (it == "rose_scarf") "#c58e87" else "#83a48a")
            canvas.drawRoundRect(135f, 302f, 255f, 326f, 9f, 9f, paint)
            canvas.drawRoundRect(230f, 318f, 252f, 364f, 7f, 7f, paint)
        }
        state.equipped["HAND"]?.let {
            canvas.save(); canvas.translate(258f, 347f); CompanionArtwork.item(canvas, it, 88f); canvas.restore()
        }
        canvas.restore()
    }
}
