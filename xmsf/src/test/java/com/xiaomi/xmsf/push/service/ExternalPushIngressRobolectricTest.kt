package com.xiaomi.xmsf.push.service

import android.app.Application
import android.content.Intent
import android.os.Message
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import com.xiaomi.push.service.AppRegionStorage
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ExternalPushIngressRobolectricTest {
    @Test
    fun `legacy start transport admits only approved registration and send requests`() {
        val context: Application = RuntimeEnvironment.getApplication()

        assertNotNull(ExternalPushIngress.validateStart(context, registrationIntent(context)).intent)
        assertNotNull(ExternalPushIngress.validateStart(context, sendIntent(context)).intent)
        assertEquals(
            "action_not_public",
            ExternalPushIngress.validateStart(context, Intent(PushConstants.ACTION_CLIENT_REPORT_CONFIG)).rejectionReason,
        )
        assertEquals(
            "action_not_public",
            ExternalPushIngress.validateStart(context, Intent()).rejectionReason,
        )
    }

    @Test
    fun `Messenger transport requires the expected opcode and sender package ownership`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val message = Message.obtain().apply {
            what = ExternalPushIngress.MESSAGE_FORWARD_INTENT
            obj = registrationIntent(context)
            sendingUid = 20_001
        }

        assertNotNull(
            ExternalPushIngress.validateBoundMessage(
                context,
                message,
                callingPackages = arrayOf(context.packageName),
            ).intent,
        )
        assertEquals(
            "caller_package_mismatch",
            ExternalPushIngress.validateBoundMessage(
                context,
                message,
                callingPackages = arrayOf("com.example.other"),
            ).rejectionReason,
        )
        message.what = 19
        assertEquals(
            "unsupported_message",
            ExternalPushIngress.validateBoundMessage(
                context,
                message,
                callingPackages = arrayOf(context.packageName),
            ).rejectionReason,
        )
    }

    @Test
    fun `bound transport rejects unknown sender identity`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val message = Message.obtain().apply {
            what = ExternalPushIngress.MESSAGE_FORWARD_INTENT
            obj = registrationIntent(context)
            sendingUid = -1
        }

        assertNull(ExternalPushIngress.validateBoundMessage(context, message).intent)
        assertEquals("unknown_caller", ExternalPushIngress.validateBoundMessage(context, message).rejectionReason)
    }

    @Test
    fun `region request replies on stock Messenger opcode without forwarding an intent`() {
        var reply: Message? = null
        val replyTo = Messenger(
            Handler(Looper.getMainLooper()) { message ->
                reply = Message.obtain(message)
                true
            },
        )
        val request = Message.obtain().apply {
            what = ExternalPushIngress.MESSAGE_REGION_REQUEST
            this.replyTo = replyTo
            obj = Intent(PushConstants.ACTION_RESET_CONNECTION)
        }

        assertEquals(true, ExternalPushIngress.replyRegion(request, "CN"))
        shadowOf(Looper.getMainLooper()).idle()

        val capturedReply = requireNotNull(reply)
        assertEquals(ExternalPushIngress.MESSAGE_REGION_REQUEST, capturedReply.what)
        assertEquals("CN", capturedReply.data.getString(PushConstants.MESSAGE_KEY_XMSF_REGION))
    }

    @Test
    fun `region reply falls back to persistent storage before the Core starts`() {
        val context: Application = RuntimeEnvironment.getApplication()
        AppRegionStorage.getInstance(context).setRegion("IN")

        assertEquals("IN", ExternalPushIngress.resolveRegion(context, null))
        assertEquals("CN", ExternalPushIngress.resolveRegion(context, "CN"))
    }

    private fun registrationIntent(context: Application): Intent = intentFor(
        context = context,
        action = PushConstants.MIPUSH_ACTION_REGISTER_APP,
        containerAction = ActionType.Registration,
    )

    private fun sendIntent(context: Application): Intent = intentFor(
        context = context,
        action = PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
        containerAction = ActionType.Notification,
    )

    private fun intentFor(context: Application, action: String, containerAction: ActionType): Intent {
        val packageName = context.packageName
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                packageName,
                "app-id",
                XmPushActionRegistrationResult("request-id", "app-id", 0L),
                containerAction,
            ),
        )
        return Intent(action).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }
    }
}
