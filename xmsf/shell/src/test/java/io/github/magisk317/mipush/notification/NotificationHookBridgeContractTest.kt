package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationHookBridgeContractTest {
    @Test
    fun exposesVersionedRuntimeContract() {
        assertEquals(1, NotificationHookBridge.HOOK_API_VERSION)
        val methods = NotificationHookBridge::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue("notify" in methods)
        assertTrue("cancel" in methods)
        assertTrue("createNotificationChannels" in methods)
        assertTrue("getNotificationChannels" in methods)
        assertTrue("createNotificationChannelGroups" in methods)
        assertTrue("getNotificationChannelGroups" in methods)
        assertTrue("areNotificationsEnabled" in methods)
        assertTrue("getActiveNotifications" in methods)
        assertTrue("notifyAsTargetPackage" in methods)
        assertTrue("cancelAsTargetPackage" in methods)
    }

    @Test
    fun `notification optional probes preserve missing method for fallback`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/notification/NotificationHookBackend.kt",
        ).readText()

        assertTrue("JavaCalls.callStaticMethodOrThrow" in source)
        assertTrue("JavaCalls.callMethodOrThrow" in source)
        assertTrue("JavaCalls.callStaticMethod(" !in source)
        assertTrue("JavaCalls.callMethod(" !in source)
    }

    @Test
    fun `miui notification field and setter absence is a compatibility boundary`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/notification/NotificationHookBackend.kt",
        ).readText()

        assertTrue("NoSuchFieldException" in source)
        assertTrue("NoSuchMethodException" in source)
        assertFalse("BackendLog.w(TAG, \"extraNotification is null!\")" in source)
    }

    private fun resolveSource(relativePath: String): java.io.File =
        listOf(java.io.File(relativePath), java.io.File("xmsf/shell/$relativePath"))
            .firstOrNull(java.io.File::isFile)
            ?: error("Source not found: $relativePath")
}
