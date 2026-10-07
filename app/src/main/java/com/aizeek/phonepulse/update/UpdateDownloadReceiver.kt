package com.aizeek.phonepulse.update

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aizeek.phonepulse.MainActivity
import com.aizeek.phonepulse.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val repository = UpdateRepository(context.applicationContext)
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (!repository.matchesDownload(id)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.refresh()
                val state = repository.state.value
                if (state.phase == UpdatePhase.READY || state.phase == UpdatePhase.FAILED) {
                    notifyResult(context, state)
                }
            } catch (e: Exception) {
                Log.e("PhonePulseUpdate", "Unable to process completed update", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyResult(context: Context, state: UpdateState) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "应用更新", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = UpdateRepository.OPEN_UPDATES
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 20, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(20, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle(if (state.phase == UpdatePhase.READY) "PhonePulse ${state.release?.version?.name} 已下载" else "PhonePulse 更新下载失败")
            .setContentText(if (state.phase == UpdatePhase.READY) "点击安装新版本" else state.message)
            .setContentIntent(pendingIntent).setAutoCancel(true).build())
    }

    companion object { const val CHANNEL = "app_updates" }
}
