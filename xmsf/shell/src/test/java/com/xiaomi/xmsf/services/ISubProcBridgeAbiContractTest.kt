package com.xiaomi.xmsf.services

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ISubProcBridgeAbiContractTest {
    @Test
    fun `sub process bridge retains descriptor transaction and synchronous notification`() {
        val source = File("src/main/java/com/xiaomi/xmsf/services/ISubProcBridge.kt").readText()

        assertTrue(source.contains("const val DESCRIPTOR = \"com.xiaomi.xmsf.services.ISubProcBridge\""))
        assertTrue(source.contains("private const val TRANSACTION_NOTIFY_ONLINE_CONFIG_CHANGED = 1"))
        assertTrue(source.contains("remote.transact(TRANSACTION_NOTIFY_ONLINE_CONFIG_CHANGED, data, reply, 0)"))
        assertTrue(source.contains("data.enforceInterface(DESCRIPTOR)"))
        assertTrue(source.contains("reply?.writeString(DESCRIPTOR)"))
    }

    @Test
    fun `main process service preserves legacy nullable string default adaptation`() {
        val source = File("src/main/java/com/xiaomi/xmsf/services/MainProcBridgeService.kt").readText()
        assertTrue(source.contains("key, defaultValue.orEmpty()"))
    }
}
