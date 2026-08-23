package com.xiaomi.push.service.timers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlarmManagerTimerTest {
    @Test
    fun `forced schedule aligns to the next elapsed realtime interval`() {
        assertEquals(
            1_500L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 0L,
                intervalMs = 500L,
                force = true,
            ),
        )
    }

    @Test
    fun `live future schedule is retained without force`() {
        assertEquals(
            1_600L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 1_600L,
                intervalMs = 500L,
                force = false,
            ),
        )
    }

    @Test
    fun `expired schedule advances once or falls back to a full interval`() {
        assertEquals(
            1_500L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 1_000L,
                intervalMs = 500L,
                force = false,
            ),
        )
        assertEquals(
            1_750L,
            StockAlarmManagerTimer.calculateNextTrigger(
                nowElapsedRealtime = 1_250L,
                currentTriggerElapsedRealtime = 500L,
                intervalMs = 500L,
                force = false,
            ),
        )
    }
}
