package io.github.magisk317.mipush.common.configurations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigRulePlansTest {
    @Test
    fun `match extracts named groups through neutral accessor`() {
        val config = ConfigJsonObject().put("title", "(?<name>Mi.+)")
        val result = ConfigRulePlans.match(config) { path ->
            assertEquals(listOf("title"), path)
            ConfigFieldValue("MiPush")
        }

        assertEquals(mapOf("name" to "MiPush"), result)
    }

    @Test
    fun `nested match uses container accessor and rejects mismatches`() {
        val config = ConfigJsonObject().put(
            "metaInfo",
            ConfigJsonObject().put("title", "MiPush"),
        )
        val values = mapOf(listOf("metaInfo") to "container", listOf("metaInfo", "title") to "MiPush")
        val result = ConfigRulePlans.match(config) { path ->
            ConfigFieldValue(values[path], isContainer = path == listOf("metaInfo"))
        }

        assertEquals(emptyMap<String, String>(), result)
        assertNull(
            ConfigRulePlans.match(config) { path ->
                ConfigFieldValue(values[path]?.replace("Mi", "Other"), isContainer = path == listOf("metaInfo"))
            },
        )
    }

    @Test
    fun `placeholder replacement preserves unknown groups and escaped dollars`() {
        assertEquals(
            "$ MiPush ${'$'}{unknown}",
            ConfigRulePlans.replacePlaceholders("$$ ${'$'}{name} ${'$'}{unknown}", mapOf("name" to "MiPush")),
        )
    }
}
