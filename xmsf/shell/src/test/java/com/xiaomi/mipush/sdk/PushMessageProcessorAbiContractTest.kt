package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import java.lang.reflect.Modifier
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The stock-facing processor is deliberately refactored behind its existing class. Keep the
 * Java-visible entrypoints here before moving decode/ack/notification support out of the façade.
 */
class PushMessageProcessorAbiContractTest {
    @Test
    fun `stock processor retains Java static singleton and notification entrypoints`() {
        val type = PushMessageProcessor::class.java

        assertStatic(type.getMethod("getInstance", Context::class.java))
        assertStatic(type.getMethod("getNotificationMessageIntent", Context::class.java, String::class.java, Map::class.java))
        assertStatic(type.getMethod("removeCachedDupKey", Context::class.java, String::class.java))
    }

    @Test
    fun `stock processor retains intent and timezone instance entrypoints`() {
        val type = PushMessageProcessor::class.java

        assertEquals(
            PushMessageHandler.PushMessageInterface::class.java,
            type.getMethod("processIntent", Intent::class.java).returnType,
        )
        assertEquals(
            List::class.java,
            type.getMethod("getTimeForTimeZone", TimeZone::class.java, TimeZone::class.java, List::class.java).returnType,
        )
    }

    private fun assertStatic(method: java.lang.reflect.Method) {
        assertTrue(Modifier.isStatic(method.modifiers), "${method.name} must remain Java static")
    }
}
