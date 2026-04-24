package io.github.magisk317.mipush.diagnostics

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushHealthSnapshotLoggerTest {

    @Test
    fun format_containsAllRequiredFields() {
        val snapshot = PushHealthSnapshotLogger.Snapshot(
            stage = "stageA",
            processName = "com.xiaomi.xmsf",
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
            extra = null
        )

        val line = PushHealthSnapshotLogger.format(snapshot)

        assertTrue(line.contains("stage=stageA"))
        assertTrue(line.contains("process=com.xiaomi.xmsf"))
        assertTrue(line.contains("pushEnabled=true"))
        assertTrue(line.contains("regIdPresent=false"))
        assertTrue(line.contains("debugMode=true"))
        assertTrue(line.contains("lifecycleReady=false"))
        assertTrue(line.contains("lifecyclePending=3"))
        assertTrue(line.contains("runtimeExecutionReady=true"))
        assertTrue(line.contains("runtimeConnection=Connected"))
        assertTrue(line.contains("runtimeTrackedChannels=2"))
        assertTrue(line.contains("runtimeBoundChannels=1"))
        assertTrue(line.contains("runtimeTrackedRegs=4"))
        assertTrue(line.contains("runtimeRegistered=2"))
        assertTrue(line.contains("runtimeDownstream=9"))
        assertTrue(line.contains("runtimeDelivered=7"))
        assertTrue(line.contains("runtimeDuplicate=1"))
        assertTrue(line.contains("runtimeAck=3"))
        assertTrue(line.contains("runtimeFallback=2"))
        assertTrue(line.contains("runtimeCancel=4"))
        assertTrue(line.contains("runtimeNotification=5"))
        assertTrue(line.contains("runtimeChannel=6"))
        assertTrue(line.contains("runtimeAccount=2"))
        assertFalse(line.contains("extra="))
    }

    @Test
    fun format_appendsExtraWhenPresent() {
        val snapshot = PushHealthSnapshotLogger.Snapshot(
            stage = "stageB",
            processName = "proc",
            pushEnabled = false,
            regIdPresent = true,
            debugMode = false,
            lifecycleReady = true,
            lifecyclePendingCount = 0,
            runtimeExecutionReady = false,
            runtimeConnectionState = "Idle",
            runtimeTrackedChannels = 0,
            runtimeBoundChannels = 0,
            runtimeTrackedRegistrations = 0,
            runtimeRegisteredPackages = 0,
            runtimeDownstreamCount = 0,
            runtimeDeliveredCount = 0,
            runtimeDuplicateCount = 0,
            runtimeAckCount = 0,
            runtimeBroadcastFallbackCount = 0,
            runtimeNotificationCancelCount = 0,
            runtimeNotificationCount = 0,
            runtimeChannelCount = 0,
            runtimeAccountCount = 0,
            extra = "k=v"
        )

        val line = PushHealthSnapshotLogger.format(snapshot)
        assertTrue(line.contains("extra=k=v"))
    }
}
