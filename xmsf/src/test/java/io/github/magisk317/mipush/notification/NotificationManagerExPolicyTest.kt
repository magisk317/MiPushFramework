package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationManagerExPolicyTest {
    private val hostPackage = "com.xiaomi.xmsf"
    private val targetPackage = "com.example.target"

    @Test
    fun `foreign arbitrary group cannot fall back to host notification manager`() {
        assertFalse(
            NotificationManagerEx.shouldUseLocalGroupFallback(
                packageName = targetPackage,
                groupId = "gp_unrelated",
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `mipush managed target group may use local compatibility fallback`() {
        assertTrue(
            NotificationManagerEx.shouldUseLocalGroupFallback(
                packageName = targetPackage,
                groupId = "gp_$targetPackage",
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `host group remains locally owned`() {
        assertTrue(
            NotificationManagerEx.shouldUseLocalGroupFallback(
                packageName = hostPackage,
                groupId = "any_group",
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `foreign arbitrary channel cannot fall back to host notification manager`() {
        assertFalse(
            NotificationManagerEx.shouldUseLocalChannelFallback(
                packageName = targetPackage,
                channelId = "foreign-channel",
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `mipush managed target channel may use local compatibility fallback`() {
        assertTrue(
            NotificationManagerEx.shouldUseLocalChannelFallback(
                packageName = targetPackage,
                channelId = "ch_$targetPackage",
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `foreign notification state cannot fall back to host`() {
        assertFalse(
            NotificationManagerEx.shouldUseLocalNotificationStateFallback(
                packageName = targetPackage,
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `host notification state remains locally owned`() {
        assertTrue(
            NotificationManagerEx.shouldUseLocalNotificationStateFallback(
                packageName = hostPackage,
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `foreign user cancellation fails closed`() {
        assertFalse(NotificationManagerEx.canCancelForUser(requestedUserId = 10, currentUserId = 0))
        assertTrue(NotificationManagerEx.canCancelForUser(requestedUserId = 0, currentUserId = 0))
        assertTrue(NotificationManagerEx.canCancelForUser(requestedUserId = -1, currentUserId = 0))
    }

    @Test
    fun `foreign user publication fails closed`() {
        assertFalse(NotificationManagerEx.canNotifyForUser(requestedUserId = 10, currentUserId = 0))
        assertTrue(NotificationManagerEx.canNotifyForUser(requestedUserId = 0, currentUserId = 0))
    }
}
