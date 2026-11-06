package io.github.magisk317.mipush.notification

import android.app.Notification
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-window content dedup for service-published notifications.
 *
 * Vendors such as com.jingdong.app.mipush-family channels re-push one logical event as two
 * MiPush messages with different messageIds; the stock publish path derives a per-message
 * notificationId, so both posts surface as separate cards (and a focus card update lands as a
 * new session instead of refreshing the existing one). When the same package + channel posts
 * *normalized-identical* title/text/bigText within [MERGE_WINDOW_MS], the new post is
 * re-targeted onto the previously published notificationId so the system updates the existing
 * card in place.
 *
 * False merges are constrained deliberately: only decorative clock stamps and relative-time
 * phrasing are normalized away (HH:mm, "3分钟前", "刚刚", "今天/昨天"), while meaningful dates
 * and tracking/order numbers stay part of the fingerprint; a different package, channel, or any
 * content difference bypasses dedup entirely, and the window is short.
 */
object NotificationDedupPolicy {
    const val MERGE_WINDOW_MS = 60_000L
    internal const val MAX_TRACKED = 512

    private data class Entry(val notificationId: Int, val postedAtMs: Long)

    private val recent = ConcurrentHashMap<String, Entry>()

    private val timePatterns = arrayOf(
        Regex("\\d{1,2}:\\d{2}(:\\d{2})?"),
        Regex("\\d+\\s*(秒钟?|分钟|小时)\\s*(之前|前|以后|后)"),
        Regex("刚刚|刚才"),
        Regex("(今天|昨天|前天)\\s*(凌晨|早上|上午|下午|晚上)?\\s*\\d{1,2}[时点]\\d{0,2}分?"),
        Regex("(今天|昨天|前天)"),
    )

    internal fun normalize(value: String?): String {
        var text = value?.trim() ?: return ""
        timePatterns.forEach { pattern -> text = pattern.replace(text, " ") }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    internal fun fingerprintOf(
        packageName: String,
        channel: String?,
        title: String,
        text: String,
        bigText: String,
    ): String? {
        if (title.isEmpty() && text.isEmpty() && bigText.isEmpty()) return null
        val raw = "$packageName|${channel.orEmpty()}|$title|$text|$bigText"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun fingerprintOf(packageName: String, notification: Notification): String? {
        val extras = notification.extras ?: return null
        return fingerprintOf(
            packageName = packageName,
            channel = notification.channelId,
            title = normalize(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()),
            text = normalize(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()),
            bigText = normalize(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()),
        )
    }

    internal fun mergedNotificationId(fingerprint: String?, notificationId: Int, nowMs: Long): Int {
        fingerprint ?: return notificationId
        val entry = recent[fingerprint] ?: return notificationId
        if (nowMs - entry.postedAtMs > MERGE_WINDOW_MS) {
            recent.remove(fingerprint, entry)
            return notificationId
        }
        return entry.notificationId
    }

    internal fun record(fingerprint: String?, notificationId: Int, nowMs: Long) {
        fingerprint ?: return
        if (recent.size > MAX_TRACKED) {
            val cutoff = nowMs - MERGE_WINDOW_MS
            recent.entries.removeAll { it.value.postedAtMs < cutoff }
        }
        recent[fingerprint] = Entry(notificationId, nowMs)
    }

    internal fun clearForTests() = recent.clear()
}
