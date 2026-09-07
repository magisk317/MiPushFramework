package com.xiaomi.xmsf.push.service

import android.content.Intent
import android.os.Bundle
import com.xiaomi.push.service.PushConstants
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun `client report config is rejected before payload validation`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns PushConstants.ACTION_CLIENT_REPORT_CONFIG

        val result = ExternalPushIntentPolicy.validate(context, intent)

        assertEquals("action_not_public", result.rejectionReason)
        assertEquals(null, result.intent)
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
        val sourceExtras = mutableMapOf<String, Any?>(
            PushConstants.EXTRA_CHANNEL_ID to "11",
            PushConstants.EXTRA_SECURITY to "test_security_token",
            PushConstants.EXTRA_TOKEN to "test_token",
            PushConstants.EXTRA_USER_ID to "user_123",
            PushConstants.EXTRA_PACKAGE_NAME to "com.example.client",
            PushConstants.EXTRA_AUTH_METHOD to "XIAOMI-PASS",
            PushConstants.EXTRA_KICK to true,
            PushConstants.MIPUSH_EXTRA_APP_PACKAGE to "com.example.client",
        )
        val source = mockk<Intent>(relaxed = true)
        every { source.action } returns PushConstants.ACTION_OPEN_CHANNEL
        every { source.getStringExtra(any()) } answers { sourceExtras[firstArg()] as? String }
        every { source.getBooleanExtra(any(), any()) } answers {
            (sourceExtras[firstArg()] as? Boolean) ?: secondArg()
        }
        every { source.hasExtra(any()) } answers { sourceExtras.containsKey(firstArg()) }

        val targetExtras = mutableMapOf<String, Any?>()
        val target = mockk<Intent>(relaxed = true)
        every { target.putExtra(any<String>(), any<String>()) } answers {
            targetExtras[firstArg()] = secondArg<String>()
            target
        }
        every { target.putExtra(any<String>(), any<Boolean>()) } answers {
            targetExtras[firstArg()] = secondArg<Boolean>()
            target
        }
        every { target.getStringExtra(any()) } answers { targetExtras[firstArg()] as? String }
        every { target.getBooleanExtra(any(), any()) } answers {
            (targetExtras[firstArg()] as? Boolean) ?: secondArg()
        }

        ExternalPushIntentPolicy.copyAllowedExtras(source, target)

        assertEquals("11", target.getStringExtra(PushConstants.EXTRA_CHANNEL_ID))
        assertEquals("test_security_token", target.getStringExtra(PushConstants.EXTRA_SECURITY))
        assertEquals("test_token", target.getStringExtra(PushConstants.EXTRA_TOKEN))
        assertEquals("user_123", target.getStringExtra(PushConstants.EXTRA_USER_ID))
        assertEquals("com.example.client", target.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME))
        assertEquals("XIAOMI-PASS", target.getStringExtra(PushConstants.EXTRA_AUTH_METHOD))
        assertTrue(target.getBooleanExtra(PushConstants.EXTRA_KICK, false))
        assertEquals("com.example.client", target.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE))
    }
}
