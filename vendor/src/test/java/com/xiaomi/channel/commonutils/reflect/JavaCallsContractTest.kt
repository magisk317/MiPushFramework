package com.xiaomi.channel.commonutils.reflect

import java.lang.reflect.Proxy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaCallsContractTest {
    @Test
    fun `method lookup traverses interfaces implemented by binder proxies`() {
        val proxy = Proxy.newProxyInstance(
            JavaCallsContractTest::class.java.classLoader,
            arrayOf(Contract::class.java),
        ) { _, method, args ->
            if (method.name == "read") "value:${args?.single()}" else null
        }

        assertEquals("value:input", JavaCalls.callMethod(proxy, "read", "input"))
    }

    private interface Contract {
        fun read(value: String): String
    }
}
