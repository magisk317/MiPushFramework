package io.github.magisk317.mipush.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DailyRouteLogQuotaCoreTest {
    @Test
    fun `quota only removes older files from the requested route`() {
        val decision = DailyRouteLogQuotaCore.decide(
            route = "MiPush",
            input = DailyRouteLogQuotaInput(
                currentDay = "2026-08-26",
                incomingBytes = 2,
                maxBytes = 8,
                files = listOf(
                    DailyRouteLogFile("runtime.MiPush.2026-08-25.jsonl", 6),
                    DailyRouteLogFile("runtime.MiPush.2026-08-26.jsonl", 4),
                    DailyRouteLogFile("runtime.manager.2026-08-25.jsonl", 6),
                ),
            ),
        )

        assertTrue(decision.accepted)
        assertEquals(listOf("runtime.MiPush.2026-08-25.jsonl"), decision.filesToDelete)
    }

    @Test
    fun `current file is never selected for deletion`() {
        val decision = DailyRouteLogQuotaCore.decide(
            route = "manager",
            input = DailyRouteLogQuotaInput(
                currentDay = "2026-08-26",
                incomingBytes = 1,
                maxBytes = 8,
                files = listOf(DailyRouteLogFile("runtime.manager.2026-08-26.jsonl", 8)),
            ),
        )

        assertFalse(decision.accepted)
        assertTrue(decision.filesToDelete.isEmpty())
    }
}
