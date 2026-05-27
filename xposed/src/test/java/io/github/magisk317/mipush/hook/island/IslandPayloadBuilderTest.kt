package io.github.magisk317.mipush.hook.island

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandPayloadBuilderTest {
    @Test
    fun `normalizes show notification flags at root and param v2`() {
        val raw = """
            {
              "isShowNotification": true,
              "param_v2": {
                "isShowNotification": true,
                "showNotification": true
              }
            }
        """.trimIndent()

        val result = IslandPayloadBuilder.normalizeShowNotificationJson(raw, showNotification = false)

        val root = Json.parseToJsonElement(result).jsonObject
        val paramV2 = root["param_v2"]!!.jsonObject
        assertFalse(root["isShowNotification"]!!.jsonPrimitive.boolean)
        assertFalse(paramV2["isShowNotification"]!!.jsonPrimitive.boolean)
        assertFalse(paramV2["showNotification"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `keeps invalid payload unchanged`() {
        val raw = "not-json"

        val result = IslandPayloadBuilder.normalizeShowNotificationJson(raw, showNotification = false)

        assertSame(raw, result)
    }

    @Test
    fun `can normalize payloads visible by request`() {
        val raw = """{"isShowNotification":false,"param_v2":{"isShowNotification":false}}"""

        val result = IslandPayloadBuilder.normalizeShowNotificationJson(raw, showNotification = true)

        val root = Json.parseToJsonElement(result).jsonObject
        val paramV2 = root["param_v2"]!!.jsonObject
        assertTrue(root["isShowNotification"]!!.jsonPrimitive.boolean)
        assertTrue(paramV2["isShowNotification"]!!.jsonPrimitive.boolean)
        assertTrue(paramV2["showNotification"]!!.jsonPrimitive.boolean)
    }
}
