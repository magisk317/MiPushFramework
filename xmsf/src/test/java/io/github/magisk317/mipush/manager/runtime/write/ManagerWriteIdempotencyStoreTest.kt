package io.github.magisk317.mipush.manager.runtime.write

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
