package io.github.magisk317.mipush.service.runtime

import android.content.Intent
import android.os.SystemClock
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.common.utils.Utils
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Stock 7.4.67-C com.xiaomi.push.service.q dedupes only SEND_MESSAGE/UNREGISTER_APP by package plus
 * complete-payload MD5 for 60 seconds. The old transport dedupe keyed registration by package for
 * 30 seconds and could discard a changed registration request, so registration stays record-only.
 */
object StockMiPushPayloadDeduper {
    const val DEDUP_WINDOW_MS = 60_000L

    private val lastSeenAtMs = HashMap<String, Long>()

    @JvmStatic
    fun shouldDrop(intent: Intent?, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return shouldDrop(
            action = intent?.action,
            packageName = intent?.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE),
            payload = intent?.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD),
            nowMs = nowMs,
            userId = Utils.requireValidUserId(Utils.myUserId()),
        )
    }

    internal fun shouldDrop(
        action: String?,
        packageName: String?,
        payload: ByteArray?,
        nowMs: Long,
        userId: Int,
    ): Boolean {
        val scopedUserId = Utils.requireValidUserId(userId)
        if (action != PushConstants.MIPUSH_ACTION_SEND_MESSAGE &&
            action != PushConstants.MIPUSH_ACTION_UNREGISTER_APP
        ) {
            return false
        }
        return shouldDrop(packageName, payload, nowMs, scopedUserId)
    }

    internal fun shouldDrop(
        packageName: String?,
        payload: ByteArray?,
        nowMs: Long,
        userId: Int,
    ): Boolean {
        val scopedUserId = Utils.requireValidUserId(userId)
        if (packageName.isNullOrBlank() || payload == null || payload.isEmpty()) return false
        val digest = runCatching {
            String.format(
                "%1\$032X",
                BigInteger(1, MessageDigest.getInstance("MD5").digest(payload)),
            ).lowercase()
        }.getOrNull()?.takeIf(String::isNotBlank) ?: return false

        synchronized(lastSeenAtMs) {
            val key = "$scopedUserId:$packageName:$digest"
            val duplicate = lastSeenAtMs.containsKey(key)
            if (!duplicate) {
                lastSeenAtMs[key] = nowMs
            }
            // Stock 7.4.67-C q.a() checks for a duplicate before q.b() expires old entries. Keep
            // that ordering so an entry is removed, rather than refreshed, on its final old hit.
            lastSeenAtMs.entries.removeIf { nowMs - it.value > DEDUP_WINDOW_MS }
            return duplicate
        }
    }

    @JvmStatic
    fun reset() = synchronized(lastSeenAtMs) { lastSeenAtMs.clear() }

    @JvmStatic
    fun clearPackageState(packageName: String, userId: Int = Utils.requireValidUserId(Utils.myUserId())) {
        val prefix = "${Utils.requireValidUserId(userId)}:$packageName:"
        synchronized(lastSeenAtMs) {
            lastSeenAtMs.keys.removeIf { it.startsWith(prefix) }
        }
    }
}
