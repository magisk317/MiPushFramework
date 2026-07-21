package io.github.magisk317.mipush.app.runtime

import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerRuntimeProbeTest {
    @Test
    fun `available runtime publishes its typed connection snapshot`() = runBlocking {
        val expected = ManagerRuntimeResult.Success(snapshot())
        val transport = FakeTransport(
            connectedAvailability = available(),
            snapshotResult = expected,
        )
        val probe = probe(transport)

        val result = withTimeout(TEST_TIMEOUT_MS) {
            probe.connectionSnapshot.first { it is ManagerRuntimeResult.Success }
        }

        assertEquals(expected, result)
        assertTrue(probe.availability.value is ManagerRuntimeAvailability.Available)
        assertEquals(1, transport.snapshotRequests)
        probe.close()
    }

    @Test
    fun `missing runtime remains skippable and does not request a snapshot`() = runBlocking {
        val transport = FakeTransport(
            connectedAvailability = ManagerRuntimeAvailability.RuntimeMissing,
        )
        val probe = probe(transport)

        val availability = withTimeout(TEST_TIMEOUT_MS) {
            probe.availability.first { it == ManagerRuntimeAvailability.RuntimeMissing }
        }

        assertEquals(ManagerRuntimeAvailability.RuntimeMissing, availability)
        assertEquals(
            ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.RuntimeMissing),
            probe.connectionSnapshot.value,
        )
        assertEquals(0, transport.snapshotRequests)
        probe.close()
    }

    @Test
    fun `timeout remains skippable and does not request a snapshot`() = runBlocking {
        val transport = FakeTransport(
            connectedAvailability = ManagerRuntimeAvailability.TimedOut,
        )
        val probe = probe(transport)

        val availability = withTimeout(TEST_TIMEOUT_MS) {
            probe.availability.first { it == ManagerRuntimeAvailability.TimedOut }
        }

        assertEquals(ManagerRuntimeAvailability.TimedOut, availability)
        assertEquals(
            ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.TimedOut),
            probe.connectionSnapshot.value,
        )
        assertEquals(0, transport.snapshotRequests)
        probe.close()
    }

    @Test
    fun `unsupported snapshot capability remains a typed nonblocking result`() = runBlocking {
        val unsupported = ManagerRuntimeResult.Unsupported(
            ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
        )
        val transport = FakeTransport(
            connectedAvailability = available(capabilities = emptyList()),
            snapshotResult = unsupported,
        )
        val probe = probe(transport)

        val result = withTimeout(TEST_TIMEOUT_MS) {
            probe.connectionSnapshot.first { it is ManagerRuntimeResult.Unsupported }
        }

        assertEquals(unsupported, result)
        assertTrue(probe.availability.value is ManagerRuntimeAvailability.Available)
        probe.close()
    }

    @Test
    fun `connect failure is contained as a typed failure`() = runBlocking {
        val transport = FakeTransport(connectFailure = IllegalStateException("bind unavailable"))
        val probe = probe(transport)

        val failure = withTimeout(TEST_TIMEOUT_MS) {
            probe.availability.first { it is ManagerRuntimeAvailability.Failed }
        }

        assertEquals(ManagerRuntimeAvailability.Failed("bind unavailable"), failure)
        assertFalse(transport.closed.get())
        probe.close()
    }

    @Test
    fun `close releases transport and resets observable state`() = runBlocking {
        val transport = FakeTransport(connectedAvailability = available())
        val probe = probe(transport)
        withTimeout(TEST_TIMEOUT_MS) {
            probe.availability.first { it is ManagerRuntimeAvailability.Available }
        }

        probe.close()
        probe.close()

        assertTrue(transport.closed.get())
        assertEquals(ManagerRuntimeAvailability.Disconnected, probe.availability.value)
        assertEquals(
            ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.Disconnected),
            probe.connectionSnapshot.value,
        )
    }

    private fun probe(transport: FakeTransport) = DefaultManagerRuntimeProbe(
        dispatcher = Dispatchers.Unconfined,
        transportFactory = { transport },
    )

    private fun available(
        capabilities: List<String> = listOf(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT),
    ) = ManagerRuntimeAvailability.Available(
        ManagerHandshake(
            protocolMajor = ManagerProtocol.MAJOR,
            protocolMinor = ManagerProtocol.MINOR,
            runtimeVersionName = "test",
            runtimeVersionCode = 1L,
            supportedCapabilities = capabilities,
            maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
        ),
    )

    private fun snapshot() = ManagerConnectionSnapshotDto(
        connectionState = ManagerProtocol.CONNECTION_STATE_CONNECTED,
        connectedAtMs = 1L,
        lastDisconnectedAtMs = 0L,
        connectionSessionCount = 1L,
        serverHost = null,
        serverIp = null,
        keepAliveIntervalMs = 300_000,
        pingIntervalMs = 300_000,
        downstreamMessageCount = 2L,
        deliveredToAppCount = 2L,
        duplicateMessageCount = 0L,
        ackMessageCount = 2L,
        registeredPackageCount = 1,
        trackedChannelCount = 1,
        boundChannelCount = 1,
    )

    private class FakeTransport(
        private val connectedAvailability: ManagerRuntimeAvailability = ManagerRuntimeAvailability.Disconnected,
        private val snapshotResult: ManagerRuntimeResult<ManagerConnectionSnapshotDto> =
            ManagerRuntimeResult.Unavailable(connectedAvailability),
        private val connectFailure: RuntimeException? = null,
    ) : ManagerRuntimeTransport {
        private val mutableAvailability = MutableStateFlow<ManagerRuntimeAvailability>(
            ManagerRuntimeAvailability.Disconnected,
        )
        override val availability: StateFlow<ManagerRuntimeAvailability> = mutableAvailability
        val closed = AtomicBoolean(false)
        var snapshotRequests = 0
            private set

        override fun connect() {
            connectFailure?.let { throw it }
            mutableAvailability.value = connectedAvailability
        }

        override suspend fun getConnectionSnapshot(): ManagerRuntimeResult<ManagerConnectionSnapshotDto> {
            snapshotRequests += 1
            return snapshotResult
        }

        override fun close() {
            closed.set(true)
            mutableAvailability.value = ManagerRuntimeAvailability.Disconnected
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 1_000L
    }
}
