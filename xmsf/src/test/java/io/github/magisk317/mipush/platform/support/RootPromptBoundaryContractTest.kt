package io.github.magisk317.mipush.platform.support

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootPromptBoundaryContractTest {
    @Test
    fun `app startup does not schedule root-backed permission grants`() {
        val source = readSource("io/github/magisk317/mipush/app/MiPushFrameworkApp.kt")

        assertFalse(source.contains("scheduleSilentPermissionGrants"))
        assertFalse(source.contains("grantSilentPermissionsForFramework"))
    }

    @Test
    fun `passive runtime probes never request root authorization`() {
        val source = readSource("io/github/magisk317/mipush/app/di/ManagerRuntimeAdapters.kt")
        val dualAppProbe = source.section("override fun isDualAppInstalled", "override fun launchAppOps")
        val zygiskGateway = source.section("class XmsfZygiskConfigGateway", "\n}")
        val zygiskProbe = source.section("override fun isZygiskModuleEnabled", "override fun saveZygiskConfig")

        assertFalse(dualAppProbe.contains("requestRootAccess"))
        assertFalse(zygiskProbe.contains("requestRootAccess"))
        assertFalse(zygiskProbe.contains("requestAuthorization = true"))
        assertTrue(dualAppProbe.contains("refreshRootAccessIfGranted"))
        assertTrue(zygiskProbe.contains("hasExistingRootForZygisk"))
        assertTrue(zygiskGateway.contains("refreshRootAccessIfGranted"))
    }

    @Test
    fun `low-level permission writes consume existing root only`() {
        val source = readSource("io/github/magisk317/mipush/platform/support/PermissionUtils.kt")
        val lowLevelWrites = listOf(
            source.section("fun allowPermission", "fun grantSilentPermissions"),
            source.section("fun grantSilentPermissions(", "fun grantSilentPermissionsForFramework"),
            source.section("fun grantSilentPermissionsForFramework", "fun lunchAppOps"),
            source.section("fun syncLauncherIconAliases", "\n}"),
        )

        assertFalse(source.contains("ensureRootAccess"))
        lowLevelWrites.forEach { operation ->
            assertFalse(operation.contains("requestRootAccess"))
            assertTrue(operation.contains("hasExistingRootAccess"))
        }
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("xmsf/src/main/java/$relativePath"),
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
