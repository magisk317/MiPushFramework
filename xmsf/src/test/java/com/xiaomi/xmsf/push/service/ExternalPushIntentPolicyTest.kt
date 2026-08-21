package com.xiaomi.xmsf.push.service

import com.xiaomi.push.service.PushConstants
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class ExternalPushIntentPolicyTest {
    @Test
    fun `external sdk transport actions remain compatible`() {
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_REGISTER_APP))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_SEND_MESSAGE))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_UNREGISTER_APP))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_DISABLE_PUSH))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE))
    }

    @Test
    fun `telemetry and private maintenance actions are rejected`() {
        assertFalse(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_SEND_TINYDATA))
        assertFalse(ExternalPushIntentPolicy.isAllowed(PushConstants.ACTION_RESET_CONNECTION))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.ACTION_OPEN_CHANNEL))
        assertFalse(ExternalPushIntentPolicy.isAllowed(PushConstants.ACTION_CLIENT_REPORT_CONFIG))
        assertFalse(ExternalPushIntentPolicy.isAllowed(null))
    }

    @Test
    fun `bound ingress caller package must match the Messenger sending uid`() {
        assertTrue(
            ExternalPushIntentPolicy.isCallerPackageAllowed(
                arrayOf("com.example.client", "com.example.shared"),
                "com.example.client",
            ),
        )
        assertFalse(
            ExternalPushIntentPolicy.isCallerPackageAllowed(
                arrayOf("com.example.client"),
                "com.example.victim",
            ),
        )
    }

    @Test
    fun `external package names must be bounded application ids`() {
        assertTrue(ExternalPushIntentPolicy.isValidPackageName("com.example.client"))
        assertFalse(ExternalPushIntentPolicy.isValidPackageName("com"))
        assertFalse(ExternalPushIntentPolicy.isValidPackageName("../example"))
        assertFalse(ExternalPushIntentPolicy.isValidPackageName("1com.example"))
        assertFalse(ExternalPushIntentPolicy.isValidPackageName("a." + "b".repeat(254)))
    }

    @Test
    fun `external payload size is bounded below binder limit`() {
        assertFalse(ExternalPushIntentPolicy.isPayloadSizeAllowed(0))
        assertTrue(ExternalPushIntentPolicy.isPayloadSizeAllowed(512 * 1024))
        assertFalse(ExternalPushIntentPolicy.isPayloadSizeAllowed(512 * 1024 + 1))
    }

    @Test
    fun `open channel extras are fully preserved across sanitized copy`() {
        val source = android.content.Intent(PushConstants.ACTION_OPEN_CHANNEL).apply {
            putExtra(PushConstants.EXTRA_CHANNEL_ID, "11")
            putExtra(PushConstants.EXTRA_SECURITY, "test_security_token")
            putExtra(PushConstants.EXTRA_TOKEN, "test_token")
            putExtra(PushConstants.EXTRA_USER_ID, "user_123")
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, "com.example.client")
            putExtra(PushConstants.EXTRA_AUTH_METHOD, "XIAOMI-PASS")
            putExtra(PushConstants.EXTRA_KICK, true)
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, "com.example.client")
        }
        val target = android.content.Intent()
        ExternalPushIntentPolicy.copyAllowedExtras(source, target)

        org.junit.jupiter.api.Assertions.assertEquals("11", target.getStringExtra(PushConstants.EXTRA_CHANNEL_ID))
        org.junit.jupiter.api.Assertions.assertEquals("test_security_token", target.getStringExtra(PushConstants.EXTRA_SECURITY))
        org.junit.jupiter.api.Assertions.assertEquals("test_token", target.getStringExtra(PushConstants.EXTRA_TOKEN))
        org.junit.jupiter.api.Assertions.assertEquals("user_123", target.getStringExtra(PushConstants.EXTRA_USER_ID))
        org.junit.jupiter.api.Assertions.assertEquals("com.example.client", target.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME))
        org.junit.jupiter.api.Assertions.assertEquals("XIAOMI-PASS", target.getStringExtra(PushConstants.EXTRA_AUTH_METHOD))
        org.junit.jupiter.api.Assertions.assertTrue(target.getBooleanExtra(PushConstants.EXTRA_KICK, false))
        org.junit.jupiter.api.Assertions.assertEquals("com.example.client", target.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE))
    }
}

