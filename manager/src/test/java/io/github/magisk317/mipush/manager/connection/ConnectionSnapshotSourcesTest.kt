package io.github.magisk317.mipush.manager.connection

import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ConnectionSnapshotSourcesTest {
    @Test
    fun `remote source maps every wire field to the manager snapshot`() = runBlocking {
        val wire = wireSnapshot()
        val source = RemoteConnectionSnapshotSource {
            ManagerRuntimeResult.Success(wire)
        }

        val result = source.load()

        assertEquals(ConnectionSnapshotSourceResult.Available(domainSnapshot()), result)
    }

    @Test
    fun `matching snapshots report matched`() = runBlocking {
        val snapshot = domainSnapshot()
        val source = comparingSource(
            remoteResult = ConnectionSnapshotSourceResult.Available(snapshot),
        )

        assertEquals(ConnectionSnapshotComparison.Matched, source.compareRemote(snapshot))
    }

    @Test
    fun `primary snapshot does not wait for or invoke the remote source`() = runBlocking {
        var remoteLoads = 0
        val primary = domainSnapshot()
        val source = ComparingConnectionSnapshotSource(
            inProcessSource = ConnectionSnapshotSource {
                ConnectionSnapshotSourceResult.Available(primary)
            },
            remoteSource = ConnectionSnapshotSource {
                remoteLoads += 1
                ConnectionSnapshotSourceResult.Unavailable(ConnectionSnapshotSourceStatus.TIMED_OUT)
            },
            enableRemoteCompare = true,
        )

        val result = source.loadPrimary()

        assertEquals(ConnectionSnapshotSourceResult.Available(primary), result)
        assertEquals(0, remoteLoads)
        assertEquals(
            ConnectionSnapshotComparison.Skipped(ConnectionSnapshotSourceStatus.TIMED_OUT),
            source.compareRemote(primary),
        )
        assertEquals(1, remoteLoads)
    }

    @Test
    fun `differences expose field names without snapshot values`() = runBlocking {
        val primary = domainSnapshot().copy(
            serverHost = "primary-sensitive-host",
            pingIntervalMs = 101,
        )
        val remote = domainSnapshot().copy(
            serverHost = "remote-sensitive-host",
            pingIntervalMs = 202,
        )
        val source = comparingSource(
            remoteResult = ConnectionSnapshotSourceResult.Available(remote),
        )

        val result = source.compareRemote(primary)

        assertEquals(
            ConnectionSnapshotComparison.Different(
                setOf(
                    ConnectionSnapshotField.SERVER_HOST,
                    ConnectionSnapshotField.PING_INTERVAL_MS,
                ),
            ),
            result,
        )
        assertFalse(result.toString().contains("primary-sensitive-host"))
        assertFalse(result.toString().contains("remote-sensitive-host"))
    }

    @Test
    fun `unsupported remote snapshot is skipped`() = runBlocking {
        val source = RemoteConnectionSnapshotSource {
            ManagerRuntimeResult.Unsupported("sensitive-capability-value")
        }

        val result = source.load()

        assertEquals(
            ConnectionSnapshotSourceResult.Unavailable(ConnectionSnapshotSourceStatus.UNSUPPORTED),
            result,
        )
        assertFalse(result.toString().contains("sensitive-capability-value"))
    }

    @Test
    fun `runtime availability is reduced to a non-sensitive status`() = runBlocking {
        val source = RemoteConnectionSnapshotSource {
            ManagerRuntimeResult.Unavailable(
                ManagerRuntimeAvailability.Incompatible(
                    handshake = io.github.magisk317.mipush.manager.api.ManagerHandshake(
                        protocolMajor = 99,
                        protocolMinor = 0,
                        runtimeVersionCode = 1L,
                        runtimeVersionName = "sensitive-runtime-version",
                        supportedCapabilities = emptyList(),
                        maxPageSize = 1,
                        maxPayloadBytes = 1,
                        compatibilityReason = "sensitive-reason",
                    ),
                    reason = "sensitive-incompatibility-reason",
                ),
            )
        }

        val result = source.load()

        assertEquals(
            ConnectionSnapshotSourceResult.Unavailable(ConnectionSnapshotSourceStatus.INCOMPATIBLE),
            result,
        )
        assertFalse(result.toString().contains("sensitive"))
    }

    @Test
    fun `in-process failure is represented without exception details`() = runBlocking {
        val source = GatewayConnectionSnapshotSource {
            error("sensitive-in-process-error")
        }

        val result = source.load()

        assertEquals(
            ConnectionSnapshotSourceResult.Unavailable(ConnectionSnapshotSourceStatus.FAILED),
            result,
        )
        assertFalse(result.toString().contains("sensitive-in-process-error"))
    }

    private fun comparingSource(
        remoteResult: ConnectionSnapshotSourceResult,
    ): ComparingConnectionSnapshotSource = ComparingConnectionSnapshotSource(
        inProcessSource = ConnectionSnapshotSource {
            ConnectionSnapshotSourceResult.Available(domainSnapshot())
        },
        remoteSource = ConnectionSnapshotSource { remoteResult },
        enableRemoteCompare = true,
    )

    private fun domainSnapshot() = ManagerConnectionSnapshot(
        connectionState = "Connected",
        connectedAtMs = 11L,
        lastDisconnectedAtMs = 12L,
        connectionSessionCount = 13L,
        serverHost = "host.example",
        serverIp = "192.0.2.1",
        keepAliveIntervalMs = 14,
        pingIntervalMs = 15,
        downstreamMessageCount = 16L,
        deliveredToAppCount = 17L,
        duplicateMessageCount = 18L,
        ackMessageCount = 19L,
        registeredPackageCount = 20,
        trackedChannelCount = 21,
        boundChannelCount = 22,
    )

    private fun wireSnapshot() = ManagerConnectionSnapshotDto(
        connectionState = "Connected",
        connectedAtMs = 11L,
        lastDisconnectedAtMs = 12L,
        connectionSessionCount = 13L,
        serverHost = "host.example",
        serverIp = "192.0.2.1",
        keepAliveIntervalMs = 14,
        pingIntervalMs = 15,
        downstreamMessageCount = 16L,
        deliveredToAppCount = 17L,
        duplicateMessageCount = 18L,
        ackMessageCount = 19L,
        registeredPackageCount = 20,
        trackedChannelCount = 21,
        boundChannelCount = 22,
    )
}
