package com.magisk317.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationHelperPlanTest {
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
}
