package com.xiaomi.mipush.sdk

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssemblePushHelperContractTest {
    @Test
    fun `public static ABI keeps parse and token method signatures`() {
        assertStaticMethod(
            name = "parseMiPushMessage",
            returnType = MiPushMessage::class.java,
            String::class.java,
        )
        assertStaticMethod(
            name = "getTokenKey",
            returnType = String::class.java,
            AssemblePush::class.java,
        )
        assertStaticMethod(
            name = "getAssemblePushTokenKey",
            returnType = String::class.java,
            AssemblePush::class.java,
        )
    }

    @Test
    fun `parse isolates malformed fields and keeps later fields`() {
        val message = AssemblePushHelper.parseMiPushMessage(
            """
            {
              "messageId":"id-1",
              "description":{"not":"a string"},
              "title":"title",
              "content":"payload",
              "passThrough":1,
              "notifyType":null,
              "messageType":2,
              "alias":["not a primitive"],
              "topic":"topic",
              "user_account":"account",
              "notifyId":{"not":"an int"},
              "category":"category",
              "isNotified":true,
              "extra":{"kept":"value","bad":{"nested":true},"alsoKept":"value-2"}
            }
            """.trimIndent(),
        )

        assertEquals("id-1", message.messageId)
        assertEquals("title", message.title)
        assertEquals("payload", message.content)
        assertEquals(1, message.passThrough)
        assertEquals(2, message.messageType)
        assertEquals("topic", message.topic)
        assertEquals("account", message.userAccount)
        assertEquals("category", message.category)
        assertTrue(message.isNotified)
        assertEquals(
            mapOf("kept" to "value", "alsoKept" to "value-2"),
            message.extra,
        )
        assertFalse(message.description != null)
        assertEquals(0, message.notifyType)
        assertEquals(0, message.notifyId)
    }

    @Test
    fun `empty and non object payloads keep stock defaults`() {
        val empty = AssemblePushHelper.parseMiPushMessage("")
        val array = AssemblePushHelper.parseMiPushMessage("[]")

        assertNullFields(empty)
        assertNullFields(array)
    }

    private fun assertStaticMethod(name: String, returnType: Class<*>, vararg parameterTypes: Class<*>) {
        val method: Method = AssemblePushHelper::class.java.getMethod(name, *parameterTypes)
        assertTrue(Modifier.isPublic(method.modifiers), "$name must remain public")
        assertTrue(Modifier.isStatic(method.modifiers), "$name must remain static")
        assertEquals(returnType, method.returnType, "$name return type changed")
        assertEquals(parameterTypes.toList(), method.parameterTypes.toList(), "$name parameters changed")
        assertNotNull(method)
    }

    private fun assertNullFields(message: MiPushMessage) {
        assertEquals(null, message.messageId)
        assertEquals(null, message.content)
        assertEquals(null, message.title)
        assertEquals(0, message.passThrough)
        assertEquals(0, message.notifyId)
        assertEquals(false, message.isNotified)
    }
}
