package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.app.ConfigCenter
import io.mockk.mockk
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IconConfigurationsTest {
    private val configurations = IconConfigurations(mockk<ConfigCenter>())

    @Test
    fun `parse indexes valid configurations by package name`() {
        val result = configurations.parse(
            """[{"appName":"Example","packageName":"com.example.app","iconColor":"#112233"}]""",
        )

        assertEquals("Example", result.getValue("com.example.app").appName)
        assertEquals("#112233", result.getValue("com.example.app").iconColor)
    }

    @Test
    fun `parse skips configurations without a usable package name`() {
        val result = configurations.parse(
            """[{"appName":"Missing"},{"appName":"Blank","packageName":""}]""",
        )

        assertEquals(emptyMap<String, IconConfigurations.IconConfig>(), result)
    }

    @Test
    fun `parse rejects malformed json`() {
        assertThrows(SerializationException::class.java) {
            configurations.parse("[{not-json}]")
        }
    }
}
