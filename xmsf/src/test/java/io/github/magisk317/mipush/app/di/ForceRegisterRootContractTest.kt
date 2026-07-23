package io.github.magisk317.mipush.app.di

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ForceRegisterRootContractTest {
    @Test
    fun `force register requests root exactly once per click path`() {
        val source = resolveSource().readText()
        val operation = source.substring(
            startIndex = source.indexOf("override suspend fun launchTargetAppAndForceRegister"),
            endIndex = source.indexOf("private fun refreshTransientState"),
        )
        val feedback = operation.substring(
            startIndex = operation.indexOf("private fun forceRegisterWithFeedback"),
        )

        assertEquals(1, Regex("PermissionUtils\\.requestRootAccess\\(\\)").findAll(operation).count())
        assertFalse(feedback.contains("requestRootAccess()"))
    }

    private fun resolveSource(): File {
        val candidates = listOf(
            File("src/main/java/io/github/magisk317/mipush/app/di/ManagerRuntimeAdapters.kt"),
            File("xmsf/src/main/java/io/github/magisk317/mipush/app/di/ManagerRuntimeAdapters.kt"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("ManagerRuntimeAdapters.kt not found from ${File(".").absolutePath}")
    }
}
