package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.DisconnectReason
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfConnectivityProbe.Companion.isTerminal
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfConnectivityProbe.Companion.toConnectivity
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.Runs
import io.mockk.unmockkConstructor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XmsfConnectivityProbeTest {

    @BeforeEach
    fun setUp() {
        mockkConstructor(ManagerRuntimeClient::class)
        every { anyConstructed<ManagerRuntimeClient>().connect() } just Runs
        every { anyConstructed<ManagerRuntimeClient>().close() } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(ManagerRuntimeClient::class)
    }

    @Test
    fun `terminal availability maps to connectivity outcomes`() {
        val handshake = mockk<io.github.magisk317.mipush.manager.api.ManagerHandshake>(relaxed = true)

        assertEquals(
            XmsfConnectivity.CONNECTED,
            ManagerRuntimeAvailability.Available(handshake).toConnectivity(),
        )
        assertEquals(
            XmsfConnectivity.INCOMPATIBLE,
            ManagerRuntimeAvailability.Incompatible(handshake, "runtime too old").toConnectivity(),
        )
        assertEquals(
            XmsfConnectivity.RUNTIME_MISSING,
            ManagerRuntimeAvailability.RuntimeMissing.toConnectivity(),
        )
        assertEquals(
            XmsfConnectivity.PERMISSION_DENIED,
            ManagerRuntimeAvailability.PermissionDenied.toConnectivity(),
        )
        assertEquals(
            XmsfConnectivity.TIMED_OUT,
            ManagerRuntimeAvailability.TimedOut.toConnectivity(),
        )
        assertEquals(
            XmsfConnectivity.FAILED,
            ManagerRuntimeAvailability.Failed("bind rejected").toConnectivity(),
        )
    }

    @Test
    fun `in-flight states are not terminal and map to UNKNOWN`() {
        assertFalse(ManagerRuntimeAvailability.Disconnected.isTerminal())
        assertFalse(ManagerRuntimeAvailability.Binding.isTerminal())
        assertFalse(
            ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BINDER_DIED).isTerminal(),
        )
        assertTrue(ManagerRuntimeAvailability.RuntimeMissing.isTerminal())
        assertTrue(ManagerRuntimeAvailability.Failed("x").isTerminal())
        assertEquals(
            XmsfConnectivity.UNKNOWN,
            ManagerRuntimeAvailability.Disconnected.toConnectivity(),
        )
        assertEquals(
            XmsfConnectivity.UNKNOWN,
            ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.REMOTE_ERROR).toConnectivity(),
        )
    }

    @Test
    fun `cancellation propagates out of probe instead of becoming FAILED`() = runBlocking {
        every { anyConstructed<ManagerRuntimeClient>().availability } returns
            MutableStateFlow(ManagerRuntimeAvailability.Disconnected)

        val scope = CoroutineScope(Dispatchers.Default)
        val probe = XmsfConnectivityProbe(mockk(relaxed = true), scope)
        val job = scope.launch { probe.probe() }

        // Give the probe time to reach the availability wait, then cancel the
        // calling coroutine: cancellation must escape probe() so the scope can
        // tear down cleanly. A probe that swallowed it would report FAILED and
        // let the job complete normally.
        Thread.sleep(100)
        job.cancel()
        withTimeout(2_000) { job.join() }

        assertTrue(job.isCancelled, "cancellation must propagate out of probe()")
        scope.cancel()
    }
}
