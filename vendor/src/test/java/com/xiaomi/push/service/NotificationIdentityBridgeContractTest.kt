import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationIdentityBridgeContractTest {
    @Test
    fun `delegated channel queries carry the calling context user`() {
        val source = resolveSource().readText()

        assertTrue(source.contains("getNotificationChannels"))
        assertTrue(source.contains("getNotificationChannelGroups"))
        assertTrue(source.contains("callingUserId(context)"))
        assertTrue(source.contains("appContext(context).packageName, packageName, callingUserId(context)"))
    }

    @Test
    fun `delegated group query supports includeDeleted overload before legacy overload`() {
        val source = resolveSource().readText()

        assertTrue(source.contains("Boolean::class.javaPrimitiveType"))
        assertTrue(source.contains("getNotificationChannelGroups"))
        assertTrue(source.contains("Keep the Android Q-era signature"))
    }
    @Test
    fun `identity bridge does not use warning producing JavaCalls entrypoints`() {
        val source = resolveSource().readText()

        assertFalse("JavaCalls.callMethod(" in source)
        assertFalse("JavaCalls.callStaticMethod(" in source)
        assertTrue("getMethod(\"canNotifyAsPackage\"" in source)
        assertTrue("getMethod(\"getService\"" in source)
    }

    private fun resolveSource(): java.io.File =
        listOf(
            java.io.File("vendor/src/main/java/com/xiaomi/push/service/NotificationIdentityBridge.kt"),
            java.io.File("src/main/java/com/xiaomi/push/service/NotificationIdentityBridge.kt"),
        ).firstOrNull(java.io.File::isFile)
            ?: error("NotificationIdentityBridge.kt not found")
}
