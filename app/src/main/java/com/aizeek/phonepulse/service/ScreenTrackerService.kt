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
import com.aizeek.phonepulse.PhonePulseApp
import com.aizeek.phonepulse.battery.AndroidBatteryTelemetrySource
import com.aizeek.phonepulse.battery.BatteryMonitorRepository
import com.aizeek.phonepulse.battery.BatteryChargeAlarm
import com.aizeek.phonepulse.battery.BatteryTelemetry
import com.aizeek.phonepulse.battery.RecordedBatteryTelemetrySource
import com.aizeek.phonepulse.data.BatteryRepository
import com.aizeek.phonepulse.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import android.util.Log

class ScreenTrackerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var batteryRepo: BatteryRepository? = null
    private var tickerJob: Job? = null
    private lateinit var monitor: BatteryMonitorRepository
    private lateinit var telemetrySource: AndroidBatteryTelemetrySource
    private lateinit var chargeAlarm: BatteryChargeAlarm
    private var samplingJob: Job? = null
    private var samplingConsumer: Job? = null
    private var monitorNotificationJob: Job? = null
    @Volatile private var latestCharging = false
    private var monitoringOwner = 0L
    private data class SampleRequest(val telemetry: BatteryTelemetry, val tick: Boolean)
    private val sampleRequests = Channel<SampleRequest>(Channel.UNLIMITED)
    private val sampleLock = Any()
    private var tickQueued = false

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
        monitor = (application as PhonePulseApp).batteryMonitor
        telemetrySource = AndroidBatteryTelemetrySource(this)
        chargeAlarm = BatteryChargeAlarm(this)
        monitoringOwner = monitor.beginMonitoring()
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(screenReceiver, filter)

        ScreenStateHolder.setServiceRunning(true)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenCurrentlyOn = powerManager?.isInteractive ?: ScreenStateHolder.isScreenOn.value
        ScreenStateHolder.syncScreenState(isScreenCurrentlyOn)

        val initialNotification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
        startSampling()
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
        enqueueSample(batteryIntent)
        val repository = batteryRepo ?: return
        val isScreenOn = ScreenStateHolder.isScreenOn.value
        serviceScope.launch {
            try {
                val current = if (batteryIntent != null) repository.readCurrentBattery(batteryIntent)
                    else repository.readCurrentBattery()
                repository.recordBatterySnapshot(current = current, isScreenOn = isScreenOn)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startSampling() {
        samplingConsumer = serviceScope.launch(Dispatchers.IO) {
            for (request in sampleRequests) {
                if (request.tick) synchronized(sampleLock) { tickQueued = false }
                try {
                    val cycle = monitor.sample(RecordedBatteryTelemetrySource(request.telemetry),
                        request.telemetry.screenOn, monitoringOwner)
                        ?: continue
                    latestCharging = cycle.charging
                    val settings = monitor.settings()
                    if (!cycle.charging || cycle.alarmMuted || !settings.chargeAlarmEnabled) chargeAlarm.cancel()
                    if (cycle.charging && settings.chargeAlarmEnabled && cycle.endPct >= settings.chargeTargetPct) {
                        if (chargeAlarm.canNotify()) {
                            monitor.deliverChargeAlarm(cycle.id, chargeAlarm::show)
                        } else {
                            monitor.reportError("充电提醒通知未获授权或已关闭，请在系统设置中开启")
                        }
                    }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    monitor.reportError("电池采样暂不可用，稍后自动重试")
                    Log.e("BatteryMonitor", "Battery sample failed", e)
                }
            }
        }
        samplingJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                enqueueSample(tick = true)
                delay(if (ScreenStateHolder.isScreenOn.value || latestCharging) 5_000 else 30_000)
            }
        }
        monitorNotificationJob = serviceScope.launch {
            monitor.state.collect { updateNotification() }
        }
    }

    private fun enqueueSample(batteryIntent: Intent? = null, tick: Boolean = false) {
        // Capture timestamps and state together, in enqueue order. Boundaries must never be conflated.
        synchronized(sampleLock) {
            if (tick && tickQueued) return
            try {
                val sample = telemetrySource.readSnapshot(ScreenStateHolder.isScreenOn.value, batteryIntent)
                if (sampleRequests.trySend(SampleRequest(sample, tick)).isSuccess && tick) tickQueued = true
            } catch (e: Exception) {
                monitor.reportError("系统电池读数暂不可用，稍后自动重试")
                Log.e("BatteryMonitor", "Failed to capture battery event", e)
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
        val lastOn = ScreenStateHolder.lastScreenOnDuration.value

        val currentDuration = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
        val currentDurationText = TimeFormatter.formatSingleUnit(currentDuration)

        val lastDurationText = if (isScreenOn) {
            lastOff?.let { TimeFormatter.formatSingleUnit(it) }
        } else {
            lastOn?.let { TimeFormatter.formatSingleUnit(it) }
        }

        val battery = if (::monitor.isInitialized) monitor.state.value else null
        val activeCycle = battery?.activeCycle

        val currentStr = battery?.currentUa?.let {
            String.format(java.util.Locale.getDefault(), "%+.0f mA", it / 1000)
        }

        val stateLabel = if (isScreenOn) "亮屏" else "息屏"

        // 纯单行通知文本（控制在 16~18 字符内，严格保证窄屏不折行）
        val singleLineTitle = buildString {
            append("$stateLabel $currentDurationText")
            if (activeCycle != null) {
                append(" · ${activeCycle.endPct}%")
                if (activeCycle.charging) append("⚡")
            }
            if (currentStr != null) {
                append(" · $currentStr")
            } else if (lastDurationText != null) {
                append(" · 上次 $lastDurationText")
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

        // 仅设置 setContentTitle，不设置 setContentText 和 setStyle，保证通知栏仅占一行高度且单行不换行
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(singleLineTitle)
            .setSmallIcon(R.drawable.ic_notification)
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
        enqueueSample()
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
        samplingJob?.cancel()
        samplingConsumer?.cancel()
        monitorNotificationJob?.cancel()
        sampleRequests.close()
        (application as PhonePulseApp).applicationScope.launch {
            try {
                samplingConsumer?.join()
                monitor.stop(monitoringOwner)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { Log.e("BatteryMonitor", "Failed to save monitor checkpoint", e) }
        }
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
            val appContext = context.applicationContext ?: context
            val intent = Intent(appContext, ScreenTrackerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("ScreenTrackerService", "Failed to start ScreenTrackerService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenTrackerService::class.java)
            context.stopService(intent)
        }
    }
}
