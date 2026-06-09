package io.github.magisk317.mipush.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfigLocalPathSupportTest {
    @Test
    fun `parse accepts root json config`() {
        val path = ConfigLocalPathSupport.parse("com.example.app.json")

        assertEquals("com.example.app.json", path.path)
        assertEquals("com.example.app.json", path.fileName)
        assertEquals(emptyList<String>(), path.parentSegments)
        assertEquals("com.example.app", path.name)
    }

    @Test
    fun `parse accepts icon json config`() {
        val path = ConfigLocalPathSupport.parse("icon/NotifyIconsSupportConfig.json")

        assertEquals("icon/NotifyIconsSupportConfig.json", path.path)
        assertEquals("NotifyIconsSupportConfig.json", path.fileName)
        assertEquals(listOf("icon"), path.parentSegments)
        assertEquals("icon/NotifyIconsSupportConfig", path.name)
    }

    @Test
    fun `parse rejects unsupported nested metadata path`() {
        assertThrows<IllegalArgumentException> {
            ConfigLocalPathSupport.parse("_meta/config-index.json")
        }
    }

    @Test
    fun `parse rejects traversal path`() {
        assertThrows<IllegalArgumentException> {
            ConfigLocalPathSupport.parse("icon/../config.json")
        }
    }
}
