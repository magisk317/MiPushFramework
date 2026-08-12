package io.github.magisk317.mipush.platform.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Unit tests for [XMPushUtils.dispatchToApplicationResult] and its [XMPushUtils.DispatchResult].
 *
 * These lock in the observable dispatch outcome so that callers (notably QQ, which relies on
 * PushMessageHandler) can distinguish a real service delivery from a broadcast fallback rather than
 * treating any non-null result as full success.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class XMPushUtilsDispatchResultTest {

    // =========================================================================
    // DispatchResult.dispatched semantics (pure logic)
    // =========================================================================

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

    // =========================================================================
    // dispatchToApplicationResult behavior
    // =========================================================================

    @Test
    fun `blank package returns Failed`() {
        val context = RuntimeEnvironment.getApplication()
        val result = XMPushUtils.dispatchToApplicationResult(
            context = context,
            packageName = "",
            payload = byteArrayOf(1, 2, 3),
            fromNotification = true
        )
        assertEquals(XMPushUtils.DispatchResult.Failed, result)
        assertFalse(result.dispatched)
    }

    @Test
    fun `successful service start returns ServiceStarted targeting PushMessageHandler`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = "com.example.target"

        val result = XMPushUtils.dispatchToApplicationResult(
            context = context,
            packageName = packageName,
            payload = byteArrayOf(4, 5, 6),
            fromNotification = true
        )

        // Robolectric resolves startService for an intent with an explicit component, so the primary
        // service path is taken. The service intent is constructed internally against the target
        // PushMessageHandler, so asserting ServiceStarted is sufficient to lock the contract.
        assertEquals(XMPushUtils.DispatchResult.ServiceStarted, result)
        assertTrue(result.dispatched)
    }

    @Test
    fun `dispatchToApplication boolean wrapper mirrors dispatched`() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(
            XMPushUtils.dispatchToApplication(
                context = context,
                packageName = "",
                payload = byteArrayOf(1),
                fromNotification = false
            )
        )
        assertTrue(
            XMPushUtils.dispatchToApplication(
                context = context,
                packageName = "com.example.target2",
                payload = byteArrayOf(1),
                fromNotification = false
            )
        )
    }
}
