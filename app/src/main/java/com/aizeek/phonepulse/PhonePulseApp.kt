package com.aizeek.phonepulse

import android.app.Application
import com.aizeek.phonepulse.service.ScreenStateHolder
import com.aizeek.phonepulse.battery.BatteryMonitorRepository
import com.aizeek.phonepulse.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PhonePulseApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val batteryMonitor by lazy { BatteryMonitorRepository(AppDatabase.getInstance(this), applicationScope) }

    override fun onCreate() {
        super.onCreate()
        ScreenStateHolder.initialize(this)
    }
}
