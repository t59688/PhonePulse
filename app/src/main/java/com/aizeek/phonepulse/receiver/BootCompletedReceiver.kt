package com.aizeek.phonepulse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aizeek.phonepulse.service.ScreenTrackerService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val shouldRestoreTracking = when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> true
            else -> false
        }
        if (!shouldRestoreTracking) return

        try {
            ScreenTrackerService.start(ctx)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
