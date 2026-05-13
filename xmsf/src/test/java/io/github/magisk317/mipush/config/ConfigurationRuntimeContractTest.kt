package io.github.magisk317.mipush.config

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.ConfigurationsLoader
import io.github.magisk317.mipush.utils.PackageConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class ConfigurationRuntimeContractTest {
    @Test
    fun `remote config json is parsed by runtime and preserves notification policy operations`() {
        val configurations = Configurations(ConfigurationsLoader())
        configurations.load(
            """
            {
              "version": "1",
              "configs": {
                "com.example.app": [
                  {
                    "match": {
                      "metaInfo": {
                        "title": "^Legacy (?<name>.+)$",
                        "description": "^Body$",
                        "extra": {
                          "msg_busi_type": "voip",
                          "notification_style_type": "4",
                          "miui.focus.param": ".*",
                          "sweet_tag": "raw"
                        }
                      }
                    },
                    "replace": {
                      "metaInfo": {
                        "title": "Configured ${'$'}{name}",
                        "description": "Configured body",
                        "extra": {
                          "notification_style_type": "6",
                          "miui.focus.param": "{\"updatable\":true,\"reopen\":\"close\"}",
                          "sweet_tag": "<ft color=\"#fff\">tag</ft>",
                          "legacy_key": null
                        }
                      }
                    },
                    "operation": "ignore wake open notify",
                    "stop": true
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val container = notificationContainer()

        val operations = configurations.handle("com.example.app", container)
        val extra = requireNotNull(container.metaInfo.extra)

        assertEquals(
            setOf(
                PackageConfig.OPERATION_IGNORE,
                PackageConfig.OPERATION_WAKE,
                PackageConfig.OPERATION_OPEN,
                PackageConfig.OPERATION_NOTIFY,
            ),
            operations,
        )
        assertEquals("Configured Alice", container.metaInfo.title)
        assertEquals("Configured body", container.metaInfo.description)
        assertEquals("6", extra["notification_style_type"])
        assertEquals("""{"updatable":true,"reopen":"close"}""", extra["miui.focus.param"])
        assertEquals("""<ft color="#fff">tag</ft>""", extra["sweet_tag"])
        assertEquals("voip", extra["msg_busi_type"])
        assertFalse(extra.containsKey("legacy_key"))
    }

    @Test
    fun `runtime config stop prevents package fallback rules from overriding a match`() {
        val configurations = Configurations(ConfigurationsLoader())
        configurations.load(
            """
            {
              "version": "1",
              "configs": {
                "^": [
                  {
                    "match": { "metaInfo": { "title": ".*" } },
                    "replace": { "metaInfo": { "title": "Global" } },
                    "operation": "notify",
                    "stop": true
                  }
                ],
                "com.example.app": [
                  {
                    "match": { "metaInfo": { "title": "Global" } },
                    "replace": { "metaInfo": { "title": "Package" } },
                    "operation": "open",
                    "stop": true
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val container = notificationContainer()

        val operations = configurations.handle("com.example.app", container)

        assertTrue(PackageConfig.OPERATION_NOTIFY in operations)
        assertFalse(PackageConfig.OPERATION_OPEN in operations)
        assertEquals("Global", container.metaInfo.title)
    }

    private fun notificationContainer(): XmPushActionContainer {
        return XmPushActionContainer().apply {
            action = ActionType.SendMessage
            packageName = "com.example.app"
            metaInfo = PushMetaInfo().apply {
                title = "Legacy Alice"
                description = "Body"
                extra = mutableMapOf(
                    "msg_busi_type" to "voip",
                    "notification_style_type" to "4",
                    "miui.focus.param" to """{"updatable":false}""",
                    "sweet_tag" to "raw",
                    "legacy_key" to "delete-me",
                )
            }
        }
    }
}
