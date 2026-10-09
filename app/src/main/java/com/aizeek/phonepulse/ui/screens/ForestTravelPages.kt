package com.aizeek.phonepulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun forestDate(time: Long): String = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(time))

internal fun LazyListScope.forestTravelPage(state: CompanionState, mailbox: Boolean, onMailbox: (Boolean) -> Unit,
                                           onLetter: (String) -> Unit, onFriend: (String) -> Unit) {
    item {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (mailbox) "森林信箱" else "沿途的日子", color = ForestCream, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !mailbox, onClick = { onMailbox(false) }, label = { Text("旅途") })
                FilterChip(selected = mailbox, onClick = { onMailbox(true) }, label = { Text("来信 · ${state.letters.size}") })
            }
        }
    }
    if (state.letters.isEmpty() && state.journeys.isEmpty()) item { ForestPanel { Text("下次回来，听我讲路上的事。", color = ForestMuted) } }
    if (mailbox) {
        items(state.letters.asReversed(), key = { it.id }) { letter -> ForestPanel {
            SceneryArtwork(letter, Modifier.fillMaxWidth().aspectRatio(5f / 3f).clip(RoundedCornerShape(14.dp)).clickable { onLetter(letter.id) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(letter.title, color = ForestCream, fontWeight = FontWeight.SemiBold)
                    Text("${ForestWorld.place(letter.placeId).name} · ${forestDate(letter.receivedAt)}", color = ForestMuted, fontSize = 12.sp)
                }
                TextButton(onClick = { onLetter(letter.id) }) { Text(if (letter.read) "再读一次" else "拆信", color = ForestSage) }
            }
        } }
    } else {
        if (state.friends.isNotEmpty()) item { ForestPanel {
            Text("森林里的朋友", color = ForestGold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                state.friends.forEach { friend -> Column(Modifier.weight(1f).clickable { onFriend(friend.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    FriendArtwork(friend.id, Modifier.size(68.dp)); Text(ForestWorld.friend(friend.id)?.name.orEmpty(), color = ForestCream, fontSize = 12.sp)
                } }
            }
        } }
        items(state.journeys, key = { "trip:${it.id}" }) { trip -> ForestPanel {
            val letter = state.letters.firstOrNull { it.journeyId == trip.id }
            if (letter != null) SceneryArtwork(letter, Modifier.fillMaxWidth().aspectRatio(5f / 3f).clip(RoundedCornerShape(14.dp)).clickable { onLetter(letter.id) })
            Text("${trip.place} · ${forestDate(trip.endTime)}", color = ForestGold, fontSize = 12.sp)
            Text(trip.guess, color = ForestCream, fontSize = 15.sp, lineHeight = 26.sp)
            FlowRewards(trip.rewards)
            if (letter != null) TextButton(onClick = { onLetter(letter.id) }) { Text("看看寄回的明信片", color = ForestSage) }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRewards(rewards: List<JourneyReward>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rewards.filter { it.kind in listOf(RewardKind.ITEM, RewardKind.FRIEND, RewardKind.MATERIAL) }.forEach {
            Text(it.title, color = ForestMuted, fontSize = 11.sp, modifier = Modifier.background(ForestBackground, RoundedCornerShape(10.dp)).padding(8.dp))
        }
    }
}

internal fun LazyListScope.forestBagPage(state: CompanionState, onSelect: (String) -> Unit) { item { BagContents(state, onSelect) } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BagContents(state: CompanionState, onSelect: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("全部") }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("带回家的喜欢", color = ForestCream, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("全部", "衣物", "家具", "纪念物", "建材").forEach { title -> FilterChip(selected = category == title, onClick = { category = title }, label = { Text(title, fontSize = 12.sp) }) }
        }
        if (category == "建材") Text("木料 ${state.home.wood}    石材 ${state.home.stone}    纤维 ${state.home.fiber}", color = ForestCream)
        else {
            val owned = CompanionCatalog.items.filter { (state.inventory[it.id] ?: 0) > 0 }.filter {
                when (category) { "衣物" -> it.kind !in listOf(ItemKind.HOME, ItemKind.TREASURE); "家具" -> it.kind == ItemKind.HOME; "纪念物" -> it.kind == ItemKind.TREASURE; else -> true }
            }
            if (owned.isEmpty()) Text("留一个位置，给下一次的小惊喜。", color = ForestMuted)
            owned.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item -> Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(ForestSurface)
                    .clickable { onSelect(item.id) }.testTag("item_${item.id}").padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ItemArtwork(item.id, Modifier.size(80.dp)); Text(item.name, color = ForestCream, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    val used = state.equipped[item.kind.name] == item.id || item.id in state.home.furniture.values
                    Text(if (used) "正在使用" else "${item.kind.label} · ${state.inventory[item.id]}", color = if (used) ForestGold else ForestMuted, fontSize = 11.sp)
                } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            } }
        }
    }
}
