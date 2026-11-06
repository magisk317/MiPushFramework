import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackgroundActivityStartContractTest {
    @Test
    fun `listener registration remains present on Android Q and above`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/XMPushServiceAbilityAssembler.kt",
        ).readText()

        assertTrue(source.contains("listeners += BackgroundActivityStartAbility(pushService)"))
        assertTrue(source.contains("Build.VERSION.SDK_INT > Build.VERSION_CODES.P"))
    }

    @Test
    fun `notification target pending intent is recovered and cloned`() {
        val source = resolveSource(
            "xmsf/notification/src/main/java/io/github/magisk317/mipush/notification/MIPushNotificationIntentSupport.kt",
        ).readText()

        assertTrue(source.contains("cloneTargetPendingIntentForBackgroundActivityStart"))
        assertTrue(source.contains("PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE"))
        assertTrue(source.contains("NotificationShellBridge.clonePendingIntentForBackgroundActivityStart"))
    }

    @Test
    fun `message handler consumes the token only for notification clicks and keeps fallback`() {
        val source = resolveSource(
            "src/main/java/com/xiaomi/push/sdk/MyPushMessageHandler.kt",
        ).readText()

        assertTrue(source.contains("MIPushNotificationHelper.FROM_NOTIFICATION"))
        assertTrue(source.contains("tryDispatchWithBackgroundActivityStart"))
        assertTrue(source.contains("pendingIntent.send(this, 0, deliveryIntent)"))
        assertTrue(source.contains("launchApp = true"))
    }

    private fun resolveSource(relativePath: String): File {
        val workingDirectory = System.getProperty("user.dir") ?: "."
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .take(6)
            .flatMap { root ->
                sequenceOf(
                    root.resolve(relativePath),
                    root.resolve("xmsf/shell/$relativePath"),
                )
            }
            .firstOrNull(File::isFile)
            ?: error("source not found: $relativePath from $workingDirectory")
    }
}
