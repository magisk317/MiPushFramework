package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Pure seam tests for the framework smallIcon transport into the existing SystemUI path.
 *
 * **Validates: Requirements 2.2, 2.4, 2.5, 2.9, 3.1, 3.3, 3.6**
 *
 * The seam deliberately accepts only the already-published Notification.smallIcon. It does not
 * resolve an icon pack, inspect extras, read private storage, or create a monochrome bitmap.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class StatusBarSmallIconTransportTest {
    @Test
    fun `framework notification smallIcon reaches status bar by object identity`() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        val publishedIcon = Icon.createWithBitmap(bitmap)
        val notification = Notification.Builder(context, "status-bar-transport-test")
            .setSmallIcon(publishedIcon)
            .setContentTitle("title")
            .build()

        val transport = SystemUiNotificationPolicy.statusBarSmallIconTransport(
            notificationSmallIcon = notification.smallIcon,
            shouldIntercept = true,
        )

        assertEquals(
            SystemUiNotificationPolicy.StatusBarSmallIconSource.FRAMEWORK_NOTIFICATION_SMALL_ICON,
            transport.source,
        )
        assertSame(notification.smallIcon, transport.icon)
        val transportedBitmap =
            (transport.icon!!.loadDrawable(context) as BitmapDrawable).bitmap
        assertSame(bitmap, transportedBitmap)
        assertTrue(transport.entersExistingStatusBarTintPipeline)
    }

    @Test
    fun `monochrome managed icon keeps framework input before existing policy transforms`() {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val icon = Icon.createWithBitmap(bitmap)
        val shouldIntercept = SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
            colorStatusBarIcon = false,
            forceGlobalStatusBarIcons = false,
            isMiPushManaged = true,
            iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
            resId = 0,
            resPackage = null,
            packageName = "com.example.target",
            uid = 10_123,
            isSystemApp = false,
            canColorize = false,
        )

        assertTrue(shouldIntercept)
        val transport = SystemUiNotificationPolicy.statusBarSmallIconTransport(icon, shouldIntercept)
        assertSame(icon, transport.icon)
        assertTrue(transport.entersExistingStatusBarTintPipeline)
        assertTrue(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = true,
            ),
        )
    }

    @Test
    fun `non MiPush color path remains native when the existing guard declines`() {
        val icon = Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888))
        val shouldIntercept = SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
            colorStatusBarIcon = true,
            forceGlobalStatusBarIcons = false,
            isMiPushManaged = false,
            iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
            resId = 0,
            resPackage = null,
            packageName = "com.example.target",
            uid = 10_123,
            isSystemApp = false,
            canColorize = false,
        )

        assertFalse(shouldIntercept)
        val transport = SystemUiNotificationPolicy.statusBarSmallIconTransport(icon, shouldIntercept)
        assertEquals(SystemUiNotificationPolicy.StatusBarSmallIconSource.NATIVE_SYSTEMUI, transport.source)
        assertNull(transport.icon)
        assertFalse(transport.entersExistingStatusBarTintPipeline)
    }

    @Test
    fun `existing tint and guard policy stays scoped to status bar`() {
        assertEquals(
            0xFF808080.toInt(),
            SystemUiNotificationPolicy.globalMonochromeTint(
                requestedColor = 0xFF00FF00.toInt(),
                fallbackColor = 0xFF808080.toInt(),
            ),
        )
        assertEquals(
            0xFFFFFFFF.toInt(),
            SystemUiNotificationPolicy.globalMonochromeTint(
                requestedColor = 0xFFFFFFFF.toInt(),
                fallbackColor = 0xFFFF0000.toInt(),
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.isStatusBarSubstitutionContext(
                listOf("com.android.systemui.statusbar.StatusBarIconView"),
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.isStatusBarSubstitutionContext(
                listOf("com.android.systemui.statusbar.notification.NotificationViewWrapper"),
            ),
        )
    }

    @Test
    fun `resource guard declines malformed framework icon while bitmap path remains interceptable`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = true,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_RESOURCE,
                resId = 0x01070001,
                resPackage = "com.example.target",
                packageName = "com.example.target",
                uid = 10_123,
                isSystemApp = false,
                canColorize = false,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = true,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
                resId = 0,
                resPackage = null,
                packageName = "com.example.target",
                uid = 10_123,
                isSystemApp = false,
                canColorize = false,
            ),
        )
    }
}
