package io.github.magisk317.mipush.service

import android.app.Notification
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationsRevivalPolicyTest {
    @Test
    fun `only untagged notifications owned by xmsf are eligible`() {
        val xmsfNotification = statusBarNotification("com.xiaomi.xmsf", null)
        val delegatedNotification = statusBarNotification("com.example.target", null)
        val taggedXmsfNotification = statusBarNotification("com.xiaomi.xmsf", "source")
        val fallbackTargetNotification = statusBarNotification(
            "com.xiaomi.xmsf",
            null,
            targetPackage = "com.example.target",
        )
        val otherUserNotification = statusBarNotification(
            "com.xiaomi.xmsf",
            null,
            userHandle = userHandle(10),
        )

        assertTrue(shouldReviveXmsfNotification(xmsfNotification, "com.xiaomi.xmsf"))
        assertFalse(shouldReviveXmsfNotification(delegatedNotification, "com.xiaomi.xmsf"))
        assertFalse(shouldReviveXmsfNotification(taggedXmsfNotification, "com.xiaomi.xmsf"))
        assertFalse(shouldReviveXmsfNotification(fallbackTargetNotification, "com.xiaomi.xmsf"))
        assertFalse(shouldReviveXmsfNotification(otherUserNotification, "com.xiaomi.xmsf"))
    }

    @Suppress("DEPRECATION")
    private fun statusBarNotification(
        packageName: String,
        tag: String?,
        targetPackage: String? = null,
        userHandle: UserHandle = Process.myUserHandle(),
    ): StatusBarNotification {
        val notification = Notification().apply {
            targetPackage?.let { extras.putString("xmsf_target_package", it) }
        }
        return StatusBarNotification(
            packageName,
            packageName,
            1,
            tag,
            10_000,
            123,
            0,
            notification,
            userHandle,
            1_000L,
        )
    }

    private fun userHandle(identifier: Int): UserHandle =
        UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }.newInstance(identifier)
}
