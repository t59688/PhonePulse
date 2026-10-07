package com.aizeek.phonepulse

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import com.aizeek.phonepulse.service.ScreenTrackerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28], application = Application::class)
class NotificationIconTest {
    @Test
    fun `tracking notification uses only the standard Android small icon`() {
        val service = Robolectric.buildService(ScreenTrackerService::class.java).get()
        val method = ScreenTrackerService::class.java.getDeclaredMethod("buildNotification")
        method.isAccessible = true
        val notification = method.invoke(service) as android.app.Notification

        assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        assertNull(notification.getLargeIcon())
        assertFalse(notification.extras.containsKey("miui.appIcon"))
    }

    @Test
    fun `application icon uses adaptive launcher resources on modern Android`() {
        val app = RuntimeEnvironment.getApplication()
        val icon = app.getDrawable(app.applicationInfo.icon)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertTrue(icon is AdaptiveIconDrawable)
        } else {
            assertTrue(icon is BitmapDrawable)
        }

        assertEquals(R.mipmap.ic_launcher, app.applicationInfo.icon)
    }
}
