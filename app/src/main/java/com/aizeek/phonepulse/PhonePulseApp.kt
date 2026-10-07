package com.aizeek.phonepulse

import android.app.Application
import com.aizeek.phonepulse.service.ScreenStateHolder

class PhonePulseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ScreenStateHolder.initialize(this)
    }
}
