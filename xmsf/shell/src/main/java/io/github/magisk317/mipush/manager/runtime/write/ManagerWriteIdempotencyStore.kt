package io.github.magisk317.mipush.manager.runtime.write

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Process-local request-id cache for retry-after-death safety.
 * Reservations are held across dispatch so concurrent duplicate requestIds single-flight.
 */
class ManagerWriteIdempotencyStore(
    private val maxEntries: Int = 256,
) {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val results = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxEntries
    }
    private val inFlight = linkedSetOf<String>()

    fun get(requestId: String): ManagerWriteResultDto? = lock.withLock {
        results[requestId]?.result
    }

    /**
     * Returns a previous result (as [WRITE_STATUS_DUPLICATE]), waits for an in-flight peer, or
     * reserves [requestId] for the caller to execute.
     */
    fun begin(requestId: String, requestFingerprint: String = ""): BeginResult {
        if (requestId.isBlank()) return BeginResult.Execute
        lock.lock()
        try {
            while (true) {
                results[requestId]?.let { previous ->
                    if (previous.requestFingerprint != requestFingerprint) {
                        return BeginResult.Rejected(
                            ManagerWriteResultDto(
                                requestId = requestId,
                                status = ManagerProtocol.WRITE_STATUS_FAILED,
                                details = "request_id_reused",
                            ),
                        )
                    }
                    return BeginResult.Duplicate(
                        previous.result.copy(status = ManagerProtocol.WRITE_STATUS_DUPLICATE),
                    )
                }
                if (requestId !in inFlight) {
                    inFlight += requestId
                    return BeginResult.Execute
                }
                runCatching { condition.await(50L, TimeUnit.MILLISECONDS) }
            }
        } finally {
            lock.unlock()
        }
    }

    fun complete(result: ManagerWriteResultDto, requestFingerprint: String = "") = lock.withLock {
        inFlight -= result.requestId
        // Only cache completed successes. Caching FAILED (e.g. temporary ROOT_MISSING)
        // would permanently block later retries with the same stable requestId after the
        // user grants root or the environment recovers.
        if (result.requestId.isNotBlank() &&
            (result.status == ManagerProtocol.WRITE_STATUS_SUCCESS ||
                result.status == ManagerProtocol.WRITE_STATUS_DUPLICATE)
        ) {
            results[result.requestId] = Entry(result, requestFingerprint)
        }
        condition.signalAll()
    }

    fun abort(requestId: String) = lock.withLock {
        inFlight -= requestId
        condition.signalAll()
    }

    fun put(result: ManagerWriteResultDto, requestFingerprint: String = "") =
        complete(result, requestFingerprint)

    private data class Entry(
        val result: ManagerWriteResultDto,
        val requestFingerprint: String,
    )

    sealed interface BeginResult {
        data object Execute : BeginResult
        data class Duplicate(val result: ManagerWriteResultDto) : BeginResult
        data class Rejected(val result: ManagerWriteResultDto) : BeginResult
    }
}
