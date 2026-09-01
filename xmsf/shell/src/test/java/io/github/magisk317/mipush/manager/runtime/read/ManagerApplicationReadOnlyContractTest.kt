package io.github.magisk317.mipush.manager.runtime.read

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerApplicationReadOnlyContractTest {
    @Test
    fun `binder application source contains only read dependencies`() {
        val source = resolveSource().readText()

        assertTrue(source.contains("registeredApplicationDao.getAll("))
        assertTrue(source.contains("override suspend fun readStoredApplications"))
        assertFalse(source.contains("runBlocking"))
        assertFalse(source.contains("RegisteredApplicationDb"))
        assertFalse(source.contains("RegistrationStateStore"))
        assertFalse(Regex("""\.(insert|insertOrReplace|update|delete)\(""").containsMatchIn(source))
        assertTrue(source.contains("override suspend fun currentUserId(): Int = resolveCurrentUserId()"))
        assertTrue(source.contains("private fun resolveCurrentUserId(): Int = runCatching"))
        assertFalse(source.contains("Utils.myUserId().coerceAtLeast(0)"))
        assertFalse(source.contains("registeredApplicationDao.getAll(Utils.myUserId()"))
        assertFalse(source.contains("--user \${Utils.myUserId().coerceAtLeast(0)}"))
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
