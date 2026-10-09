package com.aizeek.phonepulse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizeek.phonepulse.companion.*
import com.aizeek.phonepulse.data.LiveBatteryInfo
import com.aizeek.phonepulse.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen(
    ui: CompanionUiState, battery: LiveBatteryInfo, serviceRunning: Boolean,
    onBack: () -> Unit, onEquip: (String) -> Unit, onShowcase: (String) -> Unit,
    onClaim: (String) -> Unit, onRename: (String) -> Unit, onConfirm: (Long, String) -> Unit,
    onObserve: () -> Unit, onShare: (String?) -> Unit, onRetry: () -> Unit,
    sharing: Boolean = false, shareError: String? = null, onStartTracking: () -> Unit = {},
    initialPage: Int = 0, initialItem: String? = null, feedbackHost: SnackbarHostState? = null,
    openRequest: Int = 0, onModalVisibility: (Boolean) -> Unit = {},
    onBuild: (HomeStage) -> Unit = {}, onPlace: (String, HomeSlot) -> Unit = { _, _ -> },
    onRemoveFurniture: (HomeSlot) -> Unit = {}, onReadLetter: (String) -> Unit = {},
    onFrame: (String) -> Unit = {}, onRoof: (String) -> Unit = {}
) {
    val state = ui.data
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    var selectedItem by rememberSaveable { mutableStateOf(initialItem) }
    var selectedLetter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFriend by rememberSaveable { mutableStateOf<String?>(null) }
    var buildPreview by rememberSaveable { mutableStateOf(false) }
    var nameDialog by rememberSaveable { mutableStateOf(false) }
    var interior by rememberSaveable { mutableStateOf(false) }
    var mailbox by rememberSaveable { mutableStateOf(false) }
    var homeSettings by rememberSaveable { mutableStateOf(false) }
    var handledRequest by rememberSaveable { mutableIntStateOf(openRequest) }
    BackHandler { if (interior) interior = false else onBack() }
    LaunchedEffect(openRequest) {
        if (handledRequest != openRequest) { page = initialPage; selectedItem = initialItem; handledRequest = openRequest }
    }
    LaunchedEffect(selectedItem, selectedLetter, selectedFriend, buildPreview, nameDialog, homeSettings) {
        onModalVisibility(selectedItem != null || selectedLetter != null || selectedFriend != null || buildPreview || nameDialog || homeSettings)
    }
    val list = rememberLazyListState()
    LaunchedEffect(page, mailbox) {
        if (page != 0) interior = false
        list.scrollToItem(0)
    }
    val openLetter: (String) -> Unit = { selectedLetter = it; onReadLetter(it) }
    Scaffold(containerColor = ForestBackground, snackbarHost = { feedbackHost?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(title = { Text(if (interior) "木屋里" else "森林里的家", color = ForestCream, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = { if (interior) interior = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ForestCream) } },
                actions = {
                    IconButton(onClick = { nameDialog = true }, enabled = ui.loaded) { Icon(Icons.Default.Edit, "给兔子改名", tint = ForestMuted) }
                    IconButton(onClick = { onShare(null) }, enabled = ui.loaded && !sharing) { Icon(Icons.Default.Share, "分享我的森林家园", tint = ForestSage) }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestBackground))
        }, bottomBar = {
            NavigationBar(containerColor = ForestSurface) {
                listOf("家园" to Icons.Default.Home, "旅册" to Icons.Default.AutoStories, "行囊" to Icons.Default.Backpack).forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(selected = page == index, onClick = { page = index; mailbox = false }, icon = { Icon(icon, null) }, label = { Text(title) },
                        modifier = Modifier.testTag("companion_page_$index"), colors = NavigationBarItemDefaults.colors(selectedIconColor = ForestBackground,
                            selectedTextColor = ForestCream, indicatorColor = ForestSage, unselectedIconColor = ForestMuted, unselectedTextColor = ForestMuted))
                }
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("companion_content"), state = list,
            contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (ui.error != null || shareError != null) item { ForestPanel {
                Text(ui.error ?: shareError.orEmpty(), color = Color(0xFFFFB4AB)); TextButton(onClick = onRetry) { Text("重试", color = ForestSage) }
            } }
            if (!ui.loaded) item { Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ForestSage) } }
            else when (page) {
                0 -> {
                    item {
                        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(state.name, color = ForestCream, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                                Text(state.home.stage.title, color = ForestGold, fontSize = 12.sp)
                            }
                            if (state.home.stage >= HomeStage.CABIN) TextButton(onClick = { interior = !interior }) { Text(if (interior) "到院子里" else "进屋坐坐", color = ForestSage) }
                        }
                    }
                    item {
                        Box(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(28.dp))) {
                            PetScene(state, Modifier.fillMaxWidth().aspectRatio(400f / 420f).testTag("forest_scene"), interior = interior)
                            Row(Modifier.align(Alignment.BottomCenter).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SceneAction("换装", Icons.Default.Checkroom) { page = 2 }
                                SceneAction("信箱${state.letters.count { !it.read }.let { if (it > 0) " · $it" else "" }}", Icons.Default.MailOutline) { page = 1; mailbox = true }
                                SceneAction(if (state.home.stage >= HomeStage.CABIN) "布置" else "建家", Icons.Default.Cottage) { if (state.home.stage >= HomeStage.CABIN) homeSettings = true else buildPreview = true }
                            }
                        }
                    }
                    item { ForestPanel {
                        Text(if (!serviceRunning) "我在这里等你。" else if (state.journeys.isEmpty()) "这里很适合安家。我去找点木头，你去忙你的吧。"
                            else "你去生活，我去看看森林。回来时，我们再交换故事。", color = ForestCream, fontSize = 15.sp, lineHeight = 25.sp)
                        if (!serviceRunning) TextButton(onClick = onStartTracking) { Text("开启记录", color = ForestSage) }
                    } }
                    state.letters.lastOrNull()?.let { letter -> item { ForestPanel {
                        Text(if (letter.read) "最近的一封信" else "森林寄来了一封信", color = ForestGold, fontSize = 12.sp)
                        SceneryArtwork(letter, Modifier.fillMaxWidth().aspectRatio(5f / 3f).clip(RoundedCornerShape(16.dp)).clickable { openLetter(letter.id) })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(letter.title, color = ForestCream, modifier = Modifier.weight(1f))
                            TextButton(onClick = { openLetter(letter.id) }) { Text("拆开看看", color = ForestSage) }
                        }
                    } } }
                    if (state.showcase.isNotEmpty()) item { ForestPanel {
                        Text("留在家里的纪念", color = ForestGold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.showcase.forEach { id -> Column(Modifier.weight(1f).clickable { selectedItem = id }, horizontalAlignment = Alignment.CenterHorizontally) {
                                ItemArtwork(id, Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(CompanionCatalog.find(id)?.name.orEmpty(), color = ForestMuted, fontSize = 10.sp)
                            } }
                        }
                    } }
                    item { ForestPanel {
                        val plan = ForestHome.next(state.home)
                        Text(plan?.invitation ?: "我们的小屋，慢慢变成喜欢的样子。", color = ForestCream, fontWeight = FontWeight.Medium)
                        Text("木料 ${state.home.wood}    石材 ${state.home.stone}    纤维 ${state.home.fiber}", color = ForestMuted, fontSize = 12.sp)
                        if (plan != null) {
                            val progress = minOf(state.home.wood.toFloat() / plan.wood, state.home.stone.toFloat() / plan.stone, state.home.fiber.toFloat() / plan.fiber).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = ForestSage, trackColor = ForestBorder)
                            TextButton(onClick = { buildPreview = true }) { Text(if (ForestHome.canBuild(state.home)) "搭起来" else "看看下一步", color = ForestSage) }
                        } else TextButton(onClick = { homeSettings = true }) { Text("布置我们的家", color = ForestSage) }
                    } }
                }
                1 -> forestTravelPage(state, mailbox, { mailbox = it }, openLetter, { selectedFriend = it })
                2 -> forestBagPage(state, { selectedItem = it })
            }
        }
    }
    selectedItem?.let { id -> CompanionCatalog.find(id)?.takeIf { (state.inventory[id] ?: 0) > 0 }?.let {
        ForestItemSheet(state, it, ui.error, { selectedItem = null }, onEquip, onShowcase, onPlace, onShare, sharing)
    } }
    selectedLetter?.let { id -> state.letters.firstOrNull { it.id == id }?.let { ForestLetterSheet(state, it, { selectedLetter = null }, onFrame, onShare, sharing) } }
    selectedFriend?.let { id -> state.friends.firstOrNull { it.id == id }?.let { ForestFriendSheet(it, { selectedFriend = null }) } }
    if (buildPreview) ForestBuildSheet(state, ui.error, { buildPreview = false }) { stage -> onBuild(stage); buildPreview = false }
    if (homeSettings) ForestHomeSheet(state, { homeSettings = false }, onRoof, onRemoveFurniture, { page = 2; homeSettings = false }, { buildPreview = true; homeSettings = false })
    if (nameDialog) {
        var name by rememberSaveable { mutableStateOf(state.name) }
        AlertDialog(onDismissRequest = { nameDialog = false }, containerColor = ForestSurface,
            title = { Text("给兔子起个名字", color = ForestCream) }, text = { OutlinedTextField(name, { if (it.length <= 12) name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(name); nameDialog = false }, enabled = name.trim().isNotEmpty()) { Text("就叫这个名字", color = ForestSage) } },
            dismissButton = { TextButton(onClick = { nameDialog = false }) { Text("以后再说", color = ForestMuted) } })
    }
}

@Composable
internal fun ForestPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ForestSurface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun SceneAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = ForestBackground.copy(alpha = 0.86f), contentColor = ForestCream)) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(text, fontSize = 12.sp)
    }
}
