package com.xiaomi.xmsf.stock

import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StockNotificationPresentationBridgeTest {

    @Test
    fun `presentation defaults to current post time and visible timestamp`() {
        val metaInfo = PushMetaInfo().apply { setMessageTs(42L) }
        val builder = mockBuilder()

        StockNotificationPresentationBridge.apply(metaInfo, builder, nowMs = 12_345L)

        verify { builder.setWhen(12_345L) }
        verify { builder.setShowWhen(true) }
        verify(exactly = 0) { builder.setTicker(any()) }
        verify(exactly = 0) { builder.setTimeoutAfter(any()) }
    }

    @Test
    fun `presentation honors show ticker and positive timeout payloads`() {
        val metaInfo = PushMetaInfo().apply {
            putToExtra("notification_show_when", "false")
            putToExtra("ticker", "Order updated")
            putToExtra("timeout", "15")
        }
        val builder = mockBuilder()

        StockNotificationPresentationBridge.apply(metaInfo, builder, nowMs = 67_890L)

        verify { builder.setWhen(67_890L) }
        verify { builder.setShowWhen(false) }
        verify { builder.setTicker("Order updated") }
        verify { builder.setTimeoutAfter(15_000L) }
    }

    @Test
    fun `presentation ignores malformed and non-positive timeout payloads`() {
        listOf("broken", "0", "-5").forEach { timeout ->
            val metaInfo = PushMetaInfo().apply { putToExtra("timeout", timeout) }
            val builder = mockBuilder()

            StockNotificationPresentationBridge.apply(metaInfo, builder, nowMs = 1L)

            verify(exactly = 0) { builder.setTimeoutAfter(any()) }
        }
    }

    private fun mockBuilder(): NotificationCompat.Builder {
        val builder = mockk<NotificationCompat.Builder>(relaxed = true)
        every { builder.setWhen(any()) } returns builder
        every { builder.setShowWhen(any()) } returns builder
        every { builder.setTicker(any<CharSequence>()) } returns builder
        every { builder.setTimeoutAfter(any()) } returns builder
        return builder
    }
}
