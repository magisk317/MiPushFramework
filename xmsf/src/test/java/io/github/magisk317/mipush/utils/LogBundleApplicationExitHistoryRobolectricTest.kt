package io.github.magisk317.mipush.utils

import android.app.Application
import android.content.Context
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.util.Date
import java.util.zip.ZipFile

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35], application = Application::class)
class LogBundleApplicationExitHistoryRobolectricTest {
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
        LogBundleExporter.resetRootCommandAccessForTest()
        LogBundleExporter.clearLogFolders(context)
    }

    @Test
    fun `export captures application exit history on api thirty`() {
        val currentDate = LogUtils.currentDateString(Date())
        File(LogBundleExporter.getLogDir(context), "runtime.$currentDate.jsonl")
            .writeText("""{"timestamp":1,"message":"hello"}""")

        val result = LogBundleExporter.buildLogBundle(context, DiagnosticExportMode.FULL)

        val zip = result.file
        assertNotNull(zip)
        val history = ZipFile(zip!!).use { archive ->
            val entry = archive.getEntry("system/application_exit_history.txt")
            assertNotNull(entry)
            archive.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        assertTrue(history.contains("# ApplicationExitInfo history"))
        assertTrue(history.contains("count="))
        assertTrue(result.details.contains("application exit history"))
    }
}
