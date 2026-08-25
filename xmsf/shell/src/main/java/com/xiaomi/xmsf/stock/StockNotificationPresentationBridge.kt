package com.xiaomi.xmsf.stock

import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo

/**
 * Applies the stock timestamp and payload-controlled presentation fields to a notification.
 *
 * Stock 7.4.67-C t0 starts `when` at the local post time, honors `notification_show_when`, and
 * applies `ticker` plus a positive timeout in seconds. The old product path used the server's
 * message timestamp and always showed it, which made delayed pushes look stale and left bounded
 * top-notification timing inconsistent with the visible notification.
 */
internal object StockNotificationPresentationBridge {
    private const val SHOW_WHEN = "notification_show_when"
    private const val TICKER = "ticker"
    private const val TIMEOUT_SECONDS = "timeout"
    private const val MILLIS_PER_SECOND = 1_000L

    fun apply(
        metaInfo: PushMetaInfo,
        builder: NotificationCompat.Builder,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val source = metaInfo.extra
        builder.setWhen(nowMs)
        builder.setShowWhen(source?.get(SHOW_WHEN)?.takeIf(String::isNotEmpty)?.toBoolean() ?: true)
        source?.get(TICKER)?.let(builder::setTicker)
        source?.get(TIMEOUT_SECONDS)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { builder.setTimeoutAfter(it.toLong() * MILLIS_PER_SECOND) }
    }
}
