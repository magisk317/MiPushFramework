package io.github.magisk317.mipush.runtime.core.registration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationComponentPolicyTest {
    private fun component(
        name: String,
        enabled: Boolean = true,
        exported: Boolean = true
    ) = RegistrationComponentInfo(name, enabled, exported)

    @Test
    fun `official service produces a direct SDK plan`() {
        val plan = RegistrationComponentPolicy.classifyForceRegisterPlan(
            packageName = "com.example.direct",
            serviceNames = setOf("com.xiaomi.push.service.XMPushService"),
            receiverNames = emptySet()
        )
        assertTrue(plan.available)
        assertTrue(plan.supportsServiceDispatch)
        assertEquals("direct_sdk", plan.reason)
    }

    @Test
    fun `official receiver without a service produces receiver fallback`() {
        val plan = RegistrationComponentPolicy.classifyForceRegisterPlan(
            packageName = "com.example.receiver",
            serviceNames = emptySet(),
            receiverNames = setOf("com.xiaomi.mipush.sdk.PushServiceReceiver")
        )
        assertFalse(plan.available)
        assertTrue(plan.supportsReceiverFallback)
        assertEquals("receiver_only", plan.reason)
    }

    @Test
    fun `bridge hints are recognized case insensitively`() {
        val plan = RegistrationComponentPolicy.classifyForceRegisterPlan(
            packageName = "com.example.bridge",
            serviceNames = emptySet(),
            receiverNames = setOf("COM.IGEXIN.sdk.MIUiPushReceiver")
        )
        assertEquals("bridge_wrapper", plan.reason)
        assertEquals(setOf("MiuiPushReceiver", "com.igexin"), plan.bridgeCandidates)
    }

    @Test
    fun `unknown components remain unsupported`() {
        val plan = RegistrationComponentPolicy.classifyForceRegisterPlan(
            packageName = "com.example.unknown",
            serviceNames = setOf("com.example.Service"),
            receiverNames = setOf("com.example.Receiver")
        )
        assertFalse(plan.available)
        assertEquals("unsupported_components", plan.reason)
    }

    @Test
    fun `cross package dispatch rejects internal-only handler`() {
        val plan = RegistrationComponentPolicy.resolveForceRegisterPlan(
            packageName = "com.example.direct",
            serviceInfos = setOf(component("com.xiaomi.mipush.sdk.PushMessageHandler", exported = false)),
            receiverInfos = emptySet(),
            sourcePackageName = "com.xiaomi.xmsf"
        )
        assertEquals("internal_only_components", plan.reason)
        assertEquals(setOf("com.xiaomi.mipush.sdk.PushMessageHandler"), plan.blockedServiceCandidates)
    }

    @Test
    fun `same package dispatch allows a non-exported enabled handler`() {
        val plan = RegistrationComponentPolicy.resolveForceRegisterPlan(
            packageName = "com.example.direct",
            serviceInfos = setOf(component("com.xiaomi.mipush.sdk.PushMessageHandler", exported = false)),
            receiverInfos = emptySet(),
            sourcePackageName = "com.example.direct"
        )
        assertTrue(plan.available)
        assertEquals("direct_sdk", plan.reason)
    }

    @Test
    fun `disabled exported receiver remains blocked`() {
        val plan = RegistrationComponentPolicy.resolveForceRegisterPlan(
            packageName = "com.example.receiver",
            serviceInfos = emptySet(),
            receiverInfos = setOf(component("com.xiaomi.mipush.sdk.PushServiceReceiver", enabled = false)),
            sourcePackageName = "com.xiaomi.xmsf"
        )
        assertEquals("internal_only_components", plan.reason)
        assertEquals(setOf("com.xiaomi.mipush.sdk.PushServiceReceiver"), plan.blockedReceiverCandidates)
    }

    @Test
    fun `display classification prioritizes runtime SDK over bridge hints`() {
        assertEquals(
            "direct_sdk",
            RegistrationComponentPolicy.classifyDisplayTypeReason(
                serviceNames = setOf(
                    "com.xiaomi.push.service.XMPushService",
                    "com.example.HeytapPushService"
                ),
                receiverNames = emptySet()
            )
        )
    }
}
