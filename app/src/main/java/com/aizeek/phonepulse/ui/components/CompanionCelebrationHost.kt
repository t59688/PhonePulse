package com.aizeek.phonepulse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.util.TimeFormatter

/** Event-driven foreground invitations. Reading a return never awards an item again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionCelebrationHost(ui: CompanionUiState, onAcknowledge: (Long, Int) -> Unit,
                             onOpen: (CompanionPrompt) -> Unit, onShare: (String?) -> Unit,
                             enabled: Boolean = true, sharing: Boolean = false, shareError: String? = null) {
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    if (!ui.loaded || !enabled) return
    val event = CompanionPrompts.celebration(ui.data) ?: return
    val key = "${event.journey?.id ?: 0}:${event.level}"
    if (dismissed == key) return
    val trip = event.journey
    val item = CompanionCatalog.find(trip?.itemId)
    val name = ui.data.name
    val dismiss = {
        dismissed = key
        onAcknowledge(trip?.id ?: ui.data.seenJourneyId, event.level)
    }
    val open = {
        dismiss()
        val action = if (item != null) PromptAction.OPEN_ITEMS else PromptAction.OPEN_HOME
        onOpen(CompanionPrompt(key, "", "", "", "", action, item?.id))
    }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = ForestSurface, contentColor = ForestCream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
            .testTag("companion_celebration"), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (trip != null) "我回来啦！" else "我们又更熟悉一点了", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            if (item != null) {
                ItemArtwork(item.id, Modifier.size(96.dp))
                Text(item.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("${item.rarity.label} · ${trip!!.place} · ${TimeFormatter.formatDurationChinese(trip.durationMs)}的旅途",
                    color = Color(item.rarity.color), fontSize = 12.sp)
            } else PetScene(ui.data, Modifier.fillMaxWidth().height(155.dp))
            if (event.levelUp) Text("Lv. ${event.level}",
                color = ForestGold, fontSize = 13.sp, modifier = Modifier.background(ForestGold.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(12.dp))
            if (shareError != null || ui.error != null) Text(shareError ?: ui.error.orEmpty(), color = Color(0xFFFFB4AB), fontSize = 12.sp)
            Button(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestSage, contentColor = ForestBackground)) {
                Text(when (item?.kind) {
                    ItemKind.HAT, ItemKind.SCARF -> "去给${name}试穿"
                    ItemKind.HOME -> "去布置我的小窝"
                    ItemKind.TREASURE -> "把礼物放进展示柜"
                    else -> "看看伙伴的成长"
                })
            }
            if (item != null) {
                OutlinedButton(onClick = { dismiss(); onShare(item.id) }, enabled = !sharing, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestSage)) {
                    Text(if (sharing) "正在准备明信片…" else "分享这次发现给朋友")
                }
            }
            TextButton(onClick = dismiss) { Text("知道啦，晚点再看", color = ForestMuted) }
        }
    }
}
