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
        assertTrue(logUtils.contains("val enabled = BUILD_LOGGING_ENABLED && verboseLogging"))
        assertTrue(logUtils.contains("if (!enabled)"))
        assertTrue(logUtils.contains("sink = null"))
    }

    @Test
    fun `runtime logger reads the build policy and never a preference`() {
        val app = readSource("io/github/magisk317/mipush/app/MiPushFrameworkApp.kt")

        assertTrue(app.contains("MagiskLogPolicy.current()"))
        assertTrue(app.contains("LogUtils.init(this, verboseLogging = policy.verboseLogging)"))
        assertFalse(app.contains("preferenceRepository.isDebugMode"))
        assertFalse(app.contains("apply" + "UserSwitch"))
    }

    @Test
    fun `all Android processes derive diagnostics from debug or CI only`() {
        val sources = listOf(
            projectFile("mipush/src/main/java/io/github/magisk317/mipush/app/App.kt"),
            projectFile("xmsf/shell/src/main/java/io/github/magisk317/mipush/app/MiPushFrameworkApp.kt"),
            projectFile("xposed/src/main/java/io/github/magisk317/mipush/hook/ModuleHooks.kt"),
        ).joinToString("\n") { it.readText() }
        val diagnosticExpression = "isDiagnosticBuild = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD"

        assertTrue(sources.split(diagnosticExpression).size - 1 == 3)
        assertFalse(sources.contains("ALLOW_" + "PLAINTEXT_LOGS"))
        assertFalse(sources.contains("allow" + "PlaintextLogs"))
    }

    @Test
    fun `hook file sinks exist only in diagnostic builds`() {
        val hooks = projectFile("xposed/src/main/java/io/github/magisk317/mipush/hook/ModuleHooks.kt").readText()

        assertTrue(hooks.contains("sink = if (BUILD_LOGGING_ENABLED && logPolicy.loggingEnabled)"))
        assertTrue(hooks.contains("SystemUiLocalLogSink.append(event)"))
        assertTrue(hooks.contains("if (logPolicy.loggingEnabled)"))
    }

    @Test
    fun `public release strips all XLog and Android Log levels`() {
        listOf("mipush/proguard-release.pro", "xmsf/proguard-release.pro").forEach { path ->
            val rules = projectFile(path).readText()
            listOf("void v", "void d", "void i", "void w", "void e", "void log", "void structured").forEach {
                assertTrue(rules.contains(it), "$path is missing: $it")
            }
            assertTrue(rules.contains("class android.util.Log"))
            assertTrue(rules.contains("int println"))
        }
    }

    @Test
    fun `suspended CI AAB documents its release-shaped diagnostic contract`() {
        val yaml = projectFile(".gitlab-ci.yml").readText()
        val job = yaml.substringAfter("# android:ci:aab:").substringBefore("#   artifacts:")

        assertTrue(job.contains("MAGISK_IS_CI_BUILD: \"true\""))
        assertTrue(job.contains(":mipush:bundlePlayRelease"))
        assertTrue(job.contains("signed, non-debuggable AAB"))
    }

    @Test
    fun `vendor MyLog is build-gated before every output path`() {
        val source = projectFile(
            "vendor/src/main/java/com/xiaomi/channel/commonutils/logger/MyLog.kt",
        ).readText()

        assertTrue(source.contains("BUILD_LOGGING_ENABLED = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD"))
        assertTrue(source.contains("if (!BUILD_LOGGING_ENABLED) return false"))
        assertTrue(source.contains("fun persist(str: String) {\n        if (!BUILD_LOGGING_ENABLED) return"))
        assertTrue(source.contains("fun printCallStack(str: String) {\n        if (!BUILD_LOGGING_ENABLED) return"))
        assertTrue(source.contains("fun ps(str: String): Int {\n        if (!BUILD_LOGGING_ENABLED) return NEGATIVE_CODE"))
    }

    @Test
    fun `production vendor code has no direct stderr or stacktrace output`() {
        val roots = listOf(projectFile("vendor/src/main"), projectFile("xmsf/shell/src/main"))
        val sources = roots.asSequence()
            .flatMap { root -> root.walkTopDown().asSequence() }
            .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
            .joinToString("\n") { file -> file.readText() }

        assertFalse(sources.contains(".printStack" + "Trace()"))
        assertFalse(sources.contains("System.err." + "println("))
    }

    @Test
    fun `all persisted message fields use the shared diagnostic budget`() {
        val xmsf = projectFile("xmsf/shell/src/main/java/io/github/magisk317/mipush/utils/LogUtils.kt").readText()
        val manager = projectFile(
            "manager/ui/src/main/java/io/github/magisk317/mipush/manager/logging/ManagerRuntimeFileLog.kt",
        ).readText()

        assertTrue(xmsf.contains("message = DefaultLogSanitizer.limitForLog("))
        assertTrue(xmsf.contains("throwable = DefaultLogSanitizer.limitForLog("))
        assertTrue(manager.contains("DefaultLogSanitizer.limitForLog("))
        val vendorFile = projectFile(
            "vendor/src/main/java/com/xiaomi/push/log/MIPushLog2File.kt",
        ).readText()
        assertTrue(vendorFile.contains("private const val MAX_LOG_LINE_CHARS = 80"))
        assertFalse(vendorFile.contains("io.github.magisk317"))
    }

    @Test
    fun `production code never re-enables the in-memory log test seam`() {
        val seamCall = Regex("\\.\\s*(setTestSink|setLogPrinterForTest)\\s*\\(")
        val offenders = productionSources().filter { source ->
            seamCall.containsMatchIn(source.readText())
        }.map { it.relativeTo(projectRoot()).invariantSeparatorsPath }

        assertTrue(
            offenders.isEmpty(),
            "A test sink bypasses the release log gate, so production code must not install one: $offenders",
        )
    }

    private fun projectRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun productionSources(): List<File> {
        val skipped = setOf("build", ".gradle", ".kotlin", "node_modules")
        return projectRoot().walkTopDown()
            .onEnter { dir -> dir.name !in skipped && !dir.name.startsWith(".") }
            .filter { it.isFile && it.extension in setOf("kt", "java") && it.invariantSeparatorsPath.contains("/src/main") }
            .toList()
    }

    private fun projectFile(relativePath: String): File {
        return generateSequence(File(".").canonicalFile) { it.parentFile }
            .map { root -> File(root, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Cannot resolve project file: $relativePath")
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
