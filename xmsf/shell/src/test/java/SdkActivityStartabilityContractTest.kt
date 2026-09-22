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
        assertTrue(source.contains("isClickIntentResolvable(context, activityIntent)"))
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
        // to app-side dispatch. Bare-component payloads of other SDKs (QQ JumpActivity) keep
        // the sdk_activity route.
        assertTrue(source.contains("if (isReceiverBuiltClickTarget(intent))"))
        assertTrue(source.contains("cn.richinfo.richpush."))
        assertTrue(source.contains("receiver-built click target, skip"))
    }

    private fun resolveSource(relativePath: String): File =
        listOf(File(relativePath), File("xmsf/shell/$relativePath"))
            .firstOrNull { it.exists() }
            ?: error("source not found: $relativePath")
}
