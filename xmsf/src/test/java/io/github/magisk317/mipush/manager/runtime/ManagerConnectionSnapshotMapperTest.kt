package io.github.magisk317.mipush.manager.runtime

import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManagerConnectionSnapshotMapperTest {
    @Test
    fun `wire snapshot preserves every current connection field`() {
        val source = ManagerConnectionSnapshot(
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

        val wire = source.toWireDto()

        assertEquals(ManagerProtocol.CONNECTION_SNAPSHOT_SCHEMA_VERSION, wire.schemaVersion)
        assertEquals(source.connectionState, wire.connectionState)
        assertEquals(source.connectedAtMs, wire.connectedAtMs)
        assertEquals(source.lastDisconnectedAtMs, wire.lastDisconnectedAtMs)
        assertEquals(source.connectionSessionCount, wire.connectionSessionCount)
        assertEquals(source.serverHost, wire.serverHost)
        assertEquals(source.serverIp, wire.serverIp)
        assertEquals(source.keepAliveIntervalMs, wire.keepAliveIntervalMs)
        assertEquals(source.pingIntervalMs, wire.pingIntervalMs)
        assertEquals(source.downstreamMessageCount, wire.downstreamMessageCount)
        assertEquals(source.deliveredToAppCount, wire.deliveredToAppCount)
        assertEquals(source.duplicateMessageCount, wire.duplicateMessageCount)
        assertEquals(source.ackMessageCount, wire.ackMessageCount)
        assertEquals(source.registeredPackageCount, wire.registeredPackageCount)
        assertEquals(source.trackedChannelCount, wire.trackedChannelCount)
        assertEquals(source.boundChannelCount, wire.boundChannelCount)
    }
}
