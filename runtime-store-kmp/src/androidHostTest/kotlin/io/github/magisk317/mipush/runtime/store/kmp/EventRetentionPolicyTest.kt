package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventRetentionPolicyTest {
    @Test
    fun `retention limits preserve runtime store policy`() {
        assertEquals(7, EventRetentionPolicy.DEFAULT_RETENTION_DAYS)
        assertEquals(24L * 60L * 60L * 1000L, EventRetentionPolicy.UNDO_RETENTION_MS)
        assertEquals(64, EventRetentionPolicy.MAX_UNDO_EVENTS)
    }

    @Test
    fun `pruning remains throttled while an existing prune is active`() {
        val now = EventRetentionPolicy.PRUNE_INTERVAL_MS

        assertTrue(EventRetentionPolicy.shouldPrune(now, 0L, pruneInProgress = false))
        assertFalse(EventRetentionPolicy.shouldPrune(now, 0L, pruneInProgress = true))
    }
}
