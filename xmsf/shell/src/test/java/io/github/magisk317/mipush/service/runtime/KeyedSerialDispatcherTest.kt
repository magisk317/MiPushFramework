package io.github.magisk317.mipush.service.runtime

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyedSerialDispatcherTest {

    @Test
    fun `same key runs in submission order`() {
        withBackend(2) { backend ->
            val dispatcher = KeyedSerialDispatcher<String>(backend, maxQueuedCommands = 4)
            val events = Collections.synchronizedList(mutableListOf<Int>())
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val secondFinished = CountDownLatch(1)

            dispatcher.execute("same") {
                events += 1
                firstStarted.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

            dispatcher.execute("same") {
                events += 2
                secondFinished.countDown()
            }

            assertFalse(secondFinished.await(100, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertTrue(secondFinished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(1, 2), events)
            assertTrue(awaitIdle(dispatcher))
        }
    }

    @Test
    fun `different keys can run concurrently`() {
        withBackend(2) { backend ->
            val dispatcher = KeyedSerialDispatcher<String>(backend, maxQueuedCommands = 4)
            val started = CountDownLatch(2)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(2)

            dispatcher.execute("a") {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                finished.countDown()
            }
            dispatcher.execute("b") {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                finished.countDown()
            }

            assertTrue(started.await(2, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `command failure does not stall the key`() {
        withBackend(1) { backend ->
            val failures = Collections.synchronizedList(mutableListOf<Throwable>())
            val dispatcher = KeyedSerialDispatcher<String>(
                delegate = backend,
                maxQueuedCommands = 4,
                failureHandler = { _, failure -> failures += failure },
            )
            val secondFinished = CountDownLatch(1)

            dispatcher.execute("same") {
                throw AssertionError("expected")
            }
            dispatcher.execute("same") {
                secondFinished.countDown()
            }

            assertTrue(secondFinished.await(2, TimeUnit.SECONDS))
            assertEquals(1, failures.size)
            assertTrue(failures.single() is AssertionError)
        }
    }

    @Test
    fun `queued commands are bounded and permits are released`() {
        withBackend(1) { backend ->
            val dispatcher = KeyedSerialDispatcher<String>(backend, maxQueuedCommands = 1)
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val thirdReturned = CountDownLatch(1)

            dispatcher.execute("same") {
                firstStarted.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            dispatcher.execute("same") { }

            val producer = Thread {
                dispatcher.execute("same") { }
                thirdReturned.countDown()
            }
            producer.start()
            assertFalse(thirdReturned.await(100, TimeUnit.MILLISECONDS))
            assertEquals(1, dispatcher.queuedCommandCountForTest())

            releaseFirst.countDown()
            assertTrue(thirdReturned.await(2, TimeUnit.SECONDS))
            producer.join(2_000)
            assertTrue(awaitIdle(dispatcher))
            assertEquals(1, dispatcher.availableSlotsForTest())
        }
    }

    @Test
    fun `worker reentry remains ordered when queue is full`() {
        withBackend(1) { backend ->
            val dispatcher = KeyedSerialDispatcher<String>(backend, maxQueuedCommands = 1)
            val events = Collections.synchronizedList(mutableListOf<Int>())
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val finished = CountDownLatch(1)

            dispatcher.execute("same") {
                events += 1
                firstStarted.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
                dispatcher.execute("same") {
                    events += 3
                    finished.countDown()
                }
            }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            dispatcher.execute("same") { events += 2 }

            releaseFirst.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(1, 2, 3), events)
            assertTrue(awaitIdle(dispatcher))
        }
    }

    @Test
    fun `shutdown now detaches pending commands and rejects new work`() {
        withBackend(1) { backend ->
            val dispatcher = KeyedSerialDispatcher<String>(backend, maxQueuedCommands = 2)
            val firstStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)

            dispatcher.execute("same") {
                firstStarted.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            dispatcher.execute("same") { }
            dispatcher.execute("same") { }

            val pending = dispatcher.shutdownNow()
            assertEquals(2, pending.size)
            assertEquals(0, dispatcher.queuedCommandCountForTest())
            assertEquals(2, dispatcher.availableSlotsForTest())
            assertThrows(RejectedExecutionException::class.java) {
                dispatcher.execute("new") { }
            }

            releaseFirst.countDown()
            assertTrue(awaitIdle(dispatcher))
        }
    }

    @Test
    fun `notification key keeps Android user and notification identity separate`() {
        val base = NotificationDispatchKey(0, "com.example", 42, null)

        assertNotEquals(base, base.copy(userId = 999))
        assertNotEquals(base, base.copy(packageName = "com.other"))
        assertNotEquals(base, base.copy(notificationId = 43))
        assertNotEquals(base, base.copy(tag = "tag"))
    }

    private fun withBackend(workerCount: Int, block: (ExecutorService) -> Unit) {
        val backend = Executors.newFixedThreadPool(workerCount)
        try {
            block(backend)
        } finally {
            backend.shutdownNow()
        }
    }

    private fun awaitIdle(dispatcher: KeyedSerialDispatcher<*>): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline) {
            if (dispatcher.activeKeyCountForTest() == 0 &&
                dispatcher.queuedCommandCountForTest() == 0
            ) {
                return true
            }
            Thread.sleep(5)
        }
        return dispatcher.activeKeyCountForTest() == 0 && dispatcher.queuedCommandCountForTest() == 0
    }
}
