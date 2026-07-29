package com.xiaomi.xmsf.stock

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class StockNotificationPresentationBridgeTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `presentation defaults to current post time and visible timestamp`() {
        val metaInfo = PushMetaInfo().apply { setMessageTs(42L) }
        val builder = NotificationCompat.Builder(context, "stock-presentation")

        StockNotificationPresentationBridge.apply(metaInfo, builder, nowMs = 12_345L)

        val notification = builder.build()
        assertEquals(12_345L, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertNull(notification.tickerText)
        assertEquals(0L, notification.timeoutAfter)
    }

    @Test
    fun `presentation honors show ticker and positive timeout payloads`() {
        val metaInfo = PushMetaInfo().apply {
            putToExtra("notification_show_when", "false")
            putToExtra("ticker", "Order updated")
            putToExtra("timeout", "15")
        }
        val builder = NotificationCompat.Builder(context, "stock-presentation")

        StockNotificationPresentationBridge.apply(metaInfo, builder, nowMs = 67_890L)

        val notification = builder.build()
        assertEquals(67_890L, notification.`when`)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
        assertEquals("Order updated", notification.tickerText.toString())
        assertEquals(15_000L, notification.timeoutAfter)
    }

    @Test
    fun `presentation ignores malformed and non-positive timeout payloads`() {
        listOf("broken", "0", "-5").forEach { timeout ->
            val metaInfo = PushMetaInfo().apply { putToExtra("timeout", timeout) }
            val builder = NotificationCompat.Builder(context, "stock-presentation")

            StockNotificationPresentationBridge.apply(metaInfo, builder, nowMs = 1L)

            assertEquals(0L, builder.build().timeoutAfter, "timeout=$timeout")
        }
    }
}
