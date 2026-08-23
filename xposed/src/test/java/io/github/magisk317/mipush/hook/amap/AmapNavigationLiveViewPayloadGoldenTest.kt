package io.github.magisk317.mipush.hook.amap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Golden test for AmapNavigationLiveViewPayload JSON output.
 * Locks the exact callback format sent to AMap live-view surfaces.
 */
class AmapNavigationLiveViewPayloadGoldenTest {
    @Test
    fun `connect success payload matches stock xiaomi focus format`() {
        val payload = AmapNavigationLiveViewPayload.connectSuccess()
        val expected = """{"code":1,"message":{"msg":"connect_success","manufacturer":"XIAOMI","deviceName":"XiaomiFocus"},"displayName":"","deviceType":""}"""
        assertEquals(expected, payload.toString())
    }

    @Test
    fun `connect success has required fields`() {
        val payload = AmapNavigationLiveViewPayload.connectSuccess()
        assertEquals(1, payload["code"]?.jsonPrimitive?.int)
        val message = payload["message"]?.jsonObject
        assertNotNull(message)
        assertEquals("connect_success", message?.get("msg")?.jsonPrimitive?.content)
        assertEquals("XIAOMI", message?.get("manufacturer")?.jsonPrimitive?.content)
        assertEquals("XiaomiFocus", message?.get("deviceName")?.jsonPrimitive?.content)
        assertEquals("", payload["displayName"]?.jsonPrimitive?.content)
        assertEquals("", payload["deviceType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `payload is valid JsonObject and survives serialization roundtrip`() {
        val payload = AmapNavigationLiveViewPayload.connectSuccess()
        val serialized = payload.toString()
        val parsed = Json.parseToJsonElement(serialized)
        assertNotNull(parsed.jsonObject)
        assertEquals(parsed.jsonObject, payload)
    }
}
