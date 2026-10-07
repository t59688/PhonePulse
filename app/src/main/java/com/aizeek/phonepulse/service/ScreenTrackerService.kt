package com.aizeek.phonepulse.service

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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.aizeek.phonepulse.MainActivity
import com.aizeek.phonepulse.R
import com.aizeek.phonepulse.data.BatteryRepository
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenTrackerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var batteryRepo: BatteryRepository? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    ScreenStateHolder.onScreenStateChanged(ctx, true)
                    updateNotification()
                    recordBatteryPoint()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    ScreenStateHolder.onScreenStateChanged(ctx, false)
                    updateNotification()
                    recordBatteryPoint()
                }
                Intent.ACTION_USER_PRESENT -> {
                    if (!ScreenStateHolder.isScreenOn.value) {
                        ScreenStateHolder.onScreenStateChanged(ctx, true)
                        updateNotification()
                        recordBatteryPoint()
                    }
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    recordBatteryPoint()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        batteryRepo = BatteryRepository(this)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(screenReceiver, filter)

        ScreenStateHolder.setServiceRunning(true)

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

        recordBatteryPoint()
    }

    private fun recordBatteryPoint() {
        serviceScope.launch {
            try {
                batteryRepo?.recordBatterySnapshot()
            } catch (e: Exception) {
                e.printStackTrace()
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
        val startTime = ScreenStateHolder.stateStartTime.value
        val lastOff = ScreenStateHolder.lastScreenOffDuration.value

        val title = if (isScreenOn) "⚡ 屏幕点亮 · 正在计时" else "🌙 屏幕休眠 · 熄屏计时中"

        val contentText = buildString {
            if (isScreenOn) {
                if (lastOff != null) {
                    append("上次熄屏: ${TimeFormatter.formatDurationCompact(lastOff)} ｜ ")
                }
                append("自 ${TimeFormatter.formatTime(startTime)} 持续亮屏")
            } else {
                append("自 ${TimeFormatter.formatTime(startTime)} 开始熄屏休眠")
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

        // Calculate chronometer base for hardware-efficient real-time timer in notification
        val elapsedRealtimeNow = SystemClock.elapsedRealtime()
        val delta = System.currentTimeMillis() - startTime
        val chronometerBase = elapsedRealtimeNow - delta

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setWhen(startTime)
            .setShowWhen(true)
            .setUsesChronometer(true)
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
        serviceJob.cancel()
        ScreenStateHolder.setServiceRunning(false)
    }

    companion object {
        const val CHANNEL_ID = "phone_pulse_service_channel"
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
