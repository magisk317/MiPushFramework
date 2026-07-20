package io.github.magisk317.mipush.hook.amap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AmapFocusBypassGateTest {
    @Test
    fun `missing preference fails closed`() {
        val gate = AmapFocusBypassGate(readEnabled = { null })

        assertFalse(gate.isEnabled())
    }

    @Test
    fun `preference is cached only for the bounded window`() {
        var now = 100L
        var reads = 0
        var enabled = true
        val gate = AmapFocusBypassGate(
            readEnabled = {
                reads++
                enabled
            },
            nowMillis = { now },
            cacheMillis = 10L,
        )

        assertTrue(gate.isEnabled())
        enabled = false
        now = 109L
        assertTrue(gate.isEnabled())
        assertEquals(1, reads)

        now = 110L
        assertFalse(gate.isEnabled())
        assertEquals(2, reads)
    }

    @Test
    fun `invalidation observes a changed switch immediately`() {
        var enabled = true
        val gate = AmapFocusBypassGate(
            readEnabled = { enabled },
            nowMillis = { 1L },
        )

        assertTrue(gate.isEnabled())
        enabled = false
        gate.invalidate()
        assertFalse(gate.isEnabled())
    }
}
