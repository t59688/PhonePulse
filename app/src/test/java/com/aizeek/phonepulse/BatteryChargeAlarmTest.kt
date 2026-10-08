package com.aizeek.phonepulse

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aizeek.phonepulse.battery.BatteryChargeAlarm
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BatteryChargeAlarmTest {
    @Test @Config(sdk = [24]) fun `target reminder supports minimum Android version without channels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarm = BatteryChargeAlarm(context)
        alarm.show(80, 80)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, manager.size())
        alarm.cancel()
        assertEquals(0, manager.size())
    }
    @Test @Config(sdk = [36]) fun `target reminder has an immutable mute action and separate channel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarm = BatteryChargeAlarm(context)
        alarm.show(90, 90)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val notification = manager.getNotification(BatteryChargeAlarm.ID)
        assertEquals(BatteryChargeAlarm.CHANNEL, notification.channelId)
        assertEquals("本次静音", notification.actions.single().title)
        assertNotNull(notification.actions.single().actionIntent)
        alarm.cancel()
    }
}
