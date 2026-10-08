package com.aizeek.phonepulse.battery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aizeek.phonepulse.MainActivity
import com.aizeek.phonepulse.PhonePulseApp
import com.aizeek.phonepulse.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class BatteryChargeAlarm(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "充电目标提醒",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "电量达到设定目标时提醒，可在通知中静音"
            enableVibration(true)
        })
    }

    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)

    fun show(percentage: Int, target: Int) {
        val mute = PendingIntent.getBroadcast(context, 0,
            Intent(context, BatteryAlarmMuteReceiver::class.java).setAction(ACTION_MUTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("已达到充电目标 $target%")
            .setContentText("当前电量 $percentage%，可按需要拔下充电器")
            .setContentIntent(open).setDeleteIntent(mute).setAutoCancel(true)
            .addAction(R.drawable.ic_notification, "本次静音", mute)
            .setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build())
    }

    fun cancel() { manager.cancel(ID) }

    companion object {
        const val ID = 1002
        const val CHANNEL = "battery_charge_target"
        const val ACTION_MUTE = "com.aizeek.phonepulse.MUTE_CHARGE_ALARM"
    }
}

class BatteryAlarmMuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BatteryChargeAlarm.ACTION_MUTE) return
        val result = goAsync()
        val app = context.applicationContext as PhonePulseApp
        app.applicationScope.launch {
            try {
                app.batteryMonitor.muteAlarm()
                BatteryChargeAlarm(context).cancel()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                app.batteryMonitor.reportError("提醒静音未保存，请重试", action = true)
                android.util.Log.e("BatteryChargeAlarm", "Failed to mute charge alarm", e)
            } finally { result.finish() }
        }
    }
}
