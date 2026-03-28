package com.xiaomi.xmsf.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRuntimeQueueTest {
    @Test
    fun `offer drops oldest item when capacity exceeded`() {
        val queue = PendingRuntimeQueue<Int>(2)

        queue.offer(1)
        queue.offer(2)
        queue.offer(3)

        assertEquals(listOf(2, 3), queue.drain())
    }

    @Test
    fun `drain clears queue`() {
        val queue = PendingRuntimeQueue<String>(4)

        queue.offer("a")
        queue.offer("b")

        assertEquals(listOf("a", "b"), queue.drain())
        assertTrue(queue.drain().isEmpty())
        assertEquals(0, queue.size())
    }
}
