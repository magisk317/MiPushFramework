package com.xiaomi.xmsf.services

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IMainProcBridgeAbiContractTest {
    @Test
    fun `main process bridge retains descriptor transactions and synchronous proxy layout`() {
        val source = File("src/main/java/com/xiaomi/xmsf/services/IMainProcBridge.kt").readText()

        assertInOrder(
            source,
            "const val DESCRIPTOR = \"com.xiaomi.xmsf.services.IMainProcBridge\"",
            "private const val TRANSACTION_GET_INT = 1",
            "private const val TRANSACTION_GET_STRING = 2",
            "private const val TRANSACTION_GET_BOOLEAN = 3",
        )
        assertTrue(source.contains("remote.transact(TRANSACTION_GET_INT, data, reply, 0)"))
        assertTrue(source.contains("remote.transact(TRANSACTION_GET_STRING, data, reply, 0)"))
        assertTrue(source.contains("remote.transact(TRANSACTION_GET_BOOLEAN, data, reply, 0)"))
        assertTrue(source.contains("data.enforceInterface(DESCRIPTOR)"))
        assertTrue(source.contains("reply?.writeString(DESCRIPTOR)"))
    }

    private fun assertInOrder(source: String, vararg fragments: String) {
        var offset = -1
        fragments.forEach { fragment ->
            val next = source.indexOf(fragment, offset + 1)
            assertTrue(next >= 0, "Missing ABI fragment: $fragment")
            assertTrue(next > offset, "ABI fragment is out of order: $fragment")
            offset = next
        }
    }
}
