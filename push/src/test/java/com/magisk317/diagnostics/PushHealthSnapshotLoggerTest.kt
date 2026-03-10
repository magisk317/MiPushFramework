package com.magisk317.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
            extra = "k=v"
        )

        val line = PushHealthSnapshotLogger.format(snapshot)
        assertTrue(line.contains("extra=k=v"))
    }
}
