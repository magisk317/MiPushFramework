package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventRetentionPolicyTest {
    @Test
    fun `retention limits preserve runtime store policy`() {
        assertEquals(7, EventRetentionPolicy.DEFAULT_RETENTION_DAYS)
        assertEquals(24L * 60L * 60L * 1000L, EventRetentionPolicy.UNDO_RETENTION_MS)
        assertEquals(64, EventRetentionPolicy.MAX_UNDO_EVENTS)
    }

    @Test
    fun `coordinator marks immediate prune as in progress and releases it`() {
        val start = EventRetentionCoordinator.beginNow(
            state = EventRetentionState(),
            nowMillis = 123L,
            retentionDays = 9,
        )

        assertEquals(9, start?.retentionDays)
        assertEquals(123L, start?.state?.lastPruneAtMillis)
        assertEquals(true, start?.state?.pruneInProgress)
        assertEquals(null, EventRetentionCoordinator.beginMaybe(start!!.state, 123L + EventRetentionPolicy.PRUNE_INTERVAL_MS, 9))
        assertEquals(false, EventRetentionCoordinator.finish(start.state).pruneInProgress)
    }

    @Test
    fun `coordinator starts throttled prune only after the interval`() {
        val state = EventRetentionState(lastPruneAtMillis = 1_000L)

        assertEquals(
            null,
            EventRetentionCoordinator.beginMaybe(
                state = state,
                nowMillis = 1_000L + EventRetentionPolicy.PRUNE_INTERVAL_MS - 1L,
                retentionDays = 7,
            ),
        )
        val start = EventRetentionCoordinator.beginMaybe(
            state = state,
            nowMillis = 1_000L + EventRetentionPolicy.PRUNE_INTERVAL_MS,
            retentionDays = 7,
        )
        assertEquals(7, start?.retentionDays)
        assertEquals(true, start?.state?.pruneInProgress)
    }
}
