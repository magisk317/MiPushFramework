package io.github.magisk317.mipush.platform.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XMPushUtilsDispatchResultTest {
    @Test
    fun `ServiceStarted is dispatched`() {
        assertTrue(XMPushUtils.DispatchResult.ServiceStarted.dispatched)
    }

    @Test
    fun `BroadcastSent is dispatched regardless of explicit flag`() {
        assertTrue(XMPushUtils.DispatchResult.BroadcastSent(explicit = true).dispatched)
        assertTrue(XMPushUtils.DispatchResult.BroadcastSent(explicit = false).dispatched)
    }

    @Test
    fun `ServiceBlocked is not dispatched`() {
        assertFalse(XMPushUtils.DispatchResult.ServiceBlocked().dispatched)
        assertFalse(XMPushUtils.DispatchResult.ServiceBlocked(RuntimeException("blocked")).dispatched)
    }

    @Test
    fun `generic broadcast is not success without a potential receiver`() {
        val result = XMPushUtils.genericBroadcastResult(
            hasPotentialReceiver = false,
            sendAccepted = true,
            serviceStartError = null,
        )

        assertEquals(XMPushUtils.DispatchResult.ServiceBlocked(), result)
        assertFalse(result.dispatched)
    }

    @Test
    fun `generic broadcast success requires receiver and accepted send`() {
        assertEquals(
            XMPushUtils.DispatchResult.BroadcastSent(explicit = false),
            XMPushUtils.genericBroadcastResult(
                hasPotentialReceiver = true,
                sendAccepted = true,
                serviceStartError = null,
            ),
        )
        assertFalse(
            XMPushUtils.genericBroadcastResult(
                hasPotentialReceiver = true,
                sendAccepted = false,
                serviceStartError = null,
            ).dispatched,
        )
    }

    @Test
    fun `Failed is not dispatched`() {
        assertFalse(XMPushUtils.DispatchResult.Failed.dispatched)
    }
}
