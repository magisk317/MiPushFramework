package io.github.magisk317.mipush.notification

import java.io.File
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
    fun `foreign arbitrary group may use local compatibility fallback`() {
        assertTrue(
            NotificationManagerEx.shouldUseLocalGroupFallback(
                packageName = targetPackage,
                groupId = "unrelated-group",
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
    fun `foreign arbitrary channel may use local compatibility fallback`() {
        assertTrue(
            NotificationManagerEx.shouldUseLocalChannelFallback(
                packageName = targetPackage,
                channelId = "mipush|$targetPackage|contact_chat",
                hostPackageName = hostPackage,
            ),
        )
    }

    @Test
    fun `foreign stock-namespaced group may use local compatibility fallback`() {
        assertTrue(
            NotificationManagerEx.shouldUseLocalGroupFallback(
                packageName = targetPackage,
                groupId = "mipush_${targetPackage}_contact_chat",
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
    fun `invalid requested user cannot fall back to primary user`() {
        assertFalse(NotificationManagerEx.canCancelForUser(requestedUserId = -1, currentUserId = 0))
        assertFalse(NotificationManagerEx.canNotifyForUser(requestedUserId = -1, currentUserId = 0))
        assertFalse(NotificationManagerEx.canNotifyForUser(requestedUserId = 0, currentUserId = -1))
    }

    @Test
    fun `foreign user cancellation fails closed`() {
        assertFalse(NotificationManagerEx.canCancelForUser(requestedUserId = 10, currentUserId = 0))
        assertTrue(NotificationManagerEx.canCancelForUser(requestedUserId = 0, currentUserId = 0))
    }

    @Test
    fun `local cancel security exception is swallowed`() {
        val cancelled = NotificationManagerEx.cancelLocallySafely("tag", 42) {
            throw SecurityException("not the notification owner")
        }

        assertFalse(cancelled)
    }

    @Test
    fun `successful local cancel is reported`() {
        var invoked = false
        val cancelled = NotificationManagerEx.cancelLocallySafely("tag", 42) {
            invoked = true
        }

        assertTrue(cancelled)
        assertTrue(invoked)
    }

    @Test
    fun `foreign user publication fails closed`() {
        assertFalse(NotificationManagerEx.canNotifyForUser(requestedUserId = 10, currentUserId = 0))
        assertTrue(NotificationManagerEx.canNotifyForUser(requestedUserId = 0, currentUserId = 0))
    }

    @Test
    fun `publish remaps unavailable channel through managed fallback`() {
        val source = resolveNotificationControllerSource().readText()

        assertTrue(source.contains("findPublishFallbackChannel(packageName, channelId)"))
        assertTrue(source.contains("notificationBuilder.setChannelId(channelId)"))
        assertTrue(source.contains("publish remapped unavailable channel"))
    }
    @Test
    fun `group cancellation passes resolved user to notification manager`() {
        val source = resolveNotificationControllerSource().readText()
        val groupBranch = source.substringAfter("if (clearGroup) {")
            .substringBefore("\n        }\n    }\n\n    @JvmStatic")

        assertTrue(groupBranch.contains("userId = userId,"))
    }

    private fun resolveNotificationControllerSource(): File {
        val relativePath =
            "src/main/java/io/github/magisk317/mipush/notification/NotificationController.kt"
        return listOf(File(relativePath), File("xmsf/shell/$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("NotificationController.kt not found")
    }
}
