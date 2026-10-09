package com.aizeek.phonepulse.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForestSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ForestSurface, contentColor = ForestCream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun ForestButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestSage, contentColor = ForestBackground)) { Text(text) }
}

@Composable
internal fun ForestItemSheet(state: CompanionState, item: CompanionItem, error: String?, onDismiss: () -> Unit,
                             onEquip: (String) -> Unit, onShowcase: (String) -> Unit,
                             onPlace: (String, HomeSlot) -> Unit, onShare: (String?) -> Unit, sharing: Boolean) {
    var trying by rememberSaveable(item.id) { mutableStateOf(false) }
    ForestSheet(onDismiss) {
        Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (trying) PetScene(CompanionRules.equip(state, item.id), Modifier.fillMaxWidth().aspectRatio(400f / 420f).clip(RoundedCornerShape(20.dp)))
        else ItemArtwork(item.id, Modifier.size(110.dp))
        Text(item.story, color = ForestMuted, lineHeight = 24.sp)
        if (error != null) Text(error, color = Color(0xFFFFB4AB))
        when (item.kind) {
            ItemKind.HOME -> {
                Text("放在哪里？", color = ForestGold, fontSize = 13.sp)
                HomeSlot.entries.filter { state.home.stage >= it.minimum }.forEach { slot ->
                    OutlinedButton(onClick = { onPlace(item.id, slot) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.home.furniture[slot.name] == item.id) "${slot.title} · 已摆放" else slot.title, color = ForestSage)
                    }
                }
                if (state.home.stage == HomeStage.CLEARING) Text("等营地搭好，给它留个位置。", color = ForestMuted)
            }
            ItemKind.TREASURE -> {
                OutlinedButton(onClick = { onShowcase(item.id) }, enabled = item.id in state.showcase || state.showcase.size < 6) {
                    Text(if (item.id in state.showcase) "移出展示柜" else "加入展示柜（${state.showcase.size} / 6）", color = ForestSage)
                }
            }
            else -> {
                if (trying) {
                    ForestButton(if (state.equipped[item.kind.name] == item.id) "确认卸下" else "就穿这件") { onEquip(item.id); trying = false }
                    TextButton(onClick = { trying = false }) { Text("取消试穿", color = ForestMuted) }
                } else ForestButton("试穿看看") { trying = true }
            }
        }
        TextButton(onClick = { onShare(item.id) }, enabled = !sharing) { Text("分享这件收获", color = ForestSage) }
    }
}

@Composable
internal fun ForestLetterSheet(state: CompanionState, letter: ForestLetter, onDismiss: () -> Unit,
                               onFrame: (String) -> Unit, onShare: (String?) -> Unit, sharing: Boolean) {
    var back by rememberSaveable(letter.id) { mutableStateOf(false) }
    ForestSheet(onDismiss) {
        Text(letter.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (back) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1E6CA)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("寄给你", color = Color(0xFF77614D), fontSize = 13.sp)
            Text(letter.message, color = Color(0xFF574B3F), fontSize = 17.sp, lineHeight = 31.sp)
            HorizontalDivider(color = Color(0xFFC9B997))
            Text("${state.name}\n${ForestWorld.place(letter.placeId).name} · ${forestDate(letter.receivedAt)}", color = Color(0xFF77614D), fontSize = 13.sp, lineHeight = 23.sp)
        } else SceneryArtwork(letter, Modifier.fillMaxWidth().aspectRatio(5f / 3f).clip(RoundedCornerShape(12.dp)))
        TextButton(onClick = { back = !back }) { Text(if (back) "看看正面的风景" else "翻过来，读读来信", color = ForestSage) }
        ForestButton(if (sharing) "正在准备明信片…" else "分享这张明信片", !sharing) { onShare(letter.id) }
        if (state.home.stage >= HomeStage.CABIN) OutlinedButton(onClick = { onFrame(letter.id) }) {
            Text(if (state.home.wallPicture == letter.id) "已挂在木屋墙上" else "装框，挂进木屋", color = ForestSage)
        }
    }
}

@Composable
internal fun ForestFriendSheet(friend: ForestFriend, onDismiss: () -> Unit) {
    val definition = ForestWorld.friend(friend.id) ?: return
    ForestSheet(onDismiss) {
        FriendArtwork(friend.id, Modifier.size(100.dp))
        Text(definition.name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("${definition.species} · ${definition.description}", color = ForestMuted, lineHeight = 24.sp)
        friend.meetings.asReversed().forEach { meeting ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ForestBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${if (meeting.visiting) "来家里做客" else "路上的相遇"} · ${forestDate(meeting.time)}", color = ForestGold, fontSize = 12.sp)
                Text(meeting.story, color = ForestCream, lineHeight = 25.sp)
            }
        }
    }
}

@Composable
internal fun ForestBuildSheet(state: CompanionState, error: String?, onDismiss: () -> Unit, onBuild: (HomeStage) -> Unit) {
    val plan = ForestHome.next(state.home) ?: return
    val baseStage = remember { state.home.stage }
    ForestSheet(onDismiss) {
        Text(plan.stage.title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        PetScene(state.copy(home = state.home.copy(stage = plan.stage)), Modifier.fillMaxWidth().aspectRatio(400f / 420f).clip(RoundedCornerShape(20.dp)))
        Text(plan.invitation, color = ForestCream, lineHeight = 25.sp)
        Text("木料 ${state.home.wood}/${plan.wood}    石材 ${state.home.stone}/${plan.stone}    纤维 ${state.home.fiber}/${plan.fiber}", color = ForestGold, fontSize = 13.sp)
        if (error != null) Text(error, color = Color(0xFFFFB4AB))
        ForestButton(if (ForestHome.canBuild(state.home)) "搭起来" else "等兔子带回更多材料", ForestHome.canBuild(state.home) && state.home.stage == baseStage) { onBuild(baseStage) }
    }
}

@Composable
internal fun ForestHomeSheet(state: CompanionState, onDismiss: () -> Unit, onRoof: (String) -> Unit,
                             onRemove: (HomeSlot) -> Unit, onBag: () -> Unit, onBuild: () -> Unit) {
    var roof by rememberSaveable { mutableStateOf(state.home.roof) }
    ForestSheet(onDismiss) {
        Text("把家布置成喜欢的样子", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        PetScene(state.copy(home = state.home.copy(roof = roof)), Modifier.fillMaxWidth().aspectRatio(400f / 420f).clip(RoundedCornerShape(20.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("terracotta" to "陶土", "moss" to "苔绿", "slate" to "灰蓝").forEach { (id, title) ->
                FilterChip(selected = roof == id, onClick = { roof = id }, label = { Text(title) })
            }
        }
        if (roof != state.home.roof) ForestButton("保存屋顶颜色") { onRoof(roof) }
        ForestButton("挑选家具") { onBag() }
        state.home.furniture.forEach { (slot, id) -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${HomeSlot.valueOf(slot).title} · ${CompanionCatalog.find(id)?.name.orEmpty()}", color = ForestCream, modifier = Modifier.weight(1f))
            TextButton(onClick = { onRemove(HomeSlot.valueOf(slot)) }) { Text("收起来", color = ForestMuted) }
        } }
        if (ForestHome.next(state.home) != null) TextButton(onClick = onBuild) { Text("继续建家", color = ForestSage) }
    }
}
