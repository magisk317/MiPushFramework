package io.github.magisk317.mipush.runtime.core.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigJsonSupportTest {
    @Test
    fun validateAndFormat_formatsValidJson() {
        val result = ConfigJsonSupport.validateAndFormat("""{"version":"1","configs":{"pkg":[]}}""")
        val formatted = requireNotNull(result.formatted)

        assertTrue(result.valid)
        assertTrue(formatted.contains("\n"))
        assertTrue(formatted.contains("\"version\""))
    }

    @Test
    fun validateAndFormat_reportsLineAndColumnForInvalidJson() {
        val result = ConfigJsonSupport.validateAndFormat(
            """
            {
              "version": "1",
              "configs": {
                "pkg": [
              }
            }
            """.trimIndent(),
        )

        assertFalse(result.valid)
        assertNotNull(result.errorMessage)
        assertTrue((result.line ?: 0) > 0)
        assertTrue((result.column ?: 0) > 0)
    }
}
