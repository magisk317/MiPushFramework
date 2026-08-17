package io.github.magisk317.mipush.manager.client

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerRuntimeCallSchedulerTest {
    @Test
    fun `transition critical yields ahead of queued background refresh`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(maxInFlightCalls = 1)
        val blockerStarted = AtomicReference(false)
        val releaseBlocker = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val blocker = launch {
            scheduler.call("blocker", budget(timeoutMillis = 500, queueWaitMillis = 500)) {
                blockerStarted.set(true)
                releaseBlocker.await()
                "blocker"
            }
        }
        while (!blockerStarted.get()) delay(1)

        val background = launch {
            scheduler.call(
                "refresh",
                budget(priority = RemotePriority.BACKGROUND_REFRESH, queueWaitMillis = 500),
            ) {
                order += "background"
                "background"
            }
        }
        delay(10)
        val critical = launch {
            scheduler.call(
                "transition",
                budget(priority = RemotePriority.TRANSITION_CRITICAL, queueWaitMillis = 500),
            ) {
                order += "critical"
                "critical"
            }
        }
        delay(10)
        releaseBlocker.complete(Unit)

        blocker.join()
        critical.join()
        background.join()
        assertEquals(listOf("critical", "background"), order)
    }

    @Test
    fun `exceptional binder termination releases permit exactly once`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(maxInFlightCalls = 1)
        var failed = false
        try {
            scheduler.call("binder_death", budget()) {
                error("binder_died")
            }
        } catch (error: IllegalStateException) {
            failed = error.message == "binder_died"
        }
        assertTrue(failed)

        val next = scheduler.call("after_death", budget()) { "recovered" }
        assertEquals(ManagerRuntimeCallResult.Success("recovered"), next)
    }

    @Test
    fun `returns validation failure without exposing invalid value`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(ioDispatcher = Dispatchers.Default)

        val result = scheduler.call(
            operation = "application_page",
            budget = budget(),
            validate = { "invalid_payload" },
        ) { "payload" }

        assertEquals(ManagerRuntimeCallResult.ValidationFailed("invalid_payload"), result)
    }

    @Test
    fun `returns unavailable before acquiring a permit`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(
            availabilityProvider = { ManagerRuntimeAvailability.RuntimeMissing },
        )
        val invoked = AtomicInteger()

        val result = scheduler.call("snapshot", budget()) {
            invoked.incrementAndGet()
            "not reached"
        }

        assertEquals(
            ManagerRuntimeCallResult.Unavailable(ManagerRuntimeAvailability.RuntimeMissing),
            result,
        )
        assertEquals(0, invoked.get())
    }

    @Test
    fun `returns busy when queue budget expires`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(maxInFlightCalls = 1)
        val blockerStarted = AtomicReference(false)
        val blocker = launch {
            scheduler.call("blocker", budget(timeoutMillis = 500, queueWaitMillis = 50)) {
                blockerStarted.set(true)
                delay(200)
                "done"
            }
        }
        while (!blockerStarted.get()) delay(1)

        val result = scheduler.call("queued", budget(queueWaitMillis = 10)) { "never" }

        assertTrue(result is ManagerRuntimeCallResult.Busy)
        blocker.join()
    }

    @Test
    fun `cancelForTransition returns cancelled and releases permit`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(maxInFlightCalls = 1)
        val started = AtomicReference(false)
        val call = launch {
            scheduler.call(
                operation = "events",
                budget = budget(timeoutMillis = 500),
                transitionToken = "old",
            ) {
                started.set(true)
                delay(500)
                "stale"
            }
        }
        while (!started.get()) delay(1)
        scheduler.cancelForTransition("old")
        call.join()

        val next = scheduler.call("next", budget()) { "next" }
        assertEquals(ManagerRuntimeCallResult.Success("next"), next)
    }

    @Test
    fun `stale result is typed and does not become success`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler()
        val result = scheduler.call(
            operation = "events",
            budget = budget(),
            isStale = { true },
        ) { "late" }

        assertEquals(ManagerRuntimeCallResult.Stale, result)
    }

    @Test
    fun `cancelling a queued call removes it without consuming the next permit`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(maxInFlightCalls = 1)
        val blockerStarted = CompletableDeferred<Unit>()
        val releaseBlocker = CompletableDeferred<Unit>()
        val blocker = launch {
            scheduler.call("blocker", budget(queueWaitMillis = 500)) {
                blockerStarted.complete(Unit)
                releaseBlocker.await()
                "done"
            }
        }
        blockerStarted.await()

        val queued = launch {
            scheduler.call("cancelled", budget(queueWaitMillis = 500)) { "must not run" }
        }
        delay(10)
        queued.cancel()
        queued.join()
        releaseBlocker.complete(Unit)
        blocker.join()

        assertEquals(ManagerRuntimeCallResult.Success("next"), scheduler.call("next", budget()) { "next" })
    }

    @Test
    fun `cancellation after acquisition releases exactly one permit`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(maxInFlightCalls = 1)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = launch {
            scheduler.call("cancel", budget(timeoutMillis = 500), transitionToken = "old") {
                started.complete(Unit)
                release.await()
                "late"
            }
        }
        started.await()
        scheduler.cancelForTransition("old")
        release.complete(Unit)
        cancelled.join()

        assertEquals(ManagerRuntimeCallResult.Success("recovered"), scheduler.call("recovered", budget()) { "recovered" })
        assertEquals(ManagerRuntimeCallResult.Success("still-recovered"), scheduler.call("still_recovered", budget()) { "still-recovered" })
    }

    @Test
    fun `call timeout releases permit for a subsequent call`() = runBlocking {
        val scheduler = DefaultManagerRuntimeCallScheduler(maxInFlightCalls = 1)
        val result = scheduler.call("slow", budget(timeoutMillis = 10)) {
            delay(100)
            "too late"
        }

        assertEquals(ManagerRuntimeCallResult.Timeout(RemoteCallTimeoutScope.CALL, 10), result)
        assertEquals(ManagerRuntimeCallResult.Success("after-timeout"), scheduler.call("after", budget()) { "after-timeout" })
    }

    @Test
    fun `budget rejects non-finite queue and call values`() {
        assertThrowsIllegalArgument { budget(timeoutMillis = 0) }
        assertThrowsIllegalArgument { budget(queueWaitMillis = -1) }
    }

    private fun budget(
        priority: RemotePriority = RemotePriority.VISIBLE_PAGE,
        timeoutMillis: Long = 200,
        queueWaitMillis: Long = 100,
    ) = RemoteCallBudget(
        priority = priority,
        timeoutMillis = timeoutMillis,
        maxQueueWaitMillis = queueWaitMillis,
    )

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            error("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
