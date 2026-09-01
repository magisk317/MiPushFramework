package io.github.magisk317.mipush.manager.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManagerRuntimeSnapshotsTest {
    @Test
    fun `connection snapshot retains compatibility defaults`() {
        val snapshot = ManagerConnectionSnapshot(
            connectionState = "Connected",
            connectedAtMs = 10L,
            lastDisconnectedAtMs = 0L,
            connectionSessionCount = 1L,
            serverHost = "host",
            serverIp = null,
            keepAliveIntervalMs = 600_000,
            pingIntervalMs = 600_000,
            downstreamMessageCount = 1L,
            deliveredToAppCount = 1L,
            duplicateMessageCount = 0L,
            ackMessageCount = 1L,
            registeredPackageCount = 1,
            trackedChannelCount = 1,
            boundChannelCount = 1,
        )

        assertEquals("com.xiaomi.xmsf", snapshot.checkedPackageName)
        assertEquals(0L, snapshot.lastReconnectConnectedAtMs)
    }

    @Test
    fun `environment snapshot is a pure value`() {
        assertEquals(
            ManagerRuntimeEnvironmentSnapshot(1, null, null, "host"),
            ManagerRuntimeEnvironmentSnapshot(1, null, null, "host"),
        )
    }
}
