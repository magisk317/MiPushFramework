package io.github.magisk317.mipush.manager.runtime.write

import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import java.util.LinkedHashMap

/** Process-local request-id cache for retry-after-death safety. */
class ManagerWriteIdempotencyStore(
    private val maxEntries: Int = 256,
) {
    private val lock = Any()
    private val results = object : LinkedHashMap<String, ManagerWriteResultDto>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ManagerWriteResultDto>?): Boolean =
            size > maxEntries
    }

    fun get(requestId: String): ManagerWriteResultDto? = synchronized(lock) {
        results[requestId]
    }

    fun put(result: ManagerWriteResultDto) = synchronized(lock) {
        results[result.requestId] = result
    }
}
