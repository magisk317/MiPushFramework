package io.github.magisk317.mipush.manager.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ManagerModelsTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `manager event round trips without transient payload`() {
        val event = event(payload = byteArrayOf(1, 2, 3))
        val encoded = json.encodeToJsonElement(event)
        val decoded = json.decodeFromJsonElement<ManagerEvent>(encoded)

        assertFalse(encoded.jsonObject.containsKey("payload"))
        assertEquals(event.copy(payload = null), decoded)
        assertEquals(0, decoded.userId)
    }

    @Test
    fun `manager event equality compares payload contents`() {
        val original = event(payload = byteArrayOf(1, 2, 3))

        assertEquals(original, event(payload = byteArrayOf(1, 2, 3)))
        assertNotEquals(original, event(payload = byteArrayOf(1, 2, 4)))
    }

    private fun event(payload: ByteArray?) = ManagerEvent(
        id = 42L,
        packageName = "com.example.client",
        configOptions = setOf("focus"),
        channel = "default",
        receiveDateMs = 1234L,
        title = "Title",
        content = "Content",
        payload = payload,
    )
}
