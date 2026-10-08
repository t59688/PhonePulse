package com.aizeek.phonepulse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aizeek.phonepulse.service.ScreenTrackerService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val action = intent?.action
        Log.i(TAG, "BootCompletedReceiver received broadcast: $action")

        val shouldRestoreTracking = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_REBOOT,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> true
            else -> false
        }
        if (!shouldRestoreTracking) return

        try {
            ScreenTrackerService.start(ctx)
            Log.i(TAG, "ScreenTrackerService.start() successfully invoked on action: $action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenTrackerService on action: $action", e)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
