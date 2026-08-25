package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationManagerExPolicyTest {
    private val hostPackage = "com.xiaomi.xmsf"
    private val targetPackage = "com.example.target"

    @Test
    fun `notify result keeps target and local ownership distinct`() {
        val target = NotificationManagerEx.NotifyResult(
            posted = true,
            owner = NotificationManagerEx.NotifyOwner.TARGET,
            reason = "identity_target",
        )
        val local = NotificationManagerEx.NotifyResult(
            posted = true,
            owner = NotificationManagerEx.NotifyOwner.LOCAL_XMSF,
            reason = "local_fallback",
        )
        val failed = NotificationManagerEx.NotifyResult(
            posted = false,
            owner = NotificationManagerEx.NotifyOwner.NONE,
            reason = "target-channel-unavailable",
        )

        assertTrue(target.posted)
        assertEquals(NotificationManagerEx.NotifyOwner.TARGET, target.owner)
        assertEquals("identity_target", target.reason)
        assertTrue(local.posted)
        assertEquals(NotificationManagerEx.NotifyOwner.LOCAL_XMSF, local.owner)
        assertEquals("local_fallback", local.reason)
        assertFalse(failed.posted)
        assertEquals(NotificationManagerEx.NotifyOwner.NONE, failed.owner)
        assertEquals("target-channel-unavailable", failed.reason)
    }

    @Test
    fun `unrelated target group cannot use local compatibility fallback`() {
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
