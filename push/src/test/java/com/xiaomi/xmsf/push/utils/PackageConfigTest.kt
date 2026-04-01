package com.xiaomi.xmsf.push.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageConfigTest {
    @Test
    fun replacePlaceholders_replacesEscapedDollarAndNamedGroup() {
        val result = PackageConfig.replacePlaceholders(
            value = "\$\$ \${name} \${count}",
            matchGroup = mapOf(
                "name" to "QQ",
                "count" to "3",
            ),
        )

        assertEquals("$ QQ 3", result)
    }

    @Test
    fun replacePlaceholders_keepsUnknownPlaceholder() {
        val result = PackageConfig.replacePlaceholders(
            value = "hello \${unknown}",
            matchGroup = mapOf("name" to "QQ"),
        )

        assertEquals("hello \${unknown}", result)
    }
}
