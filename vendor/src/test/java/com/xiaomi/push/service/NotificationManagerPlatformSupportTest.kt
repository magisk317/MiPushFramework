import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationManagerPlatformSupportTest {
    @Test
    fun `missing active notification method uses compatibility fallback`() {
        assertTrue(
            com.xiaomi.push.service.NotificationManagerPlatformSupport
                .shouldFallbackActiveNotifications(NoSuchMethodException()),
        )
    }

    @Test
    fun `security failure does not use active notification compatibility fallback`() {
        assertFalse(
            com.xiaomi.push.service.NotificationManagerPlatformSupport
                .shouldFallbackActiveNotifications(SecurityException()),
        )
    }

    @Test
    fun `missing cancel method uses compatibility fallback`() {
        assertTrue(
            com.xiaomi.push.service.NotificationManagerPlatformSupport
                .shouldFallbackNotificationCancel(NoSuchMethodException()),
        )
    }

    @Test
    fun `security failure does not use cancel compatibility fallback`() {
        assertFalse(
            com.xiaomi.push.service.NotificationManagerPlatformSupport
                .shouldFallbackNotificationCancel(SecurityException()),
        )
    }

    @Test
    fun `channel optional probes preserve missing method for fallback`() {
        val source = resolveSource().readText()
        assertTrue("JavaCalls.callMethodOrThrow" in source)
        assertFalse("JavaCalls.callMethod(" in source)
    }

    @Test
    fun `notification fields and app ops avoid warning producing reflection`() {
        val notificationUtils = resolveVendorSource(
            "com/xiaomi/push/service/NotificationUtils.kt",
        ).readText()
        val platformSupport = resolveVendorSource(
            "com/xiaomi/push/service/MIPushNotificationPlatformSupport.kt",
        ).readText()
        val appInfoUtils = resolveVendorSource(
            "com/xiaomi/channel/commonutils/android/AppInfoUtils.kt",
        ).readText()

        assertFalse("JavaCalls.getField(" in notificationUtils)
        assertTrue("JavaCalls.getFieldOrThrow" in notificationUtils)
        assertFalse("JavaCalls.getField(" in platformSupport)
        assertTrue("JavaCalls.getFieldOrThrow" in platformSupport)
        assertFalse("JavaCalls.getStaticField(" in appInfoUtils)
        assertTrue("OP_POST_NOTIFICATION = 11" in appInfoUtils)
        assertTrue("MODE_ALLOWED = 0" in appInfoUtils)
        assertTrue("MODE_IGNORED = 1" in appInfoUtils)
    }

    @Test
    fun `notification compatibility sources avoid warning producing JavaCalls entrypoint`() {
        val relativePaths = listOf(
            "com/xiaomi/push/service/NotificationUtils.kt",
            "com/xiaomi/push/service/NotificationManagerHelper.kt",
            "com/xiaomi/push/service/MIPushNotificationPlatformSupport.kt",
            "com/xiaomi/push/service/MIPushNotificationBuilderSupport.kt",
            "com/xiaomi/push/service/MIPushTopNotificationSupport.kt",
            "com/xiaomi/push/service/NotificationGroupHelper.kt",
            "com/xiaomi/push/service/notification/BuilderCompat.kt",
            "com/xiaomi/push/service/notification/ColorfulBuilder.kt",
            "com/xiaomi/channel/commonutils/android/AppInfoUtils.kt",
        )
        relativePaths.forEach { relativePath ->
            val source = resolveVendorSource(relativePath).readText()
            assertFalse(
                "JavaCalls.callMethod(" in source || "JavaCalls.callStaticMethod(" in source,
                "普通 JavaCalls 入口残留：$relativePath",
            )
        }
    }

    private fun resolveSource(): java.io.File = resolveVendorSource(
        "com/xiaomi/push/service/NotificationManagerPlatformSupport.kt",
    )

    private fun resolveVendorSource(relativePath: String): java.io.File =
        listOf(
            java.io.File("vendor/src/main/java/$relativePath"),
            java.io.File("src/main/java/$relativePath"),
        ).firstOrNull(java.io.File::isFile)
            ?: error("Vendor source not found: $relativePath")
}
