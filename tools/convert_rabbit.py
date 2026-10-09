"""Convert the editable rabbit SVG into static Android Canvas layer definitions."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "thridparty/04_taro_explorer.svg"
svg = ET.parse(source).getroot()
entries = []
for group in svg.findall("{*}g"):
    for element in group:
        tag = element.tag.split("}")[-1]
        attrs = element.attrib
        if tag == "path":
            path = attrs["d"]
        elif tag in ("ellipse", "circle"):
            cx, cy = float(attrs["cx"]), float(attrs["cy"])
            rx, ry = (float(attrs["rx"]), float(attrs["ry"])) if tag == "ellipse" else (float(attrs["r"]), float(attrs["r"]))
            path = f"M{cx-rx:g},{cy:g}a{rx:g},{ry:g} 0 1,0 {2*rx:g},0a{rx:g},{ry:g} 0 1,0 {-2*rx:g},0"
        else:
            raise ValueError(f"Unsupported SVG shape: {tag}")
        def android_color(value):
            return "#" + "".join(char * 2 for char in value[1:]) if value.startswith("#") and len(value) == 4 else value
        fill = android_color(attrs.get("fill", "#000000"))
        stroke = android_color(attrs.get("stroke", "none"))
        width = float(attrs.get("stroke-width", "0"))
        entries.append(f'        Layer("{group.attrib["id"]}", "{path}", "{fill}", "{stroke}", {width:g}f),')

header = '''package com.aizeek.phonepulse.companion

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
'''
footer = '''    )
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
'''
target = ROOT / "app/src/main/java/com/aizeek/phonepulse/companion/RabbitArtwork.kt"
target.write_text(header + "\n".join(entries) + "\n" + footer, encoding="utf-8", newline="\n")
print(f"Converted {len(entries)} shapes, preserving named SVG layers")
