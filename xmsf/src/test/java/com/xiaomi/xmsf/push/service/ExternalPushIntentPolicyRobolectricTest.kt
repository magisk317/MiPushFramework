package com.xiaomi.xmsf.push.service

import android.app.Application
import android.content.Intent
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ExternalPushIntentPolicyRobolectricTest {
    @Test
    fun `serialized container package must match installed outer package`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val payload = registrationPayload(context)
        val intent = Intent(PushConstants.MIPUSH_ACTION_REGISTER_APP).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }

        assertNull(ExternalPushIntentPolicy.rejectionReason(context, intent))

        val mismatchedPayload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                "com.example.other",
                "app-id",
                XmPushActionRegistration("request-id", "app-id", "token").apply {
                    this.packageName = "com.example.other"
                },
                ActionType.Registration,
            ),
        )
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, mismatchedPayload)
        assertEquals("package_mismatch", ExternalPushIntentPolicy.rejectionReason(context, intent))
    }

    @Test
    fun `legacy null action is normalized only for a valid registration payload`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val intent = validRegistrationIntent(context).apply { action = null }

        val accepted = ExternalPushIntentPolicy.validate(context, intent).intent

        assertNotNull(accepted)
        assertEquals(PushConstants.MIPUSH_ACTION_REGISTER_APP, accepted!!.action)
    }

    @Test
    fun `null action with non-registration payload is rejected`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val payload = notificationPayload(context, "command")
        val intent = Intent().apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }

        assertEquals("action_not_public", ExternalPushIntentPolicy.rejectionReason(context, intent))
    }

    @Test
    fun `container action must agree with its public ingress action`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val payload = notificationPayload(context, "command")
        val intent = Intent(PushConstants.MIPUSH_ACTION_REGISTER_APP).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }

        assertEquals("container_action_mismatch", ExternalPushIntentPolicy.rejectionReason(context, intent))
    }

    @Test
    fun `accepted external request is rebuilt with only transport extras`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val intent = validRegistrationIntent(context).apply {
            putExtra(PushConstants.EXTRA_CHANNEL_ID, "forged-channel")
            putExtra("forged_internal_flag", true)
        }

        val accepted = ExternalPushIntentPolicy.validate(context, intent).intent

        assertNotNull(accepted)
        assertEquals(PushConstants.MIPUSH_ACTION_REGISTER_APP, accepted!!.action)
        assertFalse(accepted.hasExtra(PushConstants.EXTRA_CHANNEL_ID))
        assertFalse(accepted.hasExtra("forged_internal_flag"))
        assertNotNull(accepted.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD))
    }

    @Test
    fun `tiny data and notification exposure telemetry are rejected`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val tinyData = Intent(PushConstants.MIPUSH_ACTION_SEND_TINYDATA).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
        }
        val exposure = Intent(PushConstants.MIPUSH_ACTION_SEND_MESSAGE).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, notificationPayload(context, "exposure"))
            putExtra("mipush_message_cache_collection", 1)
        }
        val normalMessage = Intent(exposure).apply {
            putExtra("mipush_message_cache_collection", 0)
        }
        val malformedCollection = Intent(exposure).apply {
            putExtra("mipush_message_cache_collection", "not-an-int")
        }

        assertEquals("telemetry_disabled", ExternalPushIntentPolicy.rejectionReason(context, tinyData))
        assertEquals("telemetry_disabled", ExternalPushIntentPolicy.rejectionReason(context, exposure))
        assertNull(ExternalPushIntentPolicy.rejectionReason(context, normalMessage))
        assertFalse(
            ExternalPushIntentPolicy.validate(context, normalMessage).intent!!
                .hasExtra("mipush_message_cache_collection"),
        )
        assertEquals(
            "telemetry_disabled",
            ExternalPushIntentPolicy.rejectionReason(context, malformedCollection),
        )
    }

    @Test
    fun `stock local notification controls retain only required extras`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val intent = Intent(PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, packageName)
            putExtra(PushConstants.EXTRA_NOTIFY_TYPE, 3)
            putExtra(PushConstants.EXTRA_SIG, MD5.MD5_16(packageName + 3))
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, byteArrayOf(1))
            putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, "forged-app-id")
            putExtra("forged_internal_flag", true)
        }

        val accepted = ExternalPushIntentPolicy.validate(context, intent).intent

        assertNotNull(accepted)
        assertEquals(3, accepted!!.getIntExtra(PushConstants.EXTRA_NOTIFY_TYPE, -1))
        assertFalse(accepted.hasExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD))
        assertFalse(accepted.hasExtra(PushConstants.MIPUSH_EXTRA_APP_ID))
        assertFalse(accepted.hasExtra("forged_internal_flag"))
    }

    @Test
    fun `malformed local notification control values are rejected without throwing`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val setType = Intent(PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, packageName)
            putExtra(PushConstants.EXTRA_NOTIFY_TYPE, "not-an-int")
            putExtra(PushConstants.EXTRA_SIG, MD5.MD5_16(packageName))
        }
        val clear = Intent(PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, packageName)
            putExtra(PushConstants.EXTRA_NOTIFY_ID, "not-an-int")
        }

        assertEquals(
            "invalid_notification_type",
            ExternalPushIntentPolicy.rejectionReason(context, setType),
        )
        assertEquals(
            "invalid_notification_id",
            ExternalPushIntentPolicy.rejectionReason(context, clear),
        )
    }

    @Test
    fun `push state controls require the matching stock notification type`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val intent = Intent(PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(
                PushConstants.MIPUSH_EXTRA_PAYLOAD,
                notificationPayload(context, NotificationType.DisablePushMessage.value),
            )
        }

        assertNull(ExternalPushIntentPolicy.rejectionReason(context, intent))
        intent.putExtra(
            PushConstants.MIPUSH_EXTRA_PAYLOAD,
            notificationPayload(context, NotificationType.EnablePushMessage.value),
        )
        assertEquals("notification_type_mismatch", ExternalPushIntentPolicy.rejectionReason(context, intent))
    }

    private fun validRegistrationIntent(context: Application): Intent {
        val packageName = context.packageName
        return Intent(PushConstants.MIPUSH_ACTION_REGISTER_APP).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, registrationPayload(context))
        }
    }

    private fun registrationPayload(context: Application): ByteArray {
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                context.packageName,
                "app-id",
                XmPushActionRegistration("request-id", "app-id", "token").apply {
                    packageName = context.packageName
                },
                ActionType.Registration,
            ),
        )
    }

    private fun notificationPayload(context: Application, type: String): ByteArray {
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                context.packageName,
                "app-id",
                XmPushActionNotification().apply {
                    setId("notification-id")
                    setAppId("app-id")
                    setType(type)
                    setRequireAck(false)
                },
                ActionType.Notification,
            ),
        )
    }
}
