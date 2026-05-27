package io.github.magisk317.mipush.hook.island

import org.json.JSONObject
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

        val root = JSONObject(result)
        val paramV2 = root.getJSONObject("param_v2")
        assertFalse(root.getBoolean("isShowNotification"))
        assertFalse(paramV2.getBoolean("isShowNotification"))
        assertFalse(paramV2.getBoolean("showNotification"))
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

        val root = JSONObject(result)
        val paramV2 = root.getJSONObject("param_v2")
        assertTrue(root.getBoolean("isShowNotification"))
        assertTrue(paramV2.getBoolean("isShowNotification"))
        assertTrue(paramV2.getBoolean("showNotification"))
    }
}
