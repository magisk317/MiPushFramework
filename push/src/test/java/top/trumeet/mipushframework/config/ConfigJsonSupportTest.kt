package top.trumeet.mipushframework.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
