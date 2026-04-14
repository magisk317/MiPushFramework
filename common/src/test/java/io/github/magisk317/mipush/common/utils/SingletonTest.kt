package io.github.magisk317.mipush.common.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

class SingletonTest {

    @AfterEach
    fun tearDown() {
        Singleton.instances.clear()
        Singleton.userInstances.clear()
    }

    // --- basic lifecycle ---

    @Test
    fun `create returns same instance on repeated calls`() {
        val a = Singleton.get(Counter::class.java)
        val b = Singleton.get(Counter::class.java)
        assertSame(a, b)
    }

    @Test
    fun `reset overrides created instance`() {
        val original = Singleton.get(Counter::class.java)
        val override = Counter()
        Singleton.reset(Counter::class.java, override)

        assertSame(override, Singleton.get(Counter::class.java))
        assertNotSame(original, Singleton.get(Counter::class.java))
    }

    @Test
    fun `autoReset restores original after close`() {
        val original = Singleton.get(Counter::class.java)
        val override = Counter()
        val autoReset = Singleton.reset(Counter::class.java, override)
        assertSame(override, Singleton.get(Counter::class.java))

        autoReset.close()
        assertSame(original, Singleton.get(Counter::class.java))
    }

    @Test
    fun `create throws for class without no-arg constructor`() {
        assertThrows(RuntimeException::class.java) {
            Singleton.get(NoDefaultConstructor::class.java)
        }
    }

    // --- concurrency ---

    @Test
    fun `concurrent create produces exactly one instance`() {
        val threadCount = 16
        val barrier = CyclicBarrier(threadCount)
        val results = Array<Counter?>(threadCount) { null }
        val threads = (0 until threadCount).map { i ->
            Thread {
                barrier.await()
                results[i] = Singleton.get(Counter::class.java)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val distinct = results.filterNotNull().toSet()
        assertEquals(1, distinct.size, "Expected exactly one Counter instance across $threadCount threads")
    }

    @Test
    fun `concurrent create of different types does not block each other`() {
        val threadCount = 8
        val barrier = CyclicBarrier(threadCount)
        val counterResults = Array<Counter?>(threadCount / 2) { null }
        val markerResults = Array<Marker?>(threadCount / 2) { null }
        val threads = (0 until threadCount).map { i ->
            Thread {
                barrier.await()
                if (i % 2 == 0) {
                    counterResults[i / 2] = Singleton.get(Counter::class.java)
                } else {
                    markerResults[i / 2] = Singleton.get(Marker::class.java)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1, counterResults.filterNotNull().toSet().size)
        assertEquals(1, markerResults.filterNotNull().toSet().size)
    }

    // --- test helpers ---

    class Counter {
        companion object {
            val instanceCount = AtomicInteger(0)
        }
        val id = instanceCount.incrementAndGet()
    }

    class Marker

    class NoDefaultConstructor(val value: String)
}
