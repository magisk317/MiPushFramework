package io.github.magisk317.mipush.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DailyRouteLogNamingPolicyTest {

    @Test
    fun `runtime names distinguish app and named routes`() {
        assertEquals("runtime.2026-08-26.jsonl", DailyRouteLogNamingPolicy.runtimeFileName("", "2026-08-26"))
        assertEquals("runtime.manager.2026-08-26.jsonl", DailyRouteLogNamingPolicy.runtimeFileName("manager", "2026-08-26"))
        assertEquals("runtime.Mi_Push.2026-08-26.jsonl", DailyRouteLogNamingPolicy.runtimeFileName("Mi/Push", "2026-08-26"))
    }

    @Test
    fun `route file matching is route scoped and requires a daily jsonl name`() {
        assertTrue(DailyRouteLogNamingPolicy.isRouteFile("runtime.manager.2026-08-26.jsonl", "manager"))
        assertFalse(DailyRouteLogNamingPolicy.isRouteFile("runtime.2026-08-26.jsonl", "manager"))
        assertFalse(DailyRouteLogNamingPolicy.isRouteFile("runtime.manager.latest.jsonl", "manager"))
    }
}
