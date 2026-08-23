package io.github.magisk317.mipush.utils

import android.app.Application
import android.content.Context
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
@Execution(ExecutionMode.SAME_THREAD)
class LogSanitizationContractRobolectricTest {
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LogSanitizerConfig.setEnabled(true)
        LogUtils.resetForTest()
        LogBundleExporter.clearLogFolders(context)
        LogUtils.init(context)
    }

    @AfterEach
    fun tearDown() {
        LogUtils.resetForTest()
        LogSanitizerConfig.setEnabled(false)
        LogBundleExporter.clearLogFolders(context)
    }

    @Test
    fun `app and module jsonl sinks redact message and throwable secrets`() {
        val token = "file-token-secret-123456"
        val bearer = "file-bearer-secret-123456"
        val phone = "13800138000"
        val message = "token=$token Authorization: Bearer $bearer phone=$phone"
        val throwable = IllegalStateException("stack token=$token phone=$phone")

        Logger.withTag("FileSanitizerContract").e(throwable) { message }
        LogUtils.appendModuleLog(
            context = context,
            source = "sanitizer_contract",
            level = "E",
            tag = "ModuleSanitizerContract",
            packageName = context.packageName,
            processName = "test",
            message = message,
            throwable = throwable.stackTraceToString(),
        )

        val files = LogUtils.summarizeFiles(context).files
        assertTrue(files.isNotEmpty())
        val text = files.joinToString("\n") { info ->
            LogUtils.readLogFile(context, info.name)?.text.orEmpty()
        }
        assertNotNull(text)
        assertFalse(text.contains(token))
        assertFalse(text.contains(bearer))
        assertFalse(text.contains(phone))
        assertTrue(text.contains("\\\"throwable\\\"") || text.contains("\"throwable\""))
    }
}
