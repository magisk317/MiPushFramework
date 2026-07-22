package io.github.magisk317.mipush.manager.runtime.write

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerWriteIdempotencyStoreTest {
    @Test
    fun `stores and returns previous results by request id`() {
        val store = ManagerWriteIdempotencyStore(maxEntries = 2)
        val first = ManagerWriteResultDto(
            requestId = "req-1",
            status = ManagerProtocol.WRITE_STATUS_SUCCESS,
            details = "ok",
        )
        store.put(first)
        assertEquals(first, store.get("req-1"))
        assertNull(store.get("missing"))
    }

    @Test
    fun `evicts oldest entries beyond capacity`() {
        val store = ManagerWriteIdempotencyStore(maxEntries = 2)
        store.put(ManagerWriteResultDto(requestId = "a", status = ManagerProtocol.WRITE_STATUS_SUCCESS))
        store.put(ManagerWriteResultDto(requestId = "b", status = ManagerProtocol.WRITE_STATUS_SUCCESS))
        store.put(ManagerWriteResultDto(requestId = "c", status = ManagerProtocol.WRITE_STATUS_SUCCESS))
        assertNull(store.get("a"))
        assertEquals("b", store.get("b")?.requestId)
        assertEquals("c", store.get("c")?.requestId)
    }

    @Test
    fun `begin returns duplicate after complete`() {
        val store = ManagerWriteIdempotencyStore()
        assertEquals(
            ManagerWriteIdempotencyStore.BeginResult.Execute,
            store.begin("req-dup"),
        )
        store.complete(
            ManagerWriteResultDto(
                requestId = "req-dup",
                status = ManagerProtocol.WRITE_STATUS_SUCCESS,
                details = "ok",
                resultLong = 7L,
            ),
        )
        val second = store.begin("req-dup")
        check(second is ManagerWriteIdempotencyStore.BeginResult.Duplicate)
        assertEquals(ManagerProtocol.WRITE_STATUS_DUPLICATE, second.result.status)
        assertEquals(7L, second.result.resultLong)
    }

    @Test
    fun `begin waits for in-flight peer then returns duplicate`() {
        val store = ManagerWriteIdempotencyStore()
        assertEquals(ManagerWriteIdempotencyStore.BeginResult.Execute, store.begin("req-flight"))
        val executor = Executors.newSingleThreadExecutor()
        val observed = AtomicReference<ManagerWriteIdempotencyStore.BeginResult>()
        val started = CountDownLatch(1)
        val done = CountDownLatch(1)
        executor.execute {
            started.countDown()
            observed.set(store.begin("req-flight"))
            done.countDown()
        }
        assertTrue(started.await(1, TimeUnit.SECONDS))
        Thread.sleep(30)
        store.complete(
            ManagerWriteResultDto(
                requestId = "req-flight",
                status = ManagerProtocol.WRITE_STATUS_SUCCESS,
                details = "done",
            ),
        )
        assertTrue(done.await(2, TimeUnit.SECONDS))
        val result = observed.get()
        check(result is ManagerWriteIdempotencyStore.BeginResult.Duplicate)
        assertEquals(ManagerProtocol.WRITE_STATUS_DUPLICATE, result.result.status)
        executor.shutdownNow()
    }

    @Test
    fun `abort releases reservation for a later begin`() {
        val store = ManagerWriteIdempotencyStore()
        assertEquals(ManagerWriteIdempotencyStore.BeginResult.Execute, store.begin("req-abort"))
        store.abort("req-abort")
        assertEquals(ManagerWriteIdempotencyStore.BeginResult.Execute, store.begin("req-abort"))
        store.complete(
            ManagerWriteResultDto(
                requestId = "req-abort",
                status = ManagerProtocol.WRITE_STATUS_SUCCESS,
            ),
        )
    }
}
