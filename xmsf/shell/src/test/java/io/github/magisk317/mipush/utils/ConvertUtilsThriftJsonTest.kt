package io.github.magisk317.mipush.utils

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The runtime event debug JSON must expose the full thrift message body (title, description,
 * notifyType, passThrough, topic, extra maps), not the previous fixed five-field reflection
 * whitelist. Regression guard for the "runtime decodes the container but the payload contents
 * stay invisible" gap in the manager event-detail debug panel.
 */
class ConvertUtilsThriftJsonTest {

    @Test
    fun `meta info body is fully serialised`() {
        val metaInfo = PushMetaInfo().apply {
            setId("mid-1")
            setTitle("标题")
            setDescription("内容")
            setTopic("topic-x")
            setNotifyType(1)
            setPassThrough(0)
            setExtra(mapOf("custom_key" to "custom_value"))
        }
        val container = XmPushActionContainer().apply {
            setAction(ActionType.SendMessage)
            setPackageName("com.example.app")
            setMetaInfo(metaInfo)
            // the pinned thrift container defaults encryptAction to true
            setEncryptAction(false)
        }

        val root = ConvertUtils.toJson(container, null, userId = 0) as? JsonObject
            ?: error("expected JsonObject")

        assertEquals("SendMessage", (root["action"] as JsonPrimitive).content)
        val meta = root["metaInfo"] as? JsonObject ?: error("metaInfo missing from debug json")
        assertEquals("标题", (meta["title"] as JsonPrimitive).content)
        assertEquals("内容", (meta["description"] as JsonPrimitive).content)
        assertEquals("topic-x", (meta["topic"] as JsonPrimitive).content)
        assertEquals("mid-1", (meta["id"] as JsonPrimitive).content)
        assertEquals("1", (meta["notifyType"] as JsonPrimitive).content)
        assertEquals("0", (meta["passThrough"] as JsonPrimitive).content)
        val extra = meta["extra"] as? JsonObject ?: error("extra map missing")
        assertEquals("custom_value", (extra["custom_key"] as JsonPrimitive).content)
    }

    @Test
    fun `container without payload reports the empty payload marker`() {
        val container = XmPushActionContainer().apply {
            setAction(ActionType.SendMessage)
            setPackageName("com.example.app")
            // the pinned thrift container defaults encryptAction to true
            setEncryptAction(false)
        }

        val root = ConvertUtils.toJson(container, null, userId = 0) as? JsonObject
            ?: error("expected JsonObject")

        assertEquals("empty_payload", (root["pushActionUnavailable"] as JsonPrimitive).content)
        assertTrue(root["metaInfo"] == null)
        // regSec diagnostics are only meaningful for encrypted containers; their candidate
        // lookup touches Android-only storage, so the encrypted path is pinned by the
        // EventDebugEnrichmentContractTest source contracts instead.
        assertTrue(root["regSec"] == null)
    }

    @Test
    fun `embedded json strings inside extra maps are expanded`() {
        val metaInfo = PushMetaInfo().apply {
            setTitle("标题")
            setExtra(
                mapOf(
                    "config_json" to "{\"inner\":{\"n\":1},\"flag\":true}",
                    "list_json" to "[\"a\",\"b\"]",
                    "plain" to "not json",
                    "brace_text" to "{not closed",
                ),
            )
        }
        val container = XmPushActionContainer().apply {
            setAction(ActionType.SendMessage)
            setPackageName("com.example.app")
            setMetaInfo(metaInfo)
            setEncryptAction(false)
        }

        val root = ConvertUtils.expandEmbeddedJson(ConvertUtils.toJson(container, null, userId = 0))
            as? JsonObject ?: error("expected JsonObject")

        val meta = root["metaInfo"] as? JsonObject ?: error("metaInfo missing")
        val extra = meta["extra"] as? JsonObject ?: error("extra map missing")
        val config = extra["config_json"] as? JsonObject ?: error("embedded json object not expanded")
        val inner = config["inner"] as? JsonObject ?: error("nested expansion missing")
        assertEquals("1", (inner["n"] as JsonPrimitive).content)
        assertEquals("true", (config["flag"] as JsonPrimitive).content)
        val list = extra["list_json"] as? kotlinx.serialization.json.JsonArray
            ?: error("embedded json array not expanded")
        assertEquals(2, list.size)
        // non-JSON strings stay untouched
        assertEquals("not json", (extra["plain"] as JsonPrimitive).content)
        assertEquals("{not closed", (extra["brace_text"] as JsonPrimitive).content)
    }
}
