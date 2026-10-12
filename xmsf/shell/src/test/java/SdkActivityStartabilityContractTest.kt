import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SdkActivityStartabilityContractTest {
    @Test
    fun `payload-declared sdk activity is gated on cross-uid startability`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationIntentSupport.kt",
        ).readText()

        assertTrue(source.contains("isStartableFromXmsfProcess"))
        assertTrue(source.contains("if (!activityInfo.exported) return false"))
        assertTrue(source.contains("sdk activity not startable from xmsf"))
    }

    @Test
    fun `click route keeps falling through to bridge and xmsf service dispatch when sdk activity is unusable`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationIntentSupport.kt",
        ).readText()

        // The sdk_activity route is only taken when an intent survives the gate; the app-side
        // routes (BridgeActivity, then the XMSF PushMessageHandler service dispatch) remain the
        // fallback for payloads pointing at non-exported components (e.g. Baidu XmNotifyActivity).
        assertTrue(source.contains("val activityIntent = getSdkIntent(context, container)"))
        assertTrue(source.contains("buildBridgePendingIntent("))
        assertTrue(source.contains("logClickRoute(\"xmsf_service\""))
    }

    @Test
    fun `sdk click intent is re-resolved after the tracking identity is applied`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationIntentSupport.kt",
        ).readText()

        // The mipush-action:// identity data breaks filter matching for payload activities whose
        // filters declare no <data> (Meituan HWPushDetailActivity fails with AMS result code=-91
        // at click time). The sdk_activity route must re-resolve the final intent and drop the
        // identity data when it breaks resolution, before falling through to the app-side routes.
        assertTrue(source.contains("isClickIntentResolvable(context, activityIntent, container.packageName)"))
        assertTrue(source.contains("activityIntent.data = null"))
        assertTrue(source.contains("logClickRoute(\"sdk_activity_nodata\""))
        assertTrue(source.contains("sdk click intent unresolved after identity drop, fall through"))
    }

    @Test
    fun `receiver-built click targets never take the sdk activity route`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationIntentSupport.kt",
        ).readText()

        // The RichPush SDK (cn.richinfo.richpush, e.g. China Mobile) rebuilds the click landing
        // intent inside the app's own XiaoMiMessageReceiver: the payload's intent_uri names
        // ClickResultActivity as a bare component whose landing parameters only exist in the
        // payload itself. Launching it from XMSF NPEs inside the app (missing richpush://
        // data), so the intent must be reported unavailable and every caller must fall back
        // to app-side dispatch. Bare-component payloads of other SDKs keep the sdk_activity
        // route, except QQ's JumpActivity, which joined this list on 2026-10-12 after a
        // device trace showed it finishes without navigating.
        assertTrue(source.contains("if (isReceiverBuiltClickTarget(intent))"))
        assertTrue(source.contains("cn.richinfo.richpush."))
        assertTrue(source.contains("receiver-built click target, skip"))
    }

    @Test
    fun `client data jump url is only tried once the sdk activity route is unusable`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationIntentSupport.kt",
        ).readText()

        // Some clients (QQ) keep the landing address in client_data.url while intent_uri only
        // names a bare component. That address is the one that reaches the intended screen, so
        // it must be tried BEFORE the sdk_activity block: a bare-component start only produces
        // an empty shell activity that finishes without navigating and would consume the click.
        val jumpIndex = source.indexOf("resolveClientDataJumpUrl(context, metaInfo")
        val sdkIndex = source.indexOf("val activityIntent = getSdkIntent(context, container)")
        val bridgeIndex = source.indexOf("val bridgePendingIntent = buildBridgePendingIntent(")
        assertTrue(jumpIndex >= 0)
        assertTrue(sdkIndex >= 0)
        assertTrue(bridgeIndex >= 0)
        assertTrue(jumpIndex < sdkIndex)
        assertTrue(jumpIndex < bridgeIndex)
        assertTrue(source.contains("client_data_url"))
        assertTrue(source.contains("resolveClientDataJumpUrl"))
    }

    @Test
    fun `qq jump activity is classified as a receiver built click target`() {
        val source = resolveSource(
            "src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationIntentSupport.kt",
        ).readText()

        assertTrue(source.contains("\"com.tencent.mobileqq.activity.JumpActivity\""))
    }

    private fun resolveSource(relativePath: String): File =
        listOf(File(relativePath), File("xmsf/shell/$relativePath"))
            .firstOrNull { it.exists() }
            ?: error("source not found: $relativePath")
}
