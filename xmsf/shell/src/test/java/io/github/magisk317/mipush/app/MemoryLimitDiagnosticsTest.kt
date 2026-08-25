package io.github.magisk317.mipush.app

import android.app.ApplicationExitInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoryLimitDiagnosticsTest {
    @Test
    fun `only memory pressure exit reasons are classified as memory related`() {
        assertTrue(
            MemoryLimitDiagnostics.isMemoryRelatedExitReason(
                ApplicationExitInfo.REASON_LOW_MEMORY,
            ),
        )
        assertTrue(
            MemoryLimitDiagnostics.isMemoryRelatedExitReason(
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ),
        )
        assertFalse(
            MemoryLimitDiagnostics.isMemoryRelatedExitReason(
                ApplicationExitInfo.REASON_CRASH,
            ),
        )
    }

    @Test
    fun `high memory usage keeps the strict eighty percent boundary`() {
        assertFalse(MemoryLimitDiagnostics.isHighMemoryUsage(80L, 100L))
        assertTrue(MemoryLimitDiagnostics.isHighMemoryUsage(81L, 100L))
        assertFalse(MemoryLimitDiagnostics.isHighMemoryUsage(1L, 0L))
        assertFalse(MemoryLimitDiagnostics.isHighMemoryUsage(-1L, 100L))
    }

    @Test
    fun `high memory usage does not overflow for large counters`() {
        val maxBytes = Long.MAX_VALUE
        val threshold = maxBytes - maxBytes / 5L
        assertFalse(
            MemoryLimitDiagnostics.isHighMemoryUsage(
                threshold - 1L,
                maxBytes,
            ),
        )
        assertTrue(
            MemoryLimitDiagnostics.isHighMemoryUsage(
                threshold,
                maxBytes,
            ),
        )
    }
}
