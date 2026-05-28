package io.github.magisk317.mipush.push.pipeline

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.platform.support.XMPushUtils
import java.security.MessageDigest

object MockMessageRegistry {
    private const val MARK_TTL_MS = 30_000L
    private val lock = Any()
    private val markedMessageIds = LinkedHashMap<String, Long>()

    @JvmStatic
    fun mark(container: XmPushActionContainer?) {
        val id = identityOf(container) ?: return
        markMessageId(id)
    }

    @JvmStatic
    fun markMessageId(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pruneExpiredLocked(now)
            markedMessageIds[messageId] = now
        }
        logD("marked mock message id=$messageId ttlMs=$MARK_TTL_MS")
    }

    @JvmStatic
    fun isMarked(container: XmPushActionContainer?): Boolean {
        val id = identityOf(container) ?: return false
        return isMarked(id)
    }

    @JvmStatic
    fun isMarked(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pruneExpiredLocked(now)
            val ts = markedMessageIds[messageId] ?: return false
            if ((now - ts) > MARK_TTL_MS) {
                markedMessageIds.remove(messageId)
                return false
            }
            return true
        }
    }

    @JvmStatic
    fun clear(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        synchronized(lock) {
            markedMessageIds.remove(messageId)
        }
    }

    @JvmStatic
    fun clearAllForTests() {
        synchronized(lock) {
            markedMessageIds.clear()
        }
    }

    private fun pruneExpiredLocked(now: Long) {
        val iterator = markedMessageIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((now - entry.value) > MARK_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun identityOf(container: XmPushActionContainer?): String? {
        if (container == null) return null
        MessageIdentity.fromContainer(container)?.let { return it }
        return runCatching {
            "payload:${sha256(XMPushUtils.packToBytes(container))}"
        }.getOrElse {
            "container:${container.packageName}|${container.action?.name}|${container.isRequest}|" +
                "${container.isEncryptAction}|${container.metaInfo?.id.orEmpty()}|" +
                "${container.metaInfo?.messageTs ?: 0L}|${container.metaInfo?.notifyId ?: 0}"
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
