package io.github.magisk317.mipush.hook.island

import io.github.magisk317.mipush.common.NotificationStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
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

    @Test
    fun `small-only payload preserves trigger island areas`() {
        val raw = """
            {
              "param_v2": {
                "param_island": {
                  "bigIslandArea": {"textInfo": {"title": "large"}},
                  "smallIslandArea": {"picInfo": {"pic": "mipush_icon"}},
                  "islandTimeout": 5
                },
                "iconTextInfo": {"title": "title"}
              }
            }
        """.trimIndent()

        val result = IslandPayloadBuilder.preserveTriggerAreasJson(raw)

        val paramIsland = Json.parseToJsonElement(result)
            .jsonObject["param_v2"]!!
            .jsonObject["param_island"]!!
            .jsonObject
        assertTrue(paramIsland.containsKey("bigIslandArea"))
        assertTrue(paramIsland.containsKey("smallIslandArea"))
        assertTrue(paramIsland.containsKey("islandTimeout"))
    }

    @Test
    fun `alert payload uses two-line icon text layout instead of highlight hint`() {
        val title = "芝麻粒消失提醒"
        val content = "可攒30粒，产生后7天消失，请及时处理"

        val result = IslandPayloadBuilder.buildFocusParam(
            context = RuntimeEnvironment.getApplication(),
            title = title,
            content = content,
            style = NotificationStyle.ALERT,
        )

        val paramV2 = Json.parseToJsonElement(result).jsonObject["param_v2"]!!.jsonObject
        assertFalse(paramV2.containsKey("highlightInfo"))
        assertFalse(paramV2.containsKey("hintInfo"))
        assertEquals(title, paramV2["iconTextInfo"]!!.jsonObject["title"]!!.jsonPrimitive.contentOrNull)
        assertEquals(content, paramV2["iconTextInfo"]!!.jsonObject["content"]!!.jsonPrimitive.contentOrNull)

        val bigIslandArea = paramV2["param_island"]!!
            .jsonObject["bigIslandArea"]!!
            .jsonObject
        assertFalse(bigIslandArea.containsKey("imageTextInfoRight"))
        val left = bigIslandArea["imageTextInfoLeft"]!!.jsonObject
        assertEquals("miui.focus.pic_mipush_icon", left["picInfo"]!!.jsonObject["pic"]!!.jsonPrimitive.contentOrNull)
        val textInfo = left["textInfo"]!!.jsonObject
        assertEquals(title, textInfo["title"]!!.jsonPrimitive.contentOrNull)
        assertEquals(content, textInfo["content"]!!.jsonPrimitive.contentOrNull)
    }
}
