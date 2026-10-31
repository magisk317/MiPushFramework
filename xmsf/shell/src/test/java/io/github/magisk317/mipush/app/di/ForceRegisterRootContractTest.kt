package io.github.magisk317.mipush.app.di

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForceRegisterRootContractTest {
    @Test
    fun `force register requests root exactly once per click path`() {
        val source = resolveSource().readText()
        val operation = source.substring(
            startIndex = source.indexOf("override suspend fun launchTargetAppAndForceRegister"),
        )
        val feedback = operation.substring(
            startIndex = operation.indexOf("private suspend fun forceRegisterWithFeedback"),
        )

        assertEquals(1, Regex("PermissionUtils\\.requestRootAccess\\(\\)").findAll(operation).count())
        assertFalse(feedback.contains("requestRootAccess()"))
    }

    @Test
    fun `dispatch success is confirmed by a registration result instead of the send itself`() {
        val source = resolveSource().readText()

        // The result path must wait for the app to report back, not report the send as success.
        assertTrue(source.contains("private suspend fun awaitForceRegisterResult("))
        assertTrue(source.contains("RegisteredAppRegisteredType.Registered"))
        assertTrue(source.contains("force_register_server_result_timeout"))
        assertTrue(source.contains("force_register_request_not_seen"))
        // Pull-up goes through root's ActivityManager first; the SDK pull-up is only a fallback.
        assertTrue(source.contains("am start --user"))
        assertTrue(source.contains("force_register_root_launch_failed"))
    }

    private fun resolveSource(): File {
        val candidates = listOf(
            File("src/main/java/io/github/magisk317/mipush/app/di/ManagerApplicationAdapter.kt"),
            File("../runtime/src/main/java/io/github/magisk317/mipush/app/di/ManagerApplicationAdapter.kt"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("ManagerRuntimeAdapters.kt not found from ${File(".").absolutePath}")
    }
}
