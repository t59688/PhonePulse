package com.aizeek.phonepulse

import android.app.Application
import android.content.ComponentName
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import com.aizeek.phonepulse.service.ScreenTrackerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], application = Application::class)
class NotificationIconTest {
    @Test
    fun `tracking notification supplies current MIUI app artwork without a secondary image`() {
        val service = Robolectric.buildService(ScreenTrackerService::class.java).get()
        val method = ScreenTrackerService::class.java.getDeclaredMethod("buildNotification")
        method.isAccessible = true
        val notification = method.invoke(service) as android.app.Notification

        assertNull(notification.getLargeIcon())
        assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        @Suppress("DEPRECATION")
        val appIcon = notification.extras.getParcelable<Icon>("miui.appIcon")
        assertNotNull("MIUI needs explicit artwork instead of its themed app icon", appIcon)
        val drawable = appIcon!!.loadDrawable(service) as BitmapDrawable
        val expected = BitmapFactory.decodeResource(service.resources, R.drawable.ic_notification_large)
        assertTrue("MIUI artwork must match the current round icon", expected.sameAs(drawable.bitmap))
    }

    @Test
    fun `system app icon is precomposed while launcher keeps its adaptive icon`() {
        val app = RuntimeEnvironment.getApplication()
        val packageManager = app.packageManager
        val icon = app.getDrawable(app.applicationInfo.icon)
        assertTrue("System surfaces need a precomposed icon", icon is BitmapDrawable)
        val bitmap = (icon as BitmapDrawable).bitmap
        assertEquals("Round icon corners must stay transparent", 0, bitmap.getPixel(0, 0))

        val activity = packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        val launcherIcon = app.getDrawable(activity.iconResource)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertTrue("Launcher must keep its adaptive icon", launcherIcon is AdaptiveIconDrawable)
        } else {
            assertTrue(launcherIcon is BitmapDrawable)
        }
    }
}
