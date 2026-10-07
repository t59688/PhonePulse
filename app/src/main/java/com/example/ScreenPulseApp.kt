package com.example

import android.app.Application
import com.example.data.ScreenStateRepository
import com.example.service.ScreenStateHolder
import com.example.service.ScreenTrackerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenPulseApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Initialize reactive screen state holder
        ScreenStateHolder.initialize(this)

        // Seed demo sessions if database is fresh
        appScope.launch {
            try {
                val repo = ScreenStateRepository(this@ScreenPulseApp)
                repo.seedDemoSessionsIfEmpty()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Start background foreground tracking service
        try {
            ScreenTrackerService.start(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
