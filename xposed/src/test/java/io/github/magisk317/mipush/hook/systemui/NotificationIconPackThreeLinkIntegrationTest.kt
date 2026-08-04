package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.magisk317.mipush.common.notification.iconpack.ICON_PACK_SOURCE_IDENTITY_EXTRA
import io.github.magisk317.mipush.common.notification.iconpack.thirdPartyPackSourceIdentity
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
 * Task 10 integration seam for the framework Notification.smallIcon -> SystemUI status-bar and
 * header paths. It intentionally does not resolve a provider, Binder/AIDL endpoint or filesystem.
 *
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.5, 2.8, 2.9, 3.1, 3.4, 3.5, 3.6**
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationIconPackThreeLinkIntegrationTest {
    private val targetPackage = "com.example.target"

    @Test
    fun `color and monochrome paths preserve the same bitmap into status bar and header`() {
        val context = RuntimeEnvironment.getApplication()
        listOf(true, false).forEach { colorStatusBarIcon ->
            val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val identity = thirdPartyPackSourceIdentity(targetPackage)
            val notification = Notification.Builder(context, "three-link-$colorStatusBarIcon")
                .setSmallIcon(Icon.createWithBitmap(bitmap))
                .setContentTitle("title")
                .addExtras(Bundle().apply {
                    putString(ICON_PACK_SOURCE_IDENTITY_EXTRA, identity)
                    putString("target_package", targetPackage)
                })
                .build()

            val shouldIntercept = SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = colorStatusBarIcon,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = true,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
                resId = 0,
                resPackage = null,
                packageName = targetPackage,
                uid = 10_123,
                isSystemApp = false,
                canColorize = false,
            )
            assertTrue(shouldIntercept)
            val transport = SystemUiNotificationPolicy.statusBarSmallIconTransport(
                notificationSmallIcon = notification.smallIcon,
                shouldIntercept = shouldIntercept,
            )
            assertSame(notification.smallIcon, transport.icon)
            assertSame(bitmap, (transport.icon!!.loadDrawable(context) as BitmapDrawable).bitmap)
            assertTrue(transport.entersExistingStatusBarTintPipeline)
            assertEquals(
                MiuiHeaderAppIconSource.THIRD_PARTY_PACK,
                MiuiHeaderAppIconPolicy.selectReplacementSource(
                    hasPassedThirdPartySmallIcon = MiuiHeaderAppIconPolicy.isPassedThirdPartySmallIcon(
                        notification.extras.getString(ICON_PACK_SOURCE_IDENTITY_EXTRA),
                        targetPackage,
                    ),
                    hasTargetAppIcon = true,
                ),
            )
            assertEquals(
                !colorStatusBarIcon,
                SystemUiNotificationPolicy.shouldReplaceBitmapWithPackageMonochrome(
                    colorStatusBarIcon = colorStatusBarIcon,
                    isMiPushManaged = true,
                    iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
                ),
            )
        }
    }

    @Test
    fun `blocked or invalid protocol marker selects app fallback without changing native scope`() {
        val context = RuntimeEnvironment.getApplication()
        val appIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
        val notification = Notification.Builder(context, "fallback-three-link")
            .setSmallIcon(appIcon)
            .setContentTitle("unchanged title")
            .addExtras(Bundle().apply { putString("target_package", targetPackage) })
            .build()

        val transport = SystemUiNotificationPolicy.statusBarSmallIconTransport(
            notificationSmallIcon = notification.smallIcon,
            shouldIntercept = true,
        )
        assertSame(notification.smallIcon, transport.icon)
        assertNull(notification.extras.getString(ICON_PACK_SOURCE_IDENTITY_EXTRA))
        assertEquals(
            MiuiHeaderAppIconSource.APP,
            MiuiHeaderAppIconPolicy.selectReplacementSource(
                hasPassedThirdPartySmallIcon = false,
                hasTargetAppIcon = true,
            ),
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.isPassedThirdPartySmallIcon(
                "THIRD_PARTY_PACK(com.example.other)",
                targetPackage,
            ),
        )
    }

    @Test
    fun `header gates preserve non XSpace and non MiPush notifications while allowing mock replay`() {
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = targetPackage,
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
            ),
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 999,
                targetPackage = targetPackage,
                postingPackage = targetPackage,
                hasReplacementIcon = true,
            ),
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 999,
                targetPackage = null,
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
            ),
        )
        assertTrue(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = targetPackage,
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            ),
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.xiaomi.xmsf",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            ),
        )
    }
}
