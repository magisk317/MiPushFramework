package io.github.magisk317.mipush.platform.support

import java.lang.reflect.Proxy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationManagerReflectionTest {
    @Test
    fun `method lookup traverses binder proxy interfaces`() {
        var invocation: List<Any?> = emptyList()
        val proxy = Proxy.newProxyInstance(
            NotificationManagerReflectionTest::class.java.classLoader,
            arrayOf(NotificationService::class.java),
        ) { _, method, args ->
            if (method.name == "createNotificationChannelsForPackage") {
                invocation = args?.toList().orEmpty()
            }
            null
        }

        val method = NotificationManagerReflection.findMethod(
            proxy.javaClass,
            "createNotificationChannelsForPackage",
            String::class.java,
            Int::class.java,
            Payload::class.java,
        )
        method.invoke(proxy, "target", 42, Payload())

        assertEquals("target", invocation[0])
        assertEquals(42, invocation[1])
        assertEquals(Payload::class.java, invocation[2]?.javaClass)
    }

    private interface NotificationService {
        fun createNotificationChannelsForPackage(packageName: String, uid: Int, payload: Payload)
    }

    private class Payload
}
