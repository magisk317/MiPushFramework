package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeepAliveTimingPolicyTest {
    @Test
    fun `uses stock default below the minimum calm down period`() {
        assertEquals(5_000L, KeepAliveTimingPolicy.effectiveCalmDownMs(-1))
        assertEquals(5_000L, KeepAliveTimingPolicy.effectiveCalmDownMs(1_999))
    }

    @Test
    fun `keeps configured period at and above stock minimum`() {
        assertEquals(2_000L, KeepAliveTimingPolicy.effectiveCalmDownMs(2_000))
        assertEquals(12_345L, KeepAliveTimingPolicy.effectiveCalmDownMs(12_345))
    }

    @Test
    fun `reconciliation requires all keepalive gates and a strategy`() {
        assertEquals(true, KeepAliveTimingPolicy.shouldReconcile(true, true, true, 1))
        assertEquals(false, KeepAliveTimingPolicy.shouldReconcile(false, true, true, 1))
        assertEquals(false, KeepAliveTimingPolicy.shouldReconcile(true, false, true, 1))
        assertEquals(false, KeepAliveTimingPolicy.shouldReconcile(true, true, false, 1))
        assertEquals(false, KeepAliveTimingPolicy.shouldReconcile(true, true, true, 0))
    }
}
