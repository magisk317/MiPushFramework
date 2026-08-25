package com.xiaomi.xmsf.stock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StockSurfaceBootstrapTest {
    @Test
    fun `service box retry delay follows stock fast attempts and cap`() {
        assertEquals(0L, StockSurfaceBootstrap.retryDelayMs(0))
        assertEquals(2_000L, StockSurfaceBootstrap.retryDelayMs(1))
        assertEquals(16_000L, StockSurfaceBootstrap.retryDelayMs(8))
        assertEquals(300_000L, StockSurfaceBootstrap.retryDelayMs(9))
    }
}
