package com.xiaomi.xmsf.push.service

import com.xiaomi.push.service.PushConstants
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalPushIntentPolicyTest {
    @Test
    fun `external sdk transport actions remain compatible`() {
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_REGISTER_APP))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_SEND_MESSAGE))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_UNREGISTER_APP))
        assertTrue(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_SEND_TINYDATA))
    }

    @Test
    fun `external maintenance actions are rejected`() {
        assertFalse(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION))
        assertFalse(ExternalPushIntentPolicy.isAllowed(PushConstants.MIPUSH_ACTION_DISABLE_PUSH))
        assertFalse(ExternalPushIntentPolicy.isAllowed(PushConstants.ACTION_RESET_CONNECTION))
        assertFalse(ExternalPushIntentPolicy.isAllowed(PushConstants.ACTION_OPEN_CHANNEL))
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
}
