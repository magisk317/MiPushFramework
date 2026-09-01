package io.github.magisk317.mipush.manager.runtime.read

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerEventRuntimeReaderContractTest {
    @Test
    fun `reader rejects invalid users before querying and passes explicit scope`() {
        val source = resolveSource().readText()

        assertTrue(source.contains("require(query.userId >= 0)"))
        assertTrue(source.contains("userId = query.userId"))
        assertFalse(source.contains("query.userId.coerceAtLeast(0)"))
        assertFalse(source.contains("query.userId.takeIf"))
    }

    private fun resolveSource(): File {
        val relativePath =
            "src/main/java/io/github/magisk317/mipush/manager/runtime/read/" +
                "ManagerEventRuntimeReader.kt"
        return listOf(File(relativePath), File("xmsf/$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("ManagerEventRuntimeReader.kt not found")
    }
}
