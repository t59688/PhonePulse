package com.aizeek.phonepulse.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aizeek.phonepulse.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, PAUSED, VERIFYING, READY, FAILED, UP_TO_DATE, NO_RELEASE }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: GitHubRelease? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String? = null,
    val autoInstall: Boolean = false,
    val downloadId: Long = -1
) {
    val hasUpdate: Boolean get() = release?.version?.let {
        it > (ReleaseVersion.parse(BuildConfig.VERSION_NAME) ?: ReleaseVersion(0, 0, 0))
    } == true
    val isDownloading: Boolean get() = phase in listOf(UpdatePhase.DOWNLOADING, UpdatePhase.PAUSED, UpdatePhase.VERIFYING)
}

class UpdateRepository(
    private val context: Context,
    private val fetchLatest: suspend () -> GitHubRelease? = { GitHubUpdateClient().latestRelease() }
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(UpdateState(release = cachedRelease()).let {
        if (it.hasUpdate) it.copy(phase = UpdatePhase.AVAILABLE) else UpdateState()
    })
    val state = _state.asStateFlow()

    private fun cachedRelease(): GitHubRelease? = preferences.getString("release", null)?.let {
        runCatching { GitHubRelease.parse(it) }.getOrNull()
    }

    suspend fun check(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            refreshLocked()
            if (_state.value.isDownloading || _state.value.phase == UpdatePhase.READY) return@withLock
            val now = System.currentTimeMillis()
            val sinceSuccess = now - preferences.getLong("checked_at", 0)
            val sinceAttempt = now - preferences.getLong("attempted_at", 0)
            if (!force && (sinceSuccess in 0 until 6 * 3600_000L || sinceAttempt in 0 until 15 * 60_000L)) return@withLock
            preferences.edit().putLong("attempted_at", now).apply()
            _state.value = _state.value.copy(phase = UpdatePhase.CHECKING, message = null)
            try {
                val release = fetchLatest()
                val newer = release?.takeIf {
                    it.version > (ReleaseVersion.parse(BuildConfig.VERSION_NAME) ?: ReleaseVersion(0, 0, 0))
                }
                preferences.edit().putLong("checked_at", now).putString("release", newer?.toJson()).apply()
                _state.value = UpdateState(
                    phase = if (newer != null) UpdatePhase.AVAILABLE else if (release == null) UpdatePhase.NO_RELEASE else UpdatePhase.UP_TO_DATE,
                    release = newer,
                    message = if (release == null) "未找到公开的正式发布" else null
                )
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(phase = if (_state.value.hasUpdate) UpdatePhase.AVAILABLE else UpdatePhase.IDLE)
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = UpdatePhase.FAILED, message = e.message ?: "更新检测失败，请重试")
            }
        }
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_state.value.isDownloading || _state.value.phase == UpdatePhase.READY) return@withLock
            val release = _state.value.release ?: return@withLock
            try {
                val previousId = preferences.getLong("download_id", -1)
                if (previousId >= 0) { downloads.remove(previousId); apkFile(previousId).delete() }
                val externalDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IOException("下载目录不可用")
                val updateDirectory = File(externalDirectory, "updates")
                if (!updateDirectory.isDirectory && !updateDirectory.mkdirs()) throw IOException("无法创建下载目录")
                val fileName = "phonepulse-${release.version.name}-release.apk"
                val oldFile = File(updateDirectory, fileName)
                if (oldFile.exists() && !oldFile.delete()) throw IOException("无法清理旧安装包")
                val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
                    .setTitle("PhonePulse ${release.version.name}")
                    .setDescription("正在下载更新安装包")
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/$fileName")
                val id = downloads.enqueue(request)
                val saved = preferences.edit().putLong("download_id", id).putString("release", release.toJson())
                    .putBoolean("auto_install", true).remove("ready_id").remove("download_error").commit()
                if (!saved) { downloads.remove(id); throw IOException("无法保存下载任务，请重试") }
                _state.value = UpdateState(UpdatePhase.DOWNLOADING, release, totalBytes = release.size, autoInstall = true, downloadId = id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(phase = UpdatePhase.FAILED, message = e.message ?: "无法开始下载")
            }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { refreshLocked() } }

    private fun refreshLocked() {
        val id = preferences.getLong("download_id", -1)
        val release = cachedRelease() ?: return
        val currentVersion = ReleaseVersion.parse(BuildConfig.VERSION_NAME) ?: return
        if (release.version <= currentVersion) {
            if (id >= 0) { downloads.remove(id); apkFile(id).delete() }
            preferences.edit().remove("download_id").remove("release").remove("ready_id").remove("download_error").apply()
            _state.value = UpdateState(UpdatePhase.UP_TO_DATE)
            return
        }
        if (id < 0) return
        val base = UpdateState(release = release, downloadId = id, totalBytes = release.size,
            autoInstall = preferences.getBoolean("auto_install", false))
        preferences.getString("download_error", null)?.let {
            _state.value = base.copy(phase = UpdatePhase.FAILED, message = it)
            return
        }
        if (preferences.getLong("ready_id", -1) == id && apkFile(id).isFile) {
            _state.value = base.copy(phase = UpdatePhase.READY, downloadedBytes = release.size)
            return
        }
        try {
            downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    preferences.edit().remove("download_id").putBoolean("auto_install", false).apply()
                    _state.value = base.copy(phase = UpdatePhase.FAILED, message = "下载已取消或被系统移除，请重新下载", autoInstall = false)
                    return
                }
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: release.size
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                _state.value = base.copy(phase = UpdatePhase.DOWNLOADING, downloadedBytes = bytes, totalBytes = total)
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _state.value = base.copy(phase = UpdatePhase.VERIFYING)
                        saveVerifiedApk(id, release)
                        preferences.edit().putLong("ready_id", id).commit()
                        _state.value = base.copy(phase = UpdatePhase.READY, downloadedBytes = release.size)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        failDownload(base, "下载失败（$reason），请检查网络或存储空间后重试")
                    }
                    DownloadManager.STATUS_PAUSED -> _state.value = base.copy(phase = UpdatePhase.PAUSED,
                        downloadedBytes = bytes, totalBytes = total, message = "下载已暂停，等待网络或系统重试")
                }
            }
        } catch (e: Exception) {
            failDownload(base, e.message ?: "无法读取下载状态，请重试")
        }
    }

    private fun failDownload(base: UpdateState, message: String) {
        preferences.edit().putString("download_error", message).putBoolean("auto_install", false).apply()
        _state.value = base.copy(phase = UpdatePhase.FAILED, message = message, autoInstall = false)
    }

    private fun apkFile(id: Long) = File(context.filesDir, "updates/$id.apk")

    private fun saveVerifiedApk(id: Long, release: GitHubRelease) {
        val directory = File(context.filesDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建安装包目录")
        val temporary = File.createTempFile("verify-", ".apk", directory)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(downloads.openDownloadedFile(id)).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > release.size) throw IOException("安装包大小超出发布信息")
                        output.write(buffer, 0, count)
                    }
                }
            }
            ApkVerifier(context).verify(temporary, release)
            if (!temporary.renameTo(apkFile(id))) throw IOException("无法保存安装包")
        } finally {
            temporary.delete()
        }
    }

    suspend fun cancelDownload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val id = preferences.getLong("download_id", -1)
                if (id >= 0) { downloads.remove(id); apkFile(id).delete() }
                preferences.edit().remove("download_id").remove("ready_id").remove("download_error").putBoolean("auto_install", false).apply()
                _state.value = UpdateState(UpdatePhase.AVAILABLE, cachedRelease())
            } catch (e: Exception) {
                showError(e.message ?: "无法取消下载，请稍后重试")
            }
        }
    }

    fun consumeAutoInstall() {
        preferences.edit().putBoolean("auto_install", false).apply()
        _state.value = _state.value.copy(autoInstall = false)
    }

    fun shouldPrompt(): Boolean = _state.value.hasUpdate &&
        preferences.getString("dismissed_version", null) != _state.value.release?.version?.name

    fun dismissPrompt() {
        preferences.edit().putString("dismissed_version", _state.value.release?.version?.name).apply()
    }

    fun showError(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    suspend fun installerIntent(): Intent = withContext(Dispatchers.IO) {
        mutex.withLock {
            refreshLocked()
            val state = _state.value
            if (state.phase != UpdatePhase.READY) throw IOException(state.message ?: "安装包尚未准备好")
            val file = apkFile(state.downloadId)
            try {
                ApkVerifier(context).verify(file, checkNotNull(state.release))
            } catch (e: Exception) {
                failDownload(state, e.message ?: "安装包校验失败")
                throw e
            }
            Intent(Intent.ACTION_VIEW).setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.updates", file), APK_MIME
            ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun matchesDownload(id: Long): Boolean = id >= 0 && id == preferences.getLong("download_id", -1)

    companion object {
        const val PREFERENCES = "app_updates"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val OPEN_UPDATES = "com.aizeek.phonepulse.OPEN_UPDATES"
    }
}
