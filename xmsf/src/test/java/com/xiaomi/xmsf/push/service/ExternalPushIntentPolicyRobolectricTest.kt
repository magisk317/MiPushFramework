package com.xiaomi.xmsf.push.service

import android.app.Application
import android.content.Intent
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
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
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                packageName,
                "app-id",
                XmPushActionRegistrationResult("request-id", "app-id", 0L),
                ActionType.Registration,
            ),
        )
        val intent = Intent(PushConstants.MIPUSH_ACTION_REGISTER_APP).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }

        assertNull(ExternalPushIntentPolicy.rejectionReason(context, intent))

        val mismatchedPayload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                "com.example.other",
                "app-id",
                XmPushActionRegistrationResult("request-id", "app-id", 0L),
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
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                packageName,
                "app-id",
                XmPushActionRegistrationResult("request-id", "app-id", 0L),
                ActionType.Notification,
            ),
        )
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
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                packageName,
                "app-id",
                XmPushActionRegistrationResult("request-id", "app-id", 0L),
                ActionType.Notification,
            ),
        )
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
    fun `tiny data cannot claim a different package`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            ClientUploadDataItem().apply {
                channel = "push_sdk_channel"
                category = "category"
                name = "name"
                data = "payload"
                pkgName = "com.example.victim"
            },
        )
        val intent = Intent(PushConstants.MIPUSH_ACTION_SEND_TINYDATA).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }

        assertEquals("tinydata_package_mismatch", ExternalPushIntentPolicy.rejectionReason(context, intent))
    }

    @Test
    fun `tiny data source package must agree with the outer package`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = context.packageName
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            ClientUploadDataItem().apply {
                channel = "push_sdk_channel"
                category = "category"
                name = "name"
                data = "payload"
                sourcePackage = "com.example.victim"
            },
        )
        val intent = Intent(PushConstants.MIPUSH_ACTION_SEND_TINYDATA).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }

        assertEquals("tinydata_source_package_mismatch", ExternalPushIntentPolicy.rejectionReason(context, intent))
    }

    private fun validRegistrationIntent(context: Application): Intent {
        val packageName = context.packageName
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                packageName,
                "app-id",
                XmPushActionRegistrationResult("request-id", "app-id", 0L),
                ActionType.Registration,
            ),
        )
        return Intent(PushConstants.MIPUSH_ACTION_REGISTER_APP).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }
    }
}
