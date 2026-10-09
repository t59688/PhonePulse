package com.aizeek.phonepulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.companion.*

val ForestBackground = Color(0xFF101D23)
val ForestSurface = Color(0xFF1B2B30)
val ForestBorder = Color(0xFF33464A)
val ForestSage = Color(0xFFB6D3B3)
val ForestCream = Color(0xFFF2E8D8)
val ForestMuted = Color(0xFFADBDB9)
val ForestGold = Color(0xFFE8C78F)

@Composable
fun PetScene(state: CompanionState, modifier: Modifier = Modifier, sleepy: Boolean = false,
             charging: Boolean = false, greeting: Boolean = false, interior: Boolean = false) {
    Canvas(modifier.semantics { contentDescription = "${state.name}的森林小窝" }) {
        drawIntoCanvas { ForestArtwork.home(it.nativeCanvas, size.width, size.height, state, interior) }
    }
}

@Composable
fun SceneryArtwork(letter: ForestLetter, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = letter.title }) {
        drawIntoCanvas { ForestArtwork.scenery(it.nativeCanvas, size.width, size.height, letter.placeId, letter.artwork) }
    }
}

@Composable
fun FriendArtwork(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = ForestWorld.friend(id)?.name.orEmpty() }) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save(); canvas.translate((size.width - minOf(size.width, size.height)) / 2, 0f)
            ForestArtwork.friend(canvas, id, scale = minOf(size.width, size.height) / 80f); canvas.restore()
        }
    }
}

@Composable
fun ItemArtwork(id: String, modifier: Modifier = Modifier, locked: Boolean = false) {
    Canvas(modifier) {
        val edge = minOf(size.width, size.height)
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.save(); canvas.translate((size.width - edge) / 2, (size.height - edge) / 2)
            CompanionArtwork.item(canvas, id, edge, locked); canvas.restore()
        }
    }
}

@Composable
fun CompanionHomeEntry(ui: CompanionUiState, onClick: () -> Unit, modifier: Modifier = Modifier,
                       serviceRunning: Boolean = true, onAction: (CompanionPrompt) -> Unit = { onClick() }) {
    val prompt = CompanionPrompts.home(ui.data, serviceRunning, System.currentTimeMillis())
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(ForestSurface).border(1.dp, ForestBorder, RoundedCornerShape(24.dp))
        .clickable(enabled = ui.loaded) { onAction(prompt) }.testTag("companion_entry").padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PetScene(ui.data, Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (ui.loaded) prompt.title else "${ui.data.name}的森林小窝", color = ForestCream,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(if (ui.loaded) prompt.message else "正在看看森林里的新消息…", color = ForestMuted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(prompt.badge, color = ForestGold, fontSize = 10.sp,
                modifier = Modifier.background(ForestGold.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).padding(9.dp, 5.dp))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onAction(prompt) }, enabled = ui.loaded,
                colors = ButtonDefaults.textButtonColors(contentColor = ForestSage)) {
                Text(prompt.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
