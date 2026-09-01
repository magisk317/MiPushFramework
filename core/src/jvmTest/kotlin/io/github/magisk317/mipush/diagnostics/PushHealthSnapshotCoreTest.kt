package io.github.magisk317.mipush.diagnostics

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushHealthSnapshotCoreTest {
    @Test
    fun `formatter preserves health fields and omits blank extra`() {
        val line = PushHealthSnapshotFormatter.format(snapshot(extra = " "))

        assertTrue(line.contains("stage=stageA"))
        assertTrue(line.contains("runtimeConnection=Connected"))
        assertTrue(line.contains("runtimeDownstream=9"))
        assertFalse(line.contains("extra="))
    }

    @Test
    fun `formatter appends non blank extra`() {
        val line = PushHealthSnapshotFormatter.format(snapshot(extra = "k=v"))

        assertTrue(line.endsWith(" extra=k=v"))
    }

    private fun snapshot(extra: String?) = PushHealthSnapshot(
        stage = "stageA",
        processName = "proc",
        pushEnabled = true,
        regIdPresent = false,
        debugMode = true,
        lifecycleReady = false,
        lifecyclePendingCount = 3,
        runtimeExecutionReady = true,
        runtimeConnectionState = "Connected",
        runtimeTrackedChannels = 2,
        runtimeBoundChannels = 1,
        runtimeTrackedRegistrations = 4,
        runtimeRegisteredPackages = 2,
        runtimeDownstreamCount = 9,
        runtimeDeliveredCount = 7,
        runtimeDuplicateCount = 1,
        runtimeAckCount = 3,
        runtimeBroadcastFallbackCount = 2,
        runtimeNotificationCancelCount = 4,
        runtimeNotificationCount = 5,
        runtimeChannelCount = 6,
        runtimeAccountCount = 2,
        extra = extra,
    )
}
