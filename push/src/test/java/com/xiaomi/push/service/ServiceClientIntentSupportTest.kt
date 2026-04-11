package com.xiaomi.push.service

import org.apache.http.NameValuePair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceClientIntentSupportTest {
    @Test
    fun `translate converts pairs into string map`() {
        val translated = ServiceClientIntentSupport.translate(
            listOf(
                pair("chid", "5"),
                pair("user", "u@example.com"),
            ),
        )

        assertEquals(mapOf("chid" to "5", "user" to "u@example.com"), translated)
    }

    @Test
    fun `translate returns empty map for null input`() {
        assertTrue(ServiceClientIntentSupport.translate(null).isEmpty())
    }

    @Test
    fun `joinAttributes preserves insertion order with separators`() {
        val joined = ServiceClientIntentSupport.joinAttributes(
            linkedMapOf(
                "chid" to "5",
                "user" to "u@example.com",
                "token" to "secret",
            ),
        )

        assertEquals("chid:5,user:u@example.com,token:secret", joined)
    }

    private fun pair(name: String, value: String): NameValuePair {
        return object : NameValuePair {
            override val name: String = name
            override val value: String = value
        }
    }
}
