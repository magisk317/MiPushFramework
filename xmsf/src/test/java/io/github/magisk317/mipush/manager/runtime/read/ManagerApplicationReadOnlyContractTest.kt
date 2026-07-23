package io.github.magisk317.mipush.manager.runtime.read

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerApplicationReadOnlyContractTest {
    @Test
    fun `binder application source contains only read dependencies`() {
        val source = resolveSource().readText()

        assertTrue(source.contains("registeredApplicationDao.getAll()"))
        assertFalse(source.contains("RegisteredApplicationDb"))
        assertFalse(source.contains("RegistrationStateStore"))
        assertFalse(Regex("""\.(insert|insertOrReplace|update|delete)\(""").containsMatchIn(source))
    }

    private fun resolveSource(): File {
        val relativePath =
            "src/main/java/io/github/magisk317/mipush/manager/runtime/read/" +
                "AndroidManagerApplicationReadSource.kt"
        return listOf(File(relativePath), File("xmsf/$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("AndroidManagerApplicationReadSource.kt not found")
    }
}
