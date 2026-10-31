package io.github.magisk317.mipush.data

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Log verbosity, redaction and export are build-channel properties.
 *
 * There is no `debug_mode` preference any more: it had no UI, yet it could still raise the log
 * level and relax redaction through the Binder write path and preference import. That is how a
 * shipped build could end up verbose while its own health snapshot reported the opposite, and why
 * a CI build was not verbose by default.
 *
 * These assertions are deliberately source-level: they pin the invariants that a refactor must not
 * silently undo.
 */
class DebugChannelContractTest {
    @Test
    fun `no debug mode preference survives anywhere`() {
        val repository = readSource("../../settings/src/main/java/io/github/magisk317/mipush/data/PreferenceRepository.kt")

        assertFalse(repository.contains("debug_mode"))
        assertFalse(repository.contains("DEBUG_MODE"))
        assertFalse(repository.contains("isDebugMode"))
        assertFalse(repository.contains("setDebugMode"))
    }

    @Test
    fun `ownership catalog carries no logging switch`() {
        val ownership = readSource("../../settings/src/main/java/io/github/magisk317/mipush/data/PreferenceOwnership.kt")

        assertFalse(ownership.contains("debug_mode"))
        assertFalse(ownership.contains("LOG_SANITIZATION_ENABLED_KEY"))
    }

    @Test
    fun `manager no longer exposes the dead debug switch`() {
        val viewModel = readSource(
            "../../manager/ui/src/main/java/io/github/magisk317/mipush/main/viewmodel/SettingsViewModel.kt",
        )

        assertFalse(viewModel.contains("debugMode"))
        assertFalse(viewModel.contains("setDebugMode"))
        assertFalse(viewModel.contains("logSanitizationEnabled"))
        assertFalse(viewModel.contains("setLogSanitizationEnabled"))
    }

    @Test
    fun `runtime preference writer rejects the retired logging keys`() {
        val support = readSource(
            "../../xmsf/shell/src/main/java/io/github/magisk317/mipush/manager/runtime/write/" +
                "ManagerRuntimePreferenceCommandSupport.kt",
        )

        assertFalse(support.contains("\"debug_mode\""))
        assertFalse(support.contains("LOG_SANITIZATION_ENABLED_KEY"))
    }

    @Test
    fun `release build installs no log file sink`() {
        val logUtils = readSource("io/github/magisk317/mipush/utils/LogUtils.kt")

        assertTrue(logUtils.contains("verboseLogging: Boolean"))
        assertTrue(logUtils.contains("if (!verboseLogging)"))
        assertTrue(logUtils.contains("sink = null"))
    }

    @Test
    fun `runtime logger reads the build policy and never a preference`() {
        val app = readSource("io/github/magisk317/mipush/app/MiPushFrameworkApp.kt")

        assertTrue(app.contains("MagiskLogPolicy.current()"))
        assertTrue(app.contains("LogUtils.init(this, verboseLogging = policy.verboseLogging)"))
        assertFalse(app.contains("preferenceRepository.isDebugMode"))
        assertFalse(app.contains("applyUserSwitch"))
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("../$relativePath"),
            File(relativePath),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }
}
