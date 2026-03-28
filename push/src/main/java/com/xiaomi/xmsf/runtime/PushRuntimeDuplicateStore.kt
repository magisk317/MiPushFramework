package com.xiaomi.xmsf.runtime

import android.content.Context
import java.util.LinkedHashMap
import java.util.LinkedList
import java.util.Queue

object PushRuntimeDuplicateStore {
    private const val PREF_NAME = "push_message_ids"
    private const val MAX_MSG_CACHE_COUNT = 25
    private val lock = Any()
    private val cachedIds = LinkedHashMap<String, Queue<String>>()

    @JvmStatic
    fun isDuplicateMessage(
        context: Context,
        packageName: String,
        messageId: String?
    ): Boolean {
        if (messageId.isNullOrBlank()) {
            return false
        }
        synchronized(lock) {
            val sharedPreferences = context.getSharedPreferences(PREF_NAME, 0)
            val queue = cachedIds.getOrPut(packageName) {
                LinkedList<String>().apply {
                    sharedPreferences.getString(packageName, "")?.split(',')
                        ?.filter { it.isNotBlank() }
                        ?.forEach(::add)
                }
            }
            if (queue.contains(messageId)) {
                PushRuntime.observeChannelEvent(
                    packageName = packageName,
                    action = "duplicate_message_drop",
                    source = "PushRuntimeDuplicateStore.isDuplicateMessage"
                )
                return true
            }
            queue.add(messageId)
            while (queue.size > MAX_MSG_CACHE_COUNT) {
                queue.poll()
            }
            sharedPreferences.edit()
                .putString(packageName, queue.joinToString(","))
                .commit()
            return false
        }
    }

    @JvmStatic
    fun clearForTests() {
        synchronized(lock) {
            cachedIds.clear()
        }
    }
}
