package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.PushConstants
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StockMiPushPayloadDeduperTest {
    @BeforeEach
    fun setUp() {
        StockMiPushPayloadDeduper.reset()
    }

    @Test
    fun `same package and payload is dropped but different payload is admitted`() {
        assertFalse(StockMiPushPayloadDeduper.shouldDrop("com.example.app", byteArrayOf(1), 1_000L))
        assertTrue(StockMiPushPayloadDeduper.shouldDrop("com.example.app", byteArrayOf(1), 2_000L))
        assertFalse(StockMiPushPayloadDeduper.shouldDrop("com.example.app", byteArrayOf(2), 2_000L))
        assertFalse(StockMiPushPayloadDeduper.shouldDrop("com.example.other", byteArrayOf(1), 2_000L))
    }

    @Test
    fun `stock action scope excludes registration and shares the payload key across admitted actions`() {
        val payload = byteArrayOf(1)
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(
                PushConstants.MIPUSH_ACTION_REGISTER_APP,
                "com.example.app",
                payload,
                1_000L,
            ),
        )
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(
                PushConstants.MIPUSH_ACTION_REGISTER_APP,
                "com.example.app",
                payload,
                1_001L,
            ),
        )
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(
                PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
                "com.example.app",
                payload,
                1_002L,
            ),
        )
        assertTrue(
            StockMiPushPayloadDeduper.shouldDrop(
                PushConstants.MIPUSH_ACTION_UNREGISTER_APP,
                "com.example.app",
                payload,
                1_003L,
            ),
        )
    }

    @Test
    fun `stock expiry removes an old entry after its final duplicate hit`() {
        assertFalse(StockMiPushPayloadDeduper.shouldDrop("com.example.app", byteArrayOf(1), 1_000L))
        assertTrue(
            StockMiPushPayloadDeduper.shouldDrop(
                "com.example.app",
                byteArrayOf(1),
                1_000L + StockMiPushPayloadDeduper.DEDUP_WINDOW_MS + 1,
            ),
        )
        assertFalse(
            StockMiPushPayloadDeduper.shouldDrop(
                "com.example.app",
                byteArrayOf(1),
                1_000L + StockMiPushPayloadDeduper.DEDUP_WINDOW_MS + 2,
            ),
        )
    }
}
