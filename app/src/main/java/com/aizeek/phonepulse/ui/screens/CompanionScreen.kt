package com.aizeek.phonepulse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.ui.components.*
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CompanionScreen(
    ui: CompanionUiState, battery: LiveBatteryInfo, serviceRunning: Boolean,
    onBack: () -> Unit, onEquip: (String) -> Unit, onShowcase: (String) -> Unit,
    onClaim: (String) -> Unit, onRename: (String) -> Unit, onConfirm: (Long, String) -> Unit,
    onObserve: () -> Unit, onShare: (String?) -> Unit, onRetry: () -> Unit,
    sharing: Boolean = false, shareError: String? = null, onStartTracking: () -> Unit = {},
    initialPage: Int = 0, initialItem: String? = null,
    feedbackHost: SnackbarHostState? = null, openRequest: Int = 0,
    onModalVisibility: (Boolean) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val state = ui.data
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var selectedId by rememberSaveable { mutableStateOf(initialItem) }
    var showName by rememberSaveable { mutableStateOf(false) }
    var handledRequest by rememberSaveable { mutableIntStateOf(openRequest) }
    LaunchedEffect(openRequest) {
        if (handledRequest != openRequest) {
            page = initialPage; selectedId = initialItem; handledRequest = openRequest
        }
    }
    LaunchedEffect(selectedId, showName) { onModalVisibility(selectedId != null || showName) }
    var greeting by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    LaunchedEffect(page) { listState.scrollToItem(0) }
    val scale by animateFloatAsState(if (greeting) 1.025f else 1f, tween(220), label = "pet_greeting")
    LaunchedEffect(interaction) {
        if (interaction > 0) { greeting = true; delay(1800); greeting = false }
    }
    val colors = ButtonDefaults.buttonColors(containerColor = ForestSage, contentColor = ForestBackground)
    Scaffold(containerColor = ForestBackground,
        snackbarHost = { feedbackHost?.let { SnackbarHost(it) } }, topBar = {
        TopAppBar(title = { Text("森林小窝", color = ForestCream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回首页", tint = ForestCream) } },
            actions = {
                IconButton(onClick = { onShare(null) }, enabled = ui.loaded && !sharing) {
                    if (sharing) CircularProgressIndicator(Modifier.size(20.dp), color = ForestSage, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Share, "分享我的小窝", tint = ForestSage)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestBackground))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("companion_content"), state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (ui.error != null || shareError != null) item {
                ForestPanel {
                    Text(ui.error ?: shareError.orEmpty(), color = Color(0xFFFFB4AB), fontSize = 13.sp)
                    if (ui.error != null) TextButton(onClick = onRetry) { Text("重试", color = ForestSage) }
                }
            }
            if (!ui.loaded) item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = ForestSage)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(when (page) { 1 -> "图鉴"; 2 -> "手记"; else -> state.name },
                                color = ForestCream, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                        }
                        ForestPill("Lv. ${state.level}", ForestGold)
                    }
                }
            }
            if (page == 0) item {
                Column(Modifier.clip(RoundedCornerShape(28.dp)).background(ForestSurface)
                    .border(1.dp, ForestBorder, RoundedCornerShape(28.dp))) {
                    Box {
                        PetScene(state, Modifier.fillMaxWidth().height(235.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clickable(enabled = ui.loaded) { interaction++ }.testTag("pet_interaction"),
                            sleepy = battery.percentage in 0..20, charging = battery.isCharging, greeting = greeting)
                        Text(if (battery.isCharging) "正在补充能量" else if (battery.percentage in 0..20) "有点困，陪你慢下来" else "旅行伙伴 · ${state.name}",
                            color = ForestCream, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                                .background(ForestBackground.copy(alpha = 0.65f), RoundedCornerShape(20.dp)).padding(10.dp, 6.dp))
                    }
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.name, color = ForestCream, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { showName = true }, enabled = ui.loaded, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Edit, "给伙伴改名", tint = ForestMuted, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            ForestPill("${state.totalJourneys} 次远行", ForestSage)
                        }
                        Text(if (greeting) listOf("我在呢。今天也很高兴见到你。", "给你留了一点森林的好心情。", "不用急着出发，坐一会也很好。")[interaction % 3]
                            else if (!serviceRunning) "我在等你。"
                            else "你回来，我也回来。",
                            color = ForestMuted, fontSize = 13.sp, lineHeight = 21.sp)
                        LinearProgressIndicator(progress = { state.growthInLevel / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                            color = ForestSage, trackColor = ForestBorder)
                        Text("成长 ${state.growthInLevel} / 100", color = ForestMuted, fontSize = 10.sp)
                        if (!serviceRunning) OutlinedButton(onClick = onStartTracking,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestSage)) {
                            Text("开启记录")
                        }
                    }
                }
            }
            stickyHeader {
                Row(Modifier.fillMaxWidth().background(ForestBackground).padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp)).background(ForestSurface).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("小窝", "图鉴", "手记").forEachIndexed { index, title ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (page == index) ForestSage else Color.Transparent)
                            .clickable { page = index }.testTag("companion_page_$index").padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center) {
                            Text(title, color = if (page == index) ForestBackground else ForestMuted,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            when (page) {
                0 -> {
                    val latest = state.journeys.firstOrNull()
                    item {
                        ForestHeading("最近的收获")
                        Spacer(Modifier.height(12.dp))
                        ForestPanel {
                            if (latest == null) {
                                Icon(Icons.Default.Explore, null, tint = ForestSage, modifier = Modifier.size(26.dp))
                                Text("暂无收获", color = ForestMuted, fontSize = 16.sp)
                            } else {
                                val item = CompanionCatalog.find(latest.itemId)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (item != null) ItemArtwork(item.id, Modifier.size(64.dp).clickable { selectedId = item.id })
                                    else Icon(Icons.Default.Explore, null, tint = ForestSage, modifier = Modifier.size(40.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(item?.name ?: "一段新的森林见闻", color = ForestCream, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${latest.place} · ${TimeFormatter.formatDurationChinese(latest.durationMs)}", color = ForestMuted, fontSize = 11.sp)
                                        if (item != null) ForestPill(item.rarity.label, Color(item.rarity.color))
                                    }
                                }
                                TextButton(onClick = { page = 2 }) { Text("读读这次的手记", color = ForestSage) }
                            }
                        }
                    }
                    item {
                        ForestHeading("休息任务")
                        Spacer(Modifier.height(12.dp))
                        val today = CompanionRules.day(System.currentTimeMillis())
                        val longest = if (state.taskDay == today) state.longestRestMs else 0
                        ForestPanel {
                            RestTask("rest20", "给自己一小段空白", "连续熄屏 20 分钟", 20, longest, state, today, ui.loaded, onClaim)
                            HorizontalDivider(color = ForestBorder)
                            RestTask("rest60", "去生活里走一走", "连续熄屏 60 分钟", 60, longest, state, today, ui.loaded, onClaim)
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { ForestHeading("我的展示柜") }
                            IconButton(onClick = { onShare(null) }, enabled = ui.loaded && !sharing) {
                                Icon(Icons.Default.Share, "分享展示柜", tint = ForestSage)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ForestPanel {
                            if (state.showcase.isEmpty()) {
                                Text("暂无收藏", color = ForestMuted, fontSize = 15.sp)
                                TextButton(onClick = { page = 1 }) { Text("去图鉴挑选", color = ForestSage) }
                            } else {
                                state.showcase.chunked(3).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { id ->
                                            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { selectedId = id }.padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally) {
                                                ItemArtwork(id, Modifier.size(58.dp))
                                                Text(CompanionCatalog.find(id)?.name.orEmpty(), color = ForestCream, fontSize = 10.sp)
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(onClick = { onShare(null) }, enabled = ui.loaded && !sharing,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp), colors = colors) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (sharing) "正在制作明信片…" else "分享我的小窝")
                        }
                    }
                }
                1 -> {
                    item {
                        ForestHeading("沿途的宝物", "已发现 ${state.ownedCount} / ${CompanionCatalog.items.size} 种")
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("全部", "收藏", "头饰", "围巾", "摆件").forEach { label ->
                                FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ForestSage,
                                        selectedLabelColor = ForestBackground, labelColor = ForestMuted))
                            }
                        }
                    }
                    val catalog = CompanionCatalog.items.filter { filter == "全部" || it.kind.label == filter }
                    items(catalog.chunked(2), key = { row -> row.first().id }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { item ->
                                val count = state.inventory[item.id] ?: 0
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(ForestSurface)
                                    .border(1.dp, if (count > 0) Color(item.rarity.color).copy(alpha = 0.3f) else ForestBorder, RoundedCornerShape(22.dp))
                                    .clickable(enabled = ui.loaded) { selectedId = item.id }.testTag("item_${item.id}").padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        ItemArtwork(item.id, Modifier.size(80.dp), locked = count == 0)
                                        if (count > 0) Text("×$count", color = ForestMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd))
                                    }
                                    Text(if (count > 0) item.name else "未发现", color = if (count > 0) ForestCream else ForestMuted,
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    ForestPill(if (state.equipped[item.kind.name] == item.id) "已装扮" else item.rarity.label,
                                        if (count > 0) Color(item.rarity.color) else ForestMuted)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                2 -> {
                    item {
                        ForestHeading("森林手记")
                        Spacer(Modifier.height(12.dp))
                        ForestPanel {
                            Text("今天的小观察", color = ForestCream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            ui.observation?.let { Text(it, color = ForestMuted, fontSize = 12.sp, lineHeight = 20.sp) }
                            OutlinedButton(onClick = onObserve, enabled = !ui.observing && ui.loaded,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestSage)) {
                                Text(if (ui.observing) "正在看今天的记录…" else "看看今天的小观察")
                            }
                        }
                    }
                    if (state.journeys.isEmpty()) item {
                        ForestPanel {
                            Text("暂无手记", color = ForestMuted, fontSize = 16.sp)
                        }
                    }
                    items(state.journeys, key = { it.id }) { trip ->
                        ForestPanel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(trip.place, color = ForestCream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${SimpleDateFormat("MM.dd HH:mm", Locale.CHINA).format(Date(trip.endTime))} · 熄屏 ${TimeFormatter.formatDurationChinese(trip.durationMs)}",
                                        color = ForestMuted, fontSize = 11.sp)
                                }
                                trip.itemId?.let { id -> ItemArtwork(id, Modifier.size(48.dp).clickable { selectedId = id }) }
                            }
                            Text(trip.guess, color = ForestCream, fontSize = 13.sp, lineHeight = 21.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("睡眠", "工作", "放松", "其他").forEach { label ->
                                    FilterChip(selected = trip.confirmed == label, onClick = { onConfirm(trip.id, label) },
                                        label = { Text(label, fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ForestSage, selectedLabelColor = ForestBackground, labelColor = ForestMuted))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedId?.let { id ->
        val item = CompanionCatalog.find(id) ?: return@let
        val owned = (state.inventory[id] ?: 0) > 0
        ModalBottomSheet(onDismissRequest = { selectedId = null }, containerColor = ForestSurface, contentColor = ForestCream) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ItemArtwork(id, Modifier.size(100.dp), locked = !owned)
                Text(if (owned) item.name else "尚未发现的宝物", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                ForestPill("${item.rarity.label} · ${item.kind.label}", Color(item.rarity.color))
                if (owned) {
                    if (ui.error != null || shareError != null) {
                        Text(ui.error ?: shareError.orEmpty(), color = Color(0xFFFFB4AB), fontSize = 12.sp)
                    }
                    state.journeys.firstOrNull { it.itemId == id }?.let {
                        Text("最近发现于 ${it.place} · ${TimeFormatter.formatDurationChinese(it.durationMs)}的探险", color = ForestMuted, fontSize = 11.sp)
                    }
                    if (item.kind != ItemKind.TREASURE) Button(onClick = { onEquip(id) }, modifier = Modifier.fillMaxWidth(), colors = colors) {
                        Text(if (state.equipped[item.kind.name] == id) "卸下${if (item.kind == ItemKind.HOME) "摆件" else "装扮"}"
                            else if (item.kind == ItemKind.HOME) "摆进小窝" else "给${state.name}穿戴")
                    }
                    OutlinedButton(onClick = { onShowcase(id) }, enabled = id in state.showcase || state.showcase.size < 6,
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestSage)) {
                        Text(if (id in state.showcase) "移出展示柜" else if (state.showcase.size >= 6) "展示柜已满（6 / 6）" else "加入展示柜（${state.showcase.size} / 6）")
                    }
                    TextButton(onClick = { onShare(id) }, enabled = !sharing) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("分享这件收获", color = ForestSage)
                    }
                }
            }
        }
    }
    if (showName) {
        var name by rememberSaveable { mutableStateOf(state.name) }
        AlertDialog(onDismissRequest = { showName = false }, containerColor = ForestSurface,
            title = { Text("给伙伴起个名字", color = ForestCream) },
            text = { OutlinedTextField(value = name, onValueChange = { if (it.length <= 12) name = it }, singleLine = true, label = { Text("1～12 个字") }) },
            confirmButton = { TextButton(onClick = { onRename(name); showName = false }, enabled = name.trim().isNotEmpty()) { Text("就叫这个名字", color = ForestSage) } },
            dismissButton = { TextButton(onClick = { showName = false }) { Text("取消", color = ForestMuted) } })
    }
}

@Composable
private fun ForestHeading(title: String, subtitle: String? = null) {
    Text(title, color = ForestCream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    subtitle?.let { Text(it, color = ForestMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) }
}

@Composable
private fun ForestPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ForestSurface)
        .border(1.dp, ForestBorder, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun ForestPill(text: String, color: Color) {
    Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp, 5.dp))
}

@Composable
private fun RestTask(id: String, title: String, subtitle: String, minutes: Int, longest: Long,
                     state: CompanionState, today: String, loaded: Boolean, onClaim: (String) -> Unit) {
    val claimed = state.taskDay == today && id in state.claimedTasks
    val eligible = longest >= minutes * 60_000L
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (claimed) Icons.Default.CheckCircle else Icons.Default.Spa, null, tint = ForestSage, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = ForestCream, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("$subtitle · ${minOf(longest / 60_000, minutes.toLong())}/$minutes", color = ForestMuted, fontSize = 10.sp)
        }
        TextButton(onClick = { onClaim(id) }, enabled = loaded && eligible && !claimed,
            colors = ButtonDefaults.textButtonColors(contentColor = ForestSage, disabledContentColor = ForestMuted)) {
            Text(if (claimed) "已领取" else if (eligible) "领取 +15" else "待完成", fontSize = 11.sp)
        }
    }
}
