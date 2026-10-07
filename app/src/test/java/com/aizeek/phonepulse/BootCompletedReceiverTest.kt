package com.aizeek.phonepulse

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.aizeek.phonepulse.receiver.BootCompletedReceiver
import com.aizeek.phonepulse.service.ScreenTrackerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BootCompletedReceiverTest {

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedService: Intent? = null

        override fun startForegroundService(service: Intent): ComponentName? {
            startedService = service
            return service.component
        }

        override fun startService(service: Intent): ComponentName? {
            startedService = service
            return service.component
        }
    }

    private fun context() = RecordingContext(RuntimeEnvironment.getApplication())

    @Test
    fun `boot completed restores tracking service`() {
        val context = context()

        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(
            ScreenTrackerService::class.java.name,
            context.startedService?.component?.className
        )
    }

    @Test
    fun `package replacement restores tracking service`() {
        val context = context()

        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertEquals(
            ScreenTrackerService::class.java.name,
            context.startedService?.component?.className
        )
    }

    @Test
    fun `unrelated broadcasts do not start tracking service`() {
        val context = context()

        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        assertNull(context.startedService)
    }
}
