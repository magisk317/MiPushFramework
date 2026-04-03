package com.xiaomi.xmsf.runtime

import android.content.Context
import java.util.LinkedHashMap

object PushRuntimeDuplicateStore {
    private const val PREF_NAME = "push_message_ids"
    internal const val MAX_MSG_CACHE_COUNT = 200
    internal const val MESSAGE_ID_TTL_MS = 3 * 24 * 60 * 60 * 1000L
    private val lock = Any()
    private val cachedIds = LinkedHashMap<String, LinkedHashMap<String, Long>>()

    @JvmStatic
    fun isDuplicateMessage(
        context: Context,
        packageName: String,
        messageId: String?,
    ): Boolean {
        return isDuplicateMessage(context, packageName, messageId, System.currentTimeMillis())
    }

    @JvmStatic
    fun isDuplicateMessage(
        context: Context,
        packageName: String,
        messageId: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (messageId.isNullOrBlank()) {
            return false
        }
        synchronized(lock) {
            val sharedPreferences = context.getSharedPreferences(PREF_NAME, 0)
            val entries = cachedIds.getOrPut(packageName) {
                parseStoredEntries(sharedPreferences.getString(packageName, null), nowMs)
            }
            val duplicated = checkAndMark(entries, messageId, nowMs)
            if (duplicated) {
                PushRuntime.observeChannelEvent(
                    packageName = packageName,
                    action = "duplicate_message_drop",
                    source = "PushRuntimeDuplicateStore.isDuplicateMessage"
                )
            }
            sharedPreferences.edit()
                .putString(packageName, serializeStoredEntries(entries))
                .commit()
            return duplicated
        }
    }

    @JvmStatic
    fun clearForTests() {
        synchronized(lock) {
            cachedIds.clear()
        }
    }

    internal fun parseStoredEntries(raw: String?, nowMs: Long): LinkedHashMap<String, Long> {
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

    internal fun serializeStoredEntries(entries: LinkedHashMap<String, Long>): String {
        return entries.entries.joinToString(separator = "\n") { (id, seenAtMs) ->
            "$id\t$seenAtMs"
        }
    }

    internal fun checkAndMark(
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
}
