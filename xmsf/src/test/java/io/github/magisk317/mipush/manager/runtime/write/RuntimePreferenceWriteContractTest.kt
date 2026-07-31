package io.github.magisk317.mipush.manager.runtime.write

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimePreferenceWriteContractTest {
    @Test
    fun `retention writes persist runtime state before applying side effects`() {
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

        assertTrue(logRetention.contains("PreferenceRepository().setRuntimeLogRetentionDays(days)"))
        assertTrue(eventRetention.contains("PreferenceRepository().setEventRetentionDays(days)"))
    }

    @Test
    fun `analytics is runtime owned and reconfigures the runtime process`() {
        val executor = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )
        val app = readSource("io/github/magisk317/mipush/app/MiPushFrameworkApp.kt")

        assertTrue(executor.contains("ENABLE_ANALYTICS_KEY -> repo.setAnalyticsEnabled(enabled)"))
        assertTrue(executor.contains("ENABLE_ANALYTICS_KEY,"))
        assertTrue(app.contains("preferenceRepository.isAnalyticsEnabled.collect(::configureAnalytics)"))
    }

    @Test
    fun `foreground preference applies enabled and disabled runtime state`() {
        val source = readSource(
            "io/github/magisk317/mipush/manager/runtime/write/ManagerWriteRuntimeExecutor.kt",
        )
        val setBoolean = source.section(
            "private fun setRuntimeBoolean",
            "private fun setRuntimeInt",
        )

        assertTrue(setBoolean.contains("repo.setIsStartForeground(enabled)"))
        assertTrue(setBoolean.contains("applyForegroundServicePolicy(enabled)"))
        assertTrue(setBoolean.contains("ForegroundHelper(service).stopForegroundNotification()"))
        assertTrue(setBoolean.contains("runtimeActions.startMiPushServiceAsForegroundService(context)"))
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
