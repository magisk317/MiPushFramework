package io.github.magisk317.mipush.manager.runtime.write

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerUnregisterWriteContractTest {
    @Test
    fun `executor dispatches unregister to the application gateway`() {
        val executor = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )
        assertTrue(
            executor.contains(
                "ManagerProtocol.WRITE_OP_UNREGISTER_APPLICATION -> unregisterApplication(request)",
            ),
        )
        val handler = executor.section(
            "private suspend fun unregisterApplication(",
            "private suspend fun deleteEvent(",
        )
        assertTrue(handler.contains("applicationGateway.unregisterApplication("))
    }

    @Test
    fun `force register clears the block before relaunching the app`() {
        val adapter = readSource("io/github/magisk317/mipush/app/di/ManagerApplicationAdapter.kt")
        val body = adapter.section(
            "override suspend fun launchTargetAppAndForceRegister(",
            "private fun launchTargetAppViaRoot(",
        )
        val unblock = body.indexOf("prepareForceRegistrationAdmission(packageName, forceUserId)")
        val relaunch = body.indexOf("force_register_relaunch")
        assertTrue(unblock >= 0)
        assertTrue(relaunch >= 0)
        assertTrue(unblock < relaunch)
    }

    @Test
    fun `registration bridge exposes the unregister seam implemented over vendor`() {
        val iface = readSource("io/github/magisk317/mipush/push/bridge/PushShellBridge.kt")
        assertTrue(iface.contains("fun rememberedAppId("))
        assertTrue(iface.contains("fun reportAppAbsent("))
        assertTrue(iface.contains("fun markPackageUnregistered("))

        val impl = readSource("io/github/magisk317/mipush/push/bridge/DefaultPushShellBridge.kt")
        assertTrue(impl.contains("override fun reportAppAbsent("))
        assertTrue(impl.contains("MIPushAppAbsentManager.flushPending("))
        assertTrue(impl.indexOf(".commit()") < impl.indexOf("MIPushAppAbsentManager.flushPending("))
        assertTrue(impl.contains("addUnRegisteredPkg(packageName)"))
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("../push/src/main/java/$relativePath"),
            File("../runtime/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing section start: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing section end: $end" }
        return substring(startIndex, endIndex)
    }
}
