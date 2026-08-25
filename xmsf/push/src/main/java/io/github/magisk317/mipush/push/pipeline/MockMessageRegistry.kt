package io.github.magisk317.mipush.push.pipeline

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import java.security.MessageDigest

object MockMessageRegistry {
    const val EXTRA_MOCK_REPLAY = "mipush_mock_replay"
    const val EXTRA_MOCK_REPLAY_SOURCE_ID = "mipush_mock_replay_source_id"
    private const val MARK_TTL_MS = 30_000L
    const val MAX_MARKED_MESSAGES = 256
    private val lock = Any()
    private val markedMessageIds = LinkedHashMap<String, Long>()

    @JvmStatic
    fun mark(container: XmPushActionContainer?) {
        val id = identityOf(container) ?: return
        markKey(key(container?.packageName, id))
    }

    @JvmStatic
    fun markMessageId(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        markKey(messageId)
    }

    private fun markKey(key: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pruneExpiredLocked(now)
            if (!markedMessageIds.containsKey(key) && markedMessageIds.size >= MAX_MARKED_MESSAGES) {
                markedMessageIds.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
            markedMessageIds[key] = now
        }
        logD("marked mock message key=$key ttlMs=$MARK_TTL_MS")
    }

    @JvmStatic
    fun isMarked(container: XmPushActionContainer?): Boolean {
        val id = identityOf(container) ?: return false
        return isMarked(container?.packageName, id)
    }

    @JvmStatic
    fun isMarked(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        return isMarkedKey(messageId)
    }

    @JvmStatic
    fun isMarked(packageName: String?, messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        return isMarkedKey(key(packageName, messageId))
    }

    private fun isMarkedKey(key: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pruneExpiredLocked(now)
            val ts = markedMessageIds[key] ?: return false
            if ((now - ts) > MARK_TTL_MS) {
                markedMessageIds.remove(key)
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

    fun markedMessageCount(): Int = synchronized(lock) { markedMessageIds.size }

    private fun pruneExpiredLocked(now: Long) {
        val iterator = markedMessageIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((now - entry.value) > MARK_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun key(packageName: String?, messageId: String): String =
        "${packageName.orEmpty()}\u0000$messageId"

    private fun identityOf(container: XmPushActionContainer?): String? {
        if (container == null) return null
        MessageIdentity.fromContainer(container)?.let { return it }
        return runCatching {
            "payload:${sha256(PushShellBridgeHolder.require().packToBytes(container))}"
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
