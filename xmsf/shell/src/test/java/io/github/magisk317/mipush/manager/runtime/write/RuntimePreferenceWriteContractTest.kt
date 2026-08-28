package io.github.magisk317.mipush.manager.runtime.write

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimePreferenceWriteContractTest {
    @Test
    fun `retention writes use the injected runtime preference repository before side effects`() {
        val source = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )
        val logRetention = source.section(
            "ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION ->",
            "ManagerProtocol.WRITE_OP_START_FOREGROUND ->",
        )
        val eventRetention = source.section(
            "ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION ->",
            "ManagerProtocol.WRITE_OP_COUNT_EVENTS_BY_DAY ->",
        )

        assertTrue(logRetention.contains("preferenceRepository.setRuntimeLogRetentionDays(days)"))
        assertTrue(eventRetention.contains("preferenceRepository.setEventRetentionDays(days)"))
        assertFalse(source.contains("PreferenceRepository().setRuntimeLogRetentionDays"))
        assertFalse(source.contains("PreferenceRepository().setEventRetentionDays"))
    }

    @Test
    fun `executor constructor resolves the same repository from runtime dependencies`() {
        val source = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )

        assertTrue(source.contains("private val preferenceRepository: PreferenceRepository"))
        assertTrue(source.contains("preferenceRepository = AppDependencies.get(context)"))
        assertFalse(source.contains("val repo = PreferenceRepository()"))
        assertFalse(source.contains("PreferenceRepository().setIslandRendererMode"))
    }

    @Test
    fun `analytics is runtime owned and reconfigures the runtime process`() {
        val executor = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )
        val app = readSource("io/github/magisk317/mipush/app/MiPushFrameworkApp.kt")

        val support = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerRuntimePreferenceCommandSupport.kt",
        )
        assertTrue(executor.contains("ManagerRuntimePreferenceCommandSupport.setRuntimeBoolean("))
        assertTrue(support.contains("ENABLE_ANALYTICS_KEY -> preferenceRepository.setAnalyticsEnabled(enabled)"))
        assertTrue(support.contains("ENABLE_ANALYTICS_KEY,"))
        assertTrue(app.contains("preferenceRepository.isAnalyticsEnabled.collect(::configureAnalytics)"))
    }

    @Test
    fun `foreground preference applies enabled and disabled runtime state`() {
        val executor = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )
        val support = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerRuntimePreferenceCommandSupport.kt",
        )

        assertTrue(executor.contains("ManagerRuntimePreferenceCommandSupport.setRuntimeBoolean("))
        assertTrue(support.contains("preferenceRepository.setIsStartForeground(enabled)"))
        assertTrue(support.contains("applyForegroundServicePolicy(enabled, context, runtimeActions)"))
        assertTrue(support.contains("ForegroundHelper(service).stopForegroundNotification()"))
        assertTrue(support.contains("runtimeActions.startMiPushServiceAsForegroundService(context)"))
    }

    @Test
    fun `runtime dispatch does not nest blocking coroutine bridges`() {
        val source = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )
        val dispatch = source.section(
            "private suspend fun dispatch",
            "private suspend fun zygiskIsEnabled",
        )

        assertFalse(dispatch.contains("runBlocking"))
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
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
