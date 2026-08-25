package io.github.magisk317.mipush.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationHelperPlanTest {
    private fun component(
        name: String,
        enabled: Boolean = true,
        exported: Boolean = true
    ) = RegistrationHelper.ComponentDispatchInfo(
        name = name,
        enabled = enabled,
        exported = exported
    )

    @Test
    fun classifyPlanMarksDirectSdkWhenOfficialServiceExists() {
        val plan = RegistrationHelper.classifyForceRegisterPlan(
            packageName = "com.example.direct",
            serviceNames = setOf("com.xiaomi.push.service.XMPushService"),
            receiverNames = emptySet()
        )

        assertTrue(plan.available)
        assertTrue(plan.supportsServiceDispatch)
        assertEquals("direct_sdk", plan.reason)
        assertEquals(setOf("com.xiaomi.push.service.XMPushService"), plan.serviceCandidates)
    }

    @Test
    fun classifyPlanMarksReceiverOnlyWhenOnlyOfficialReceiverExists() {
        val plan = RegistrationHelper.classifyForceRegisterPlan(
            packageName = "com.example.receiver",
            serviceNames = emptySet(),
            receiverNames = setOf("com.xiaomi.mipush.sdk.PushServiceReceiver")
        )

        assertFalse(plan.available)
        assertFalse(plan.supportsServiceDispatch)
        assertTrue(plan.supportsReceiverFallback)
        assertEquals("receiver_only", plan.reason)
        assertEquals(setOf("com.xiaomi.mipush.sdk.PushServiceReceiver"), plan.receiverCandidates)
    }

    @Test
    fun classifyPlanMarksBridgeWrapperForVendorReceiverNames() {
        val plan = RegistrationHelper.classifyForceRegisterPlan(
            packageName = "com.example.bridge",
            serviceNames = emptySet(),
            receiverNames = setOf("com.igexin.sdk.MiuiPushReceiver")
        )

        assertFalse(plan.available)
        assertFalse(plan.supportsServiceDispatch)
        assertFalse(plan.supportsReceiverFallback)
        assertEquals("bridge_wrapper", plan.reason)
        assertEquals(setOf("MiuiPushReceiver", "com.igexin"), plan.bridgeCandidates)
    }

    @Test
    fun classifyPlanMarksUnsupportedWhenNoKnownComponentsExist() {
        val plan = RegistrationHelper.classifyForceRegisterPlan(
            packageName = "com.example.unknown",
            serviceNames = setOf("com.example.Service"),
            receiverNames = setOf("com.example.Receiver")
        )

        assertFalse(plan.available)
        assertEquals("unsupported_components", plan.reason)
        assertTrue(plan.bridgeCandidates.isEmpty())
    }

    @Test
    fun resolvePlanRequiresExportedPushMessageHandlerForCrossPackageDispatch() {
        val plan = RegistrationHelper.resolveForceRegisterPlan(
            packageName = "com.example.direct",
            serviceInfos = setOf(
                component(
                    name = "com.xiaomi.mipush.sdk.PushMessageHandler",
                    exported = false
                )
            ),
            receiverInfos = emptySet(),
            sourcePackageName = "com.xiaomi.xmsf"
        )

        assertFalse(plan.available)
        assertFalse(plan.supportsServiceDispatch)
        assertEquals("internal_only_components", plan.reason)
        assertEquals(setOf("com.xiaomi.mipush.sdk.PushMessageHandler"), plan.blockedServiceCandidates)
    }

    @Test
    fun resolvePlanAllowsReceiverFallbackWhenOfficialReceiverIsExported() {
        val plan = RegistrationHelper.resolveForceRegisterPlan(
            packageName = "com.example.receiver",
            serviceInfos = setOf(
                component(
                    name = "com.xiaomi.mipush.sdk.PushMessageHandler",
                    exported = false
                )
            ),
            receiverInfos = setOf(
                component(name = "com.xiaomi.mipush.sdk.PushServiceReceiver")
            ),
            sourcePackageName = "com.xiaomi.xmsf"
        )

        assertFalse(plan.available)
        assertFalse(plan.supportsServiceDispatch)
        assertTrue(plan.supportsReceiverFallback)
        assertEquals("receiver_only", plan.reason)
        assertEquals(setOf("com.xiaomi.mipush.sdk.PushServiceReceiver"), plan.receiverCandidates)
        assertEquals(setOf("com.xiaomi.mipush.sdk.PushMessageHandler"), plan.blockedServiceCandidates)
    }
}
