import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Click-marker propagation contract.
 *
 * The notification click route must reach the app-side dispatch tagged with the stock SDK's
 * "mipush_notified" extra. Without it the target app parses the delivery as an arrival
 * (hasNotified=false): it re-posts its own notification and never fires
 * onNotificationMessageClicked, so the payload's notify target (e.g. Baidu XmNotifyActivity)
 * is never started and only the launcher activity opens.
 *
 * Regression guard for: the first click on a tieba push opened MainTabActivity instead of the
 * post page, because XMPushUtils rebuilt the dispatch intent with the literal key
 * "from_notification" (read by no SDK) and dropped the click marker entirely.
 */
class NotificationClickDispatchContractTest {

    @Test
    fun `handler derives the click marker from the notification origin extra`() {
        val source = resolveSource(
            "src/main/java/com/xiaomi/push/sdk/MyPushMessageHandler.kt",
        ).readText()

        assertTrue(
            source.contains(
                "val notified = intent.getBooleanExtra(MIPushNotificationHelper.FROM_NOTIFICATION, false)",
            ),
        )
        assertTrue(source.contains("notified = notified"))
    }

    @Test
    fun `runtime chain carries the click marker from facade to execution host`() {
        val facade = resolveSource(
            "../runtime/src/main/java/io/github/magisk317/mipush/runtime/PushRuntime.kt",
        ).readText()
        assertTrue(facade.contains("notified: Boolean = false,"))
        assertTrue(
            facade.contains("launchApp,\n            notified,\n            androidUserId,"),
        )

        val runtime = resolveSource(
            "../runtime/src/main/java/io/github/magisk317/mipush/runtime/android/AndroidPushRuntime.kt",
        ).readText()
        assertTrue(runtime.contains("notified: Boolean = false,"))
        assertTrue(runtime.contains("notified = notified"))

        val contract = resolveSource(
            "../../core/src/commonMain/kotlin/io/github/magisk317/mipush/runtime/core/PushRuntimeContract.kt",
        ).readText()
        assertTrue(contract.contains("notified: Boolean = false"))
    }

    @Test
    fun `execution bridge forwards the click marker into application delivery`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/runtime/PushRuntimeExecutionBridge.kt",
        ).readText()

        assertTrue(source.contains("notified: Boolean"))
        assertTrue(source.contains("notified = notified,"))
    }

    @Test
    fun `application delivery forwards the click marker into the dispatch intent`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/AppPushMessageProcessor.kt",
        ).readText()

        assertTrue(source.contains("notified: Boolean = false"))
        assertTrue(source.contains("notified = notified,"))
    }

    @Test
    fun `dispatch intent tags click deliveries with the sdk mipush_notified extra`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/platform/support/XMPushUtils.kt",
        ).readText()

        assertTrue(source.contains("notified: Boolean = false"))
        assertTrue(
            source.contains(
                "): Boolean = dispatchToApplicationResult(context, packageName, payload, fromNotification, notified).dispatched",
            ),
        )
        assertTrue(source.contains("putExtra(\"mipush_notified\", true)"))
    }

    @Test
    fun `notification origin extra is the sdk click marker itself`() {
        // FROM_NOTIFICATION must stay aliased to the stock SDK key: the click route reads it
        // off the service intent to decide whether the delivery is a click.
        val source = resolveSource(
            "../../vendor/src/main/java/com/xiaomi/push/service/MIPushNotificationHelper.kt",
        ).readText()

        assertTrue(source.contains("const val FROM_NOTIFICATION = \"mipush_notified\""))
    }

    private fun resolveSource(relativePath: String): File =
        listOf(File(relativePath), File("xmsf/shell/$relativePath"))
            .firstOrNull { it.exists() }
            ?: error("source not found: $relativePath")
}
