package com.aizeek.phonepulse.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aizeek.phonepulse.service.ScreenTrackerService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                ScreenTrackerService.start(ctx)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
