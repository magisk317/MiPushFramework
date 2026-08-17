package io.github.magisk317.mipush.manager.client

import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Priority assigned to a manager-side remote call before it waits for a Binder permit. */
enum class RemotePriority {
    TRANSITION_CRITICAL,
    VISIBLE_PAGE,
    USER_ACTION,
    BACKGROUND_REFRESH,
}

/** Cancellation behavior selected before a remote call is queued. */
enum class RemoteCancellationPolicy {
    CANCELLABLE,
    NON_CANCELLABLE,
}

/** Finite budgets and cancellation semantics for one remote call. */
data class RemoteCallBudget(
    val priority: RemotePriority,
    val timeoutMillis: Long,
    val maxQueueWaitMillis: Long,
    val cancellationPolicy: RemoteCancellationPolicy = RemoteCancellationPolicy.CANCELLABLE,
) {
    init {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        require(maxQueueWaitMillis >= 0) { "maxQueueWaitMillis must not be negative" }
    }
}

enum class RemoteCallTimeoutScope {
    QUEUE_WAIT,
    CALL,
}

sealed interface ManagerRuntimeCallResult<out T> {
    data class Success<T>(val value: T) : ManagerRuntimeCallResult<T>

    data class Busy(
        val priority: RemotePriority,
        val queueWaitMillis: Long,
    ) : ManagerRuntimeCallResult<Nothing>

    data class Unavailable(
        val availability: ManagerRuntimeAvailability,
    ) : ManagerRuntimeCallResult<Nothing>

    data class Cancelled(
        val reason: String = "cancelled",
    ) : ManagerRuntimeCallResult<Nothing>

    data class Timeout(
        val scope: RemoteCallTimeoutScope,
        val timeoutMillis: Long,
    ) : ManagerRuntimeCallResult<Nothing>

    data class ValidationFailed(
        val reason: String,
    ) : ManagerRuntimeCallResult<Nothing>

    data object Stale : ManagerRuntimeCallResult<Nothing>
}

/**
 * Manager-side scheduling boundary for Binder work.
 *
 * The scheduler deliberately knows nothing about AIDL or runtime DTOs. Callers provide the
 * operation and optional validation callback, while this class owns permit acquisition, budgets,
 * cancellation, and stale-result suppression.
 */
interface ManagerRuntimeCallScheduler {
    suspend fun <T> call(
        operation: String,
        budget: RemoteCallBudget,
        transitionToken: String? = null,
        isStale: () -> Boolean = { false },
        validate: (T) -> String? = { null },
        block: suspend () -> T,
    ): ManagerRuntimeCallResult<T>

    fun cancelForTransition(token: String)

    fun currentAvailability(): ManagerRuntimeAvailability?
}

