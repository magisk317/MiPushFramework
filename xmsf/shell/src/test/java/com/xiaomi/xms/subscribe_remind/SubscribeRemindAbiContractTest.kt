package com.xiaomi.xms.subscribe_remind

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscribeRemindAbiContractTest {
    @Test
    fun `aidl keeps stock descriptor and transaction order`() {
        val source = File("src/main/aidl/com/xiaomi/xms/subscribe_remind/ISubscribeRemind.aidl").readText()
        val methods = Regex("void\\s+(\\w+)\\s*\\(").findAll(source).map { it.groupValues[1] }.toList()

        assertTrue(source.contains("package com.xiaomi.xms.subscribe_remind;"))
        assertEquals(4, Regex("inout\\s+Bundle").findAll(source).count())
        assertTrue(!source.contains("in Bundle"))
        assertEquals(
            listOf("subscribeRemind", "cancelSubscribeRemind", "deviceInfoReport", "send"),
            methods,
        )
    }

    @Test
    fun `service uses stock component coordinate and unsupported result`() {
        val source = File("src/main/java/com/xiaomi/xms/subscribe_remind/SubscribeRemindService.kt").readText()

        assertTrue(source.contains("package com.xiaomi.xms.subscribe_remind"))
        assertTrue(source.contains("ERROR_NOT_SUPPORT = -100"))
        assertTrue(source.contains("override fun subscribeRemind"))
        assertTrue(source.contains("override fun cancelSubscribeRemind"))
        assertTrue(source.contains("override fun deviceInfoReport"))
        assertTrue(source.contains("override fun send"))
    }
}
