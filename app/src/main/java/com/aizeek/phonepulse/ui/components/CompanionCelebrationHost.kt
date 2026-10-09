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
        onOpen(CompanionPrompt(key, "", "", "", "", PromptAction.OPEN_JOURNAL))
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ForestSurface, contentColor = ForestCream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
            .testTag("companion_celebration"), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (trip != null) "我回来啦！" else "我们又更熟悉一点了", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            val unread = ui.data.journeys.count { it.id > ui.data.seenJourneyId }
            if (unread > 1) Text("这段时间走了 $unread 趟，攒下了一些故事。", color = ForestMuted, fontSize = 13.sp)
            val letter = ui.data.letters.lastOrNull { it.journeyId == trip?.id }
            if (letter != null) SceneryArtwork(letter, Modifier.fillMaxWidth().aspectRatio(5f / 3f))
            else ItemArtwork(item?.id ?: "leaf", Modifier.size(96.dp))
            if (trip != null) {
                Text(trip.place, color = ForestGold, fontSize = 13.sp)
                Text(trip.guess, color = ForestCream, lineHeight = 25.sp)
            }
            if (item != null) Text("还带回了${item.name}", color = ForestSage, fontSize = 14.sp)
            if (shareError != null || ui.error != null) Text(shareError ?: ui.error.orEmpty(), color = Color(0xFFFFB4AB), fontSize = 12.sp)
            Button(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestSage, contentColor = ForestBackground)) {
                Text("看看路上的惊喜")
            }
            if (item != null && item.kind !in listOf(ItemKind.HOME, ItemKind.TREASURE)) TextButton(onClick = {
                dismiss()
                onOpen(CompanionPrompt(key, "", "", "", "", PromptAction.OPEN_ITEMS, item.id))
            }) { Text("去给${name}试穿", color = ForestSage) }
            if (letter != null) OutlinedButton(onClick = { dismiss(); onShare(letter.id) }, enabled = !sharing,
                modifier = Modifier.fillMaxWidth()) { Text("分享这张明信片", color = ForestSage) }
            TextButton(onClick = dismiss) { Text("知道啦，晚点再看", color = ForestMuted) }
        }
    }
}