class DefaultManagerRuntimeCallScheduler(
    private val maxInFlightCalls: Int = DEFAULT_MAX_IN_FLIGHT_CALLS,
    private val availabilityProvider: () -> ManagerRuntimeAvailability? = { null },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ManagerRuntimeCallScheduler {
    init {
        require(maxInFlightCalls > 0) { "maxInFlightCalls must be positive" }
    }

    private val permits = PriorityPermitQueue(maxInFlightCalls)
    private val ownedJobs = ConcurrentHashMap<String, MutableSet<Job>>()

    override fun currentAvailability(): ManagerRuntimeAvailability? = availabilityProvider()

    override fun cancelForTransition(token: String) {
        ownedJobs.remove(token)?.toList()?.forEach { it.cancel(CancellationException("transition_cancelled")) }
    }

    override suspend fun <T> call(
        operation: String,
        budget: RemoteCallBudget,
        transitionToken: String?,
        isStale: () -> Boolean,
        validate: (T) -> String?,
        block: suspend () -> T,
    ): ManagerRuntimeCallResult<T> {
        // Resolve availability and validate all call metadata before touching the permit queue.
        availabilityProvider()?.let { availability ->
            if (availability !is ManagerRuntimeAvailability.Available) {
                return ManagerRuntimeCallResult.Unavailable(availability)
            }
        }

        val owner = currentCoroutineContext()[Job]
        transitionToken?.let { token ->
            ownedJobs.compute(token) { _, jobs ->
                (jobs ?: ConcurrentHashMap.newKeySet()).also { it += owner!! }
            }
        }

        var lease: PermitLease? = null
        val startedWaitingAt = System.nanoTime()
        return try {
            try {
                lease = permits.acquire(budget, startedWaitingAt)
            } catch (error: CancellationException) {
                if (budget.cancellationPolicy == RemoteCancellationPolicy.CANCELLABLE) {
                    return ManagerRuntimeCallResult.Cancelled(error.message ?: "cancelled")
                }
                // A non-cancellable call still waits for a permit, but its acquisition and release
                // are shielded from caller cancellation so no acquired permit can be leaked.
                lease = withContext(NonCancellable) {
                    permits.acquire(budget, startedWaitingAt)
                }
            }

            if (lease == null) {
                return ManagerRuntimeCallResult.Busy(
                    priority = budget.priority,
                    queueWaitMillis = elapsedMillis(startedWaitingAt),
                )
            }
            if (isStale()) return ManagerRuntimeCallResult.Stale

            val value = try {
                withContext(ioDispatcher) {
                    withTimeout(budget.timeoutMillis) { block() }
                }
            } catch (_: TimeoutCancellationException) {
                return ManagerRuntimeCallResult.Timeout(
                    scope = RemoteCallTimeoutScope.CALL,
                    timeoutMillis = budget.timeoutMillis,
                )
            } catch (error: CancellationException) {
                if (budget.cancellationPolicy == RemoteCancellationPolicy.CANCELLABLE) {
                    return ManagerRuntimeCallResult.Cancelled(error.message ?: "cancelled")
                }
                return withContext(NonCancellable) {
                    runCatching { block() }.fold(
                        onSuccess = { completed ->
                            if (isStale()) ManagerRuntimeCallResult.Stale
                            else validateResult(completed, validate)
                        },
                        onFailure = {
                            ManagerRuntimeCallResult.Cancelled("non_cancellable_call_cancelled")
                        },
                    )
                }
            }

            if (isStale()) return ManagerRuntimeCallResult.Stale
            validateResult(value, validate)
        } finally {
            // PermitLease.release is idempotent, so every terminal path (including Binder death,
            // timeout and stale-result handling) has exactly one effective release.
            lease?.release()
            transitionToken?.let { token ->
                if (owner != null) {
                    ownedJobs.computeIfPresent(token) { _, jobs ->
                        jobs.remove(owner)
                        jobs.takeIf { it.isNotEmpty() }
                    }
                }
            }
        }
    }

    private fun <T> validateResult(value: T, validate: (T) -> String?): ManagerRuntimeCallResult<T> =
        validate(value)?.let { ManagerRuntimeCallResult.ValidationFailed(it) }
            ?: ManagerRuntimeCallResult.Success(value)

    private fun elapsedMillis(startedAtNanos: Long): Long =
        ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)

    private companion object {
        const val DEFAULT_MAX_IN_FLIGHT_CALLS = 6
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

private class PermitLease(private val releaseAction: () -> Unit) {
    private val released = AtomicBoolean(false)

    fun release() {
        if (released.compareAndSet(false, true)) releaseAction()
    }
}

/** A bounded, FIFO-within-priority permit queue. */
private class PriorityPermitQueue(private val capacity: Int) {
    private data class Request(
        val priority: RemotePriority,
        val sequence: Long,
        val result: CompletableDeferred<PermitLease> = CompletableDeferred(),
        var granted: Boolean = false,
        var lease: PermitLease? = null,
    )

    private val lock = Any()
    private var available = capacity
    private var nextSequence = 0L
    private val waiting = PriorityQueue<Request>(compareBy<Request> { it.priority.ordinal }.thenBy { it.sequence })

    suspend fun acquire(budget: RemoteCallBudget, startedWaitingAt: Long): PermitLease? {
        val request = synchronized(lock) {
            if (available > 0) {
                available--
                return@synchronized null
            }
            Request(budget.priority, nextSequence++).also { waiting += it }
        }

        // A null request means a permit was available synchronously.
        if (request == null) return PermitLease(::releasePermit)

        return try {
            if (budget.maxQueueWaitMillis == 0L) {
                cancel(request)
                null
            } else {
                withTimeout(budget.maxQueueWaitMillis) { request.result.await() }
            }
        } catch (_: TimeoutCancellationException) {
            cancel(request)
            null
        } catch (error: CancellationException) {
            cancel(request)
            throw error
        }
    }

    private fun cancel(request: Request) {
        synchronized(lock) {
            if (request.granted) {
                // The request was granted concurrently with cancellation, but its caller never
                // received the lease. Return it exactly once through the lease guard.
                request.lease?.release()
            } else {
                waiting.remove(request)
                request.result.cancel()
            }
        }
    }

    private fun releasePermit() {
        synchronized(lock) {
            available++
            dispatchLocked()
        }
    }

    private fun dispatchLocked() {
        while (available > 0 && waiting.isNotEmpty()) {
            val request = waiting.poll() ?: break
            available--
            val lease = PermitLease(::releasePermit)
            request.lease = lease
            request.granted = true
            request.result.complete(lease)
        }
    }
}
