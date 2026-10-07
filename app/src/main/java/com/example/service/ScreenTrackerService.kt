package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenTrackerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var notificationUpdateJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    ScreenStateHolder.onScreenStateChanged(ctx, true)
                    updateNotification()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    ScreenStateHolder.onScreenStateChanged(ctx, false)
                    updateNotification()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Screen unlocked
                    if (!ScreenStateHolder.isScreenOn.value) {
                        ScreenStateHolder.onScreenStateChanged(ctx, true)
                        updateNotification()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Register screen on/off receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        ScreenStateHolder.setServiceRunning(true)

        // Start as foreground service
        val initialNotification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // Start periodic notification updater (every 3 seconds to keep notification fresh without battery drain)
        startNotificationUpdater()
    }

    private fun startNotificationUpdater() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(3000)
                updateNotification()
            }
        }
    }

    private fun updateNotification() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(): Notification {
        val isScreenOn = ScreenStateHolder.isScreenOn.value
        val durationMs = ScreenStateHolder.currentDurationMs.value
        val formattedDuration = TimeFormatter.formatDurationCompact(durationMs)
        val lastOff = ScreenStateHolder.lastScreenOffDuration.value

        val title = if (isScreenOn) {
            "⚡ 屏幕已点亮 · 正在计时"
        } else {
            "🌙 屏幕已关闭 · 熄屏计时中"
        }

        val contentText = buildString {
            append("本次状态已持续: $formattedDuration")
            if (lastOff != null && isScreenOn) {
                append(" ｜ 上次熄屏: ${TimeFormatter.formatDurationCompact(lastOff)}")
            }
        }

        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ScreenStateHolder.setServiceRunning(true)
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notificationUpdateJob?.cancel()
        serviceJob.cancel()
        ScreenStateHolder.setServiceRunning(false)
    }

    companion object {
        const val CHANNEL_ID = "screen_pulse_service_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ScreenTrackerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenTrackerService::class.java)
            context.stopService(intent)
        }
    }
}
