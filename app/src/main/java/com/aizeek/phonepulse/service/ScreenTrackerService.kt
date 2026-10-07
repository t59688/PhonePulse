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
import androidx.core.app.NotificationCompat
import com.aizeek.phonepulse.MainActivity
import com.aizeek.phonepulse.R
import com.aizeek.phonepulse.data.BatteryRepository
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenTrackerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var batteryRepo: BatteryRepository? = null
    private var tickerJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    ScreenStateHolder.onScreenStateChanged(ctx, true)
                    startTicker()
                    recordBatteryPoint()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    stopTicker()
                    ScreenStateHolder.onScreenStateChanged(ctx, false)
                    updateNotification()
                    recordBatteryPoint()
                }
                Intent.ACTION_USER_PRESENT -> {
                    if (!ScreenStateHolder.isScreenOn.value) {
                        ScreenStateHolder.onScreenStateChanged(ctx, true)
                    }
                    if (tickerJob?.isActive != true) {
                        startTicker()
                    }
                    recordBatteryPoint()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    recordBatteryPoint(intent)
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

        if (ScreenStateHolder.isScreenOn.value) {
            startTicker()
        }

        recordBatteryPoint()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                val isScreenOn = ScreenStateHolder.isScreenOn.value
                if (!isScreenOn) break

                val startTime = ScreenStateHolder.stateStartTime.value
                val currentDuration = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
                if (currentDuration < 60_000L) {
                    // Under 60s: update every second (aligned to the next whole second)
                    val delayMs = 1000L - (currentDuration % 1000L)
                    delay(if (delayMs <= 0L) 1000L else delayMs)
                } else {
                    // 60s and above: update once every minute (aligned to the next whole minute)
                    val delayMs = 60_000L - (currentDuration % 60_000L)
                    delay(if (delayMs <= 0L) 60_000L else delayMs)
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun recordBatteryPoint(batteryIntent: Intent? = null) {
        val repository = batteryRepo ?: return
        val isScreenOn = ScreenStateHolder.isScreenOn.value
        serviceScope.launch {
            try {
                val current = if (batteryIntent != null) repository.readCurrentBattery(batteryIntent)
                    else repository.readCurrentBattery()
                repository.recordBatterySnapshot(current = current, isScreenOn = isScreenOn)
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

        val title = if (isScreenOn) "屏幕点亮 · 正在计时" else "屏幕休眠 · 息屏计时中"
        val currentDuration = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
        val lastOn = ScreenStateHolder.lastScreenOnDuration.value

        val contentText = if (isScreenOn) {
            val lastOffText = lastOff?.let { TimeFormatter.formatSingleUnit(it) } ?: "--"
            val currentOnText = TimeFormatter.formatSingleUnit(currentDuration)
            "上次息屏：$lastOffText | 本次亮屏：$currentOnText"
        } else {
            val lastOnText = lastOn?.let { TimeFormatter.formatSingleUnit(it) } ?: "--"
            val currentOffText = TimeFormatter.formatSingleUnit(currentDuration)
            "上次亮屏：$lastOnText | 本次息屏：$currentOffText"
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
            .setShowWhen(false)
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
        if (ScreenStateHolder.isScreenOn.value) {
            startTicker()
        } else {
            updateNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopTicker()
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
