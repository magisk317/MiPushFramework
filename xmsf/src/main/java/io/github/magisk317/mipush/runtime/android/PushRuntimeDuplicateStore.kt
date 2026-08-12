package io.github.magisk317.mipush.runtime.android

import android.content.Context
import java.util.LinkedHashMap
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.Utils

object PushRuntimeDuplicateStore {
    private const val PREF_NAME = "push_message_ids"
    const val MAX_MSG_CACHE_COUNT = 200
    const val MESSAGE_ID_TTL_MS = 3 * 24 * 60 * 60 * 1000L
    private val lock = Any()
    private val cachedIds = LinkedHashMap<String, LinkedHashMap<String, Long>>()

    @JvmStatic
    fun isDuplicateMessage(
        context: Context,
        packageName: String,
        messageId: String?,
    ): Boolean {
        return isDuplicateMessage(
            context = context,
            packageName = packageName,
            messageId = messageId,
            nowMs = System.currentTimeMillis(),
            userId = currentUserId(),
        )
    }

    @JvmStatic
    fun isDuplicateMessage(
        context: Context,
        packageName: String,
        messageId: String?,
        nowMs: Long = System.currentTimeMillis(),
        userId: Int = currentUserId(),
    ): Boolean {
        if (messageId.isNullOrBlank()) {
            return false
        }
        synchronized(lock) {
            val sharedPreferences = context.getSharedPreferences(PREF_NAME, 0)
            val normalizedUserId = userId.coerceAtLeast(0)
            val storageKey = storageKey(packageName, normalizedUserId)
            val entries = cachedIds.getOrPut(storageKey) {
                parseStoredEntries(sharedPreferences.getString(storageKey, null), nowMs)
            }
            val duplicated = checkAndMark(entries, messageId, nowMs)
            if (duplicated) {
                AndroidPushRuntime.observeChannelEvent(
                    packageName = packageName,
                    action = "duplicate_message_drop",
                    source = "PushRuntimeDuplicateStore.isDuplicateMessage"
                )
            }
            MagiskOtel.event(
                name = "push.receive",
                attributes = mapOf(
                    "result" to if (duplicated) "skip" else "ok",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "duplicate_check",
                    "reason" to if (duplicated) "duplicate" else "unique",
                    "target_package" to packageName,
                ),
                statusOk = true,
            )
            sharedPreferences.edit()
                .putString(storageKey, serializeStoredEntries(entries))
                .apply()
            return duplicated
        }
    }

    @JvmStatic
    fun clearForTests() {
        synchronized(lock) {
            cachedIds.clear()
        }
    }

    fun parseStoredEntries(raw: String?, nowMs: Long): LinkedHashMap<String, Long> {
        val entries = LinkedHashMap<String, Long>()
        if (raw.isNullOrBlank()) return entries
        if (!raw.contains('\n') && !raw.contains('\t')) {
            raw.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { entries[it] = nowMs }
            return entries
        }
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val parts = line.split('\t', limit = 2)
                val id = parts.firstOrNull()?.trim().orEmpty()
                if (id.isBlank()) return@forEach
                val seenAtMs = parts.getOrNull(1)?.toLongOrNull() ?: nowMs
                entries[id] = seenAtMs
            }
        pruneExpired(entries, nowMs)
        trimToWindow(entries)
        return entries
    }

    fun serializeStoredEntries(entries: LinkedHashMap<String, Long>): String {
        return entries.entries.joinToString(separator = "\n") { (id, seenAtMs) ->
            "$id\t$seenAtMs"
        }
    }

    fun checkAndMark(
        entries: LinkedHashMap<String, Long>,
        messageId: String,
        nowMs: Long,
    ): Boolean {
        pruneExpired(entries, nowMs)
        val duplicated = entries.containsKey(messageId)
        entries.remove(messageId)
        entries[messageId] = nowMs
        trimToWindow(entries)
        return duplicated
    }

    private fun pruneExpired(entries: LinkedHashMap<String, Long>, nowMs: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((nowMs - entry.value) > MESSAGE_ID_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun trimToWindow(entries: LinkedHashMap<String, Long>) {
        while (entries.size > MAX_MSG_CACHE_COUNT) {
            val oldest = entries.entries.iterator().next()
            entries.remove(oldest.key)
        }
    }

    internal fun storageKey(packageName: String, userId: Int): String =
        if (userId == 0) packageName else "$userId:$packageName"

    private fun currentUserId(): Int = runCatching { Utils.myUserId() }.getOrDefault(0)
}
