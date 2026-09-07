package io.github.magisk317.mipush.service.runtime

import java.util.ArrayDeque
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Runs commands serially for each key while allowing unrelated keys to run in parallel.
 *
 * The queue bound applies to commands waiting to run, so a burst of decrypted payloads cannot
 * retain an unbounded number of notification objects and byte arrays. An idle key can run inline
 * when the bound is full; this preserves lossless delivery without allowing concurrent submissions
 * for that key to overtake it.
 */
internal class KeyedSerialDispatcher<K>(
    private val delegate: Executor,
    maxQueuedCommands: Int,
    private val failureHandler: ((K, Throwable) -> Unit)? = null,
) {
    private companion object {
        const val PRODUCER_WAIT_MILLIS = 100L
    }

    private val slots: Semaphore
    private val stateLock = Any()
    private val states = HashMap<K, State>()
    private val drainDepth = ThreadLocal<Int>()

    @Volatile
    private var closed = false

    init {
        require(maxQueuedCommands > 0) { "maxQueuedCommands must be positive" }
        slots = Semaphore(maxQueuedCommands, true)
    }

    /**
     * Enqueue a command for [key]. Same-key commands are FIFO; unrelated keys share [delegate].
     *
     * A producer waiting for a full queue periodically checks shutdown state. Dispatcher worker
     * re-entry never waits for a slot because the active command may be the only code able to
     * release one.
     */
    fun execute(key: K, command: () -> Unit) {
        requireNotNull(key) { "key" }
        requireNotNull(command) { "command" }

        var reservedSlot = false
        while (true) {
            lateinit var state: State
            var schedule = false
            var runInline = false
            var retryForSlot = false

            synchronized(stateLock) {
                if (closed) {
                    if (reservedSlot) {
                        slots.release()
                    }
                    throw RejectedExecutionException("dispatcher is shut down")
                }

                state = states[key] ?: run {
                    if (reservedSlot || slots.tryAcquire()) {
                        State().also { newState ->
                            states[key] = newState
                            newState.commands.addLast(Entry(command, ownsSlot = true))
                            newState.scheduled = true
                            reservedSlot = false
                            schedule = true
                        }
                    } else {
                        // Mark the key as draining before running inline so a concurrent producer
                        // observes the same FIFO queue instead of overtaking this command.
                        State().also { newState ->
                            states[key] = newState
                            newState.commands.addLast(Entry(command, ownsSlot = false))
                            newState.scheduled = true
                            newState.drainClaimed = true
                            newState.draining = true
                            runInline = true
                        }
                    }
                }

                if (!runInline && !schedule && states[key] === state) {
                    val ownsSlot = reservedSlot || slots.tryAcquire()
                    if (!ownsSlot && !isDispatcherWorker()) {
                        retryForSlot = true
                    } else {
                        state.commands.addLast(Entry(command, ownsSlot))
                        reservedSlot = false
                        if (!state.scheduled) {
                            state.scheduled = true
                            schedule = true
                        }
                    }
                }
            }

            if (runInline) {
                drainClaimed(key, state)
                return
            }
            if (schedule) {
                submitDrain(key, state)
                return
            }
            if (!retryForSlot) {
                return
            }
            reservedSlot = acquireSlotForCaller()
        }
    }

    /** Stop accepting new commands while allowing commands already queued to finish. */
    fun shutdown() {
        closed = true
    }

    /**
     * Stop accepting new commands and detach commands that have not started.
     * The returned commands are in per-key FIFO order and can be retried by the caller.
     */
    fun shutdownNow(): List<() -> Unit> {
        val pending = ArrayList<() -> Unit>()
        synchronized(stateLock) {
            closed = true
            states.values.forEach { state ->
                state.cancelled = true
                while (true) {
                    val entry = state.commands.pollFirst() ?: break
                    pending += entry.command
                    releaseSlot(entry)
                }
                if (!state.draining) {
                    // A delegate drain wrapper may still be queued, but it will observe cancelled.
                    state.scheduled = false
                }
            }
            states.entries.removeIf { !it.value.draining }
        }
        return pending
    }

    private fun acquireSlotForCaller(): Boolean {
        if (closed) {
            throw RejectedExecutionException("dispatcher is shut down")
        }
        if (isDispatcherWorker()) {
            return slots.tryAcquire()
        }

        var interrupted = false
        while (true) {
            if (closed) {
                if (interrupted) {
                    Thread.currentThread().interrupt()
                }
                throw RejectedExecutionException("dispatcher is shut down")
            }
            try {
                if (slots.tryAcquire(PRODUCER_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    if (interrupted) {
                        Thread.currentThread().interrupt()
                    }
                    return true
                }
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
    }

    private fun isDispatcherWorker(): Boolean = (drainDepth.get() ?: 0) > 0

    private fun submitDrain(key: K, state: State) {
        try {
            delegate.execute { drain(key, state) }
        } catch (schedulingFailure: Throwable) {
            reportFailure(key, schedulingFailure)
            if (claimDrain(state)) {
                drainClaimed(key, state)
            }
        }
    }

    private fun drain(key: K, state: State) {
        if (claimDrain(state)) {
            drainClaimed(key, state)
        }
    }

    private fun claimDrain(state: State): Boolean = synchronized(stateLock) {
        if (state.cancelled || state.drainClaimed) {
            false
        } else {
            state.drainClaimed = true
            state.draining = true
            true
        }
    }

    private fun drainClaimed(key: K, state: State) {
        drainDepth.set((drainDepth.get() ?: 0) + 1)
        try {
            while (true) {
                val entry = synchronized(stateLock) {
                    if (state.cancelled) {
                        null
                    } else {
                        state.commands.pollFirst()
                    }
                }
                if (entry == null) {
                    synchronized(stateLock) {
                        state.draining = false
                        state.scheduled = false
                        if (states[key] === state) {
                            states.remove(key)
                        }
                    }
                    return
                }

                // Active work is not counted against the queue bound. This prevents re-entry from
                // waiting for its own completion before it can enqueue a same-key command.
                releaseSlot(entry)
                try {
                    entry.command()
                } catch (failure: Throwable) {
                    reportFailure(key, failure)
                }
            }
        } finally {
            val depth = drainDepth.get() ?: 1
            if (depth <= 1) {
                drainDepth.remove()
            } else {
                drainDepth.set(depth - 1)
            }
            synchronized(stateLock) {
                if (state.draining) {
                    state.draining = false
                    state.scheduled = false
                    clearPendingLocked(state)
                    if (states[key] === state) {
                        states.remove(key)
                    }
                }
            }
        }
    }

    private fun clearPendingLocked(state: State) {
        while (true) {
            val entry = state.commands.pollFirst() ?: break
            releaseSlot(entry)
        }
    }

    private fun releaseSlot(entry: Entry) {
        if (entry.ownsSlot) {
            entry.ownsSlot = false
            slots.release()
        }
    }

    private fun reportFailure(key: K, failure: Throwable) {
        runCatching { failureHandler?.invoke(key, failure) }
    }

    // Package-private test visibility keeps production diagnostics out of the public API.
    internal fun activeKeyCountForTest(): Int = synchronized(stateLock) { states.size }

    internal fun queuedCommandCountForTest(): Int = synchronized(stateLock) {
        states.values.sumOf { it.commands.size }
    }

    internal fun availableSlotsForTest(): Int = slots.availablePermits()

    private class Entry(
        val command: () -> Unit,
        var ownsSlot: Boolean,
    )

    private class State {
        val commands = ArrayDeque<Entry>()
        var scheduled = false
        var drainClaimed = false
        var draining = false
        var cancelled = false
    }
}

/** Exact Android notification identity used as a serialization key. */
internal data class NotificationDispatchKey(
    val userId: Int,
    val packageName: String,
    val notificationId: Int,
    val tag: String?,
)
