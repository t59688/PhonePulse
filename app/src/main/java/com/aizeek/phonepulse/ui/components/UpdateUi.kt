package com.aizeek.phonepulse.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import com.aizeek.phonepulse.BuildConfig
import com.aizeek.phonepulse.ui.theme.CoralRose
import com.aizeek.phonepulse.ui.theme.NeonCyan
import com.aizeek.phonepulse.ui.theme.TextSecondary
import com.aizeek.phonepulse.update.UpdatePhase
import com.aizeek.phonepulse.update.UpdateRepository
import com.aizeek.phonepulse.update.UpdateState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
fun UpdateEntry(repository: UpdateRepository, onClick: () -> Unit) {
    val badgeFlow = remember(repository) { repository.state.map { it.hasUpdate }.distinctUntilChanged() }
    val hasUpdate by badgeFlow.collectAsStateWithLifecycle(initialValue = repository.state.value.hasUpdate)
    IconButton(onClick = onClick, modifier = Modifier.testTag("check_updates_button")) {
        Box {
            Icon(Icons.Default.SystemUpdate, contentDescription = if (hasUpdate) "发现新版本" else "检查更新", tint = NeonCyan)
            if (hasUpdate) Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(CoralRose, CircleShape).testTag("update_badge"))
        }
    }
}

@Composable
fun UpdateHost(repository: UpdateRepository, showDialog: Boolean, onClose: () -> Unit, openRequest: Int): Boolean {
    val state by repository.state.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var automaticDialog by rememberSaveable { mutableStateOf(false) }
    var handledRequest by rememberSaveable { mutableStateOf(0) }
    var installInFlight by remember { mutableStateOf(false) }

    fun launchInstaller() {
        scope.launch {
            try {
                val intent = repository.installerIntent()
                lifecycleOwner.lifecycle.withResumed {
                    context.startActivity(intent)
                    automaticDialog = false
                    onClose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                repository.showError(e.message ?: "无法唤起系统安装程序")
                automaticDialog = true
            } finally {
                installInFlight = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (repository.canInstall()) launchInstaller()
        else {
            installInFlight = false
            repository.showError("尚未允许安装此来源的应用，可点击安装后重新授权")
            automaticDialog = true
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { repository.download() }
    }

    fun requestInstall() {
        if (installInFlight) return
        installInFlight = true
        repository.consumeAutoInstall()
        if (repository.canInstall()) launchInstaller()
        else {
            try { permissionLauncher.launch(repository.installPermissionIntent()) }
            catch (e: Exception) {
                installInFlight = false
                repository.showError(e.message ?: "无法打开安装权限设置")
                automaticDialog = true
            }
        }
    }

    LaunchedEffect(repository, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.check()
            awaitCancellation()
        }
    }
    LaunchedEffect(state.isDownloading, lifecycleOwner) {
        if (state.isDownloading) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                repository.refresh()
                delay(if (repository.state.value.phase == UpdatePhase.PAUSED) 10_000 else 1000)
            }
        }
    }
    LaunchedEffect(state.phase, state.autoInstall, openRequest) {
        if (state.phase == UpdatePhase.AVAILABLE && repository.shouldPrompt()) automaticDialog = true
        if (openRequest > handledRequest) {
            automaticDialog = true
            if (state.phase == UpdatePhase.READY) {
                handledRequest = openRequest
                requestInstall()
            }
        }
        if (state.phase == UpdatePhase.READY && state.autoInstall &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) requestInstall()
    }

    val visible = showDialog || automaticDialog
    if (visible) {
        UpdateDialog(
            state = state,
            notificationsAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            onDismiss = { repository.dismissPrompt(); automaticDialog = false; handledRequest = openRequest; onClose() },
            onCheck = { scope.launch { repository.check(force = true) } },
            onDownload = {
                repository.dismissPrompt()
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else scope.launch { repository.download() }
            },
            onCancel = { scope.launch { repository.cancelDownload() } },
            onInstall = { requestInstall() }
        )
    }
    return visible
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    notificationsAllowed: Boolean,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.hasUpdate) "发现新版本 ${state.release?.version?.name}" else "应用更新") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前版本 ${BuildConfig.VERSION_NAME} · GitHub", color = TextSecondary)
                if (state.release != null) {
                    Text(state.release.title, fontWeight = FontWeight.SemiBold)
                    Text(state.release.notes.ifBlank { "此版本暂无更新说明" })
                    Text("安装包 ${formatBytes(state.release.size)}", color = TextSecondary)
                }
                Text(when (state.phase) {
                    UpdatePhase.IDLE -> "待检查更新"
                    UpdatePhase.CHECKING -> "正在检查更新…"
                    UpdatePhase.AVAILABLE -> "是否下载并安装此更新？"
                    UpdatePhase.DOWNLOADING -> "正在下载 ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
                    UpdatePhase.PAUSED -> "下载暂停，等待网络或系统重试"
                    UpdatePhase.VERIFYING -> "下载完成，正在校验安装包…"
                    UpdatePhase.READY -> "安装包已就绪，可唤起系统安装程序"
                    UpdatePhase.UP_TO_DATE -> "当前已是最新正式版本"
                    UpdatePhase.NO_RELEASE -> "暂未找到公开的正式发布"
                    UpdatePhase.FAILED -> "操作失败，可重试"
                })
                state.message?.let { Text(it, color = CoralRose) }
                if (state.isDownloading) {
                    if (state.totalBytes > 0 && state.phase != UpdatePhase.VERIFYING) {
                        LinearProgressIndicator(progress = { (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (!notificationsAllowed) Text("尚未允许通知，下载进度和安装入口仍可在应用内查看。", color = TextSecondary)
            }
        },
        confirmButton = {
            when {
                state.phase == UpdatePhase.READY -> Button(onClick = onInstall) { Text("安装") }
                state.isDownloading -> TextButton(onClick = onCancel) { Text("取消下载") }
                state.hasUpdate -> Button(onClick = onDownload, enabled = state.phase != UpdatePhase.CHECKING) { Text(if (state.phase == UpdatePhase.FAILED) "重新下载" else "下载更新") }
                else -> Button(onClick = onCheck, enabled = state.phase != UpdatePhase.CHECKING) { Text("检查更新") }
            }
        },
        dismissButton = {
            Row {
                if (state.phase == UpdatePhase.READY) TextButton(onClick = onCancel) { Text("删除安装包") }
                TextButton(onClick = onDismiss) { Text(if (state.hasUpdate && !state.isDownloading) "稍后" else "关闭") }
            }
        },
        modifier = Modifier.testTag("update_dialog")
    )
}

private fun formatBytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes.coerceAtLeast(0) / 1_048_576.0)
