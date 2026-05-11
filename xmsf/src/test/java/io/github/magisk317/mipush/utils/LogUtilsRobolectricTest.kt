package io.github.magisk317.mipush.utils

import android.content.Context
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LogUtilsRobolectricTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LogBundleExporter.clearLogFolders(context)
        LogUtils.init(context)
        LogUtils.setRetentionDays(7)
    }

    @After
    fun tearDown() {
        LogBundleExporter.resetRootCommandAccessForTest()
        LogBundleExporter.clearLogFolders(context)
    }

    @Test
    fun `app logs are written as jsonl with daily aggregate file`() {
        Napier.i("hello token=secret", tag = "DiagTest")

        val summary = LogUtils.summarizeFiles(context)
        val aggregate = summary.files.firstOrNull { it.name.matches(Regex("""runtime\.\d{4}-\d{2}-\d{2}\.jsonl""")) }
        assertNotNull(aggregate)

        val content = LogUtils.readLogFile(context, aggregate!!.name)
        assertNotNull(content)
        assertTrue(content!!.text.contains(""""tag":"DiagTest""""))
        assertTrue(content.text.contains(""""message":"hello token=secret""""))
    }

    @Test
    fun `module logs use source route jsonl file`() {
        LogUtils.appendModuleLog(
            context = context,
            source = "sms_hook",
            level = "I",
            tag = "ModuleDiag",
            packageName = "pkg",
            processName = "proc",
            message = "module hello",
            throwable = "",
        )

        val summary = LogUtils.summarizeFiles(context)
        val moduleFile = summary.files.firstOrNull { it.name.contains("runtime.sms_hook.") }
        assertNotNull(moduleFile)
        val content = LogUtils.readLogFile(context, moduleFile!!.name)
        assertNotNull(content)
        assertTrue(content!!.text.contains(""""source":"sms_hook""""))
        assertTrue(content.text.contains("module hello"))
    }

    @Test
    fun `legacy text logs are deleted without compatibility fallback`() {
        val logDir = LogBundleExporter.getLogDir(context)
        val moduleDir = File(logDir, "modules").apply { mkdirs() }
        val appTextLog = File(logDir, "logs_2026-05-10.txt").apply { writeText("old") }
        val runtimeTextLog = File(logDir, "runtime.log").apply { writeText("old") }
        val moduleTextLog = File(moduleDir, "module_2026-05-10.txt").apply { writeText("old") }

        val deleted = LogUtils.deleteLegacyTextLogFiles(context)

        assertTrue(deleted >= 3)
        assertFalse(appTextLog.exists())
        assertFalse(runtimeTextLog.exists())
        assertFalse(moduleTextLog.exists())
    }

    @Test
    fun `retention deletes expired jsonl files by day`() {
        val logDir = LogBundleExporter.getLogDir(context)
        val oldFile = File(logDir, "runtime.2000-01-01.jsonl").apply {
            writeText("""{"timestamp":946684800000,"message":"old"}""")
        }

        LogUtils.setRetentionDays(1)

        assertFalse(oldFile.exists())
    }

    @Test
    fun `export redacts token values in bundled logs`() {
        val currentDate = LogUtils.currentDateString(Date())
        File(LogBundleExporter.getLogDir(context), "runtime.$currentDate.jsonl")
            .writeText("""{"timestamp":1,"message":"ipc_token=secret token=plain"}""")

        val result = LogBundleExporter.buildLogBundle(context)

        val zip = result.file
        assertNotNull(zip)
        val text = ZipFile(zip).use { archive ->
            archive.entries().asSequence()
                .filter { !it.isDirectory }
                .joinToString("\n") { entry ->
                    archive.getInputStream(entry).bufferedReader().use { it.readText() }
                }
        }
        assertFalse(text.contains("ipc_token=secret"))
        assertFalse(text.contains("token=plain"))
        assertTrue(text.contains("ipc_token=<redacted>"))
        assertTrue(text.contains("token=<redacted>"))
    }

    @Test
    fun `export does not run su commands when root is not granted`() {
        val currentDate = LogUtils.currentDateString(Date())
        File(LogBundleExporter.getLogDir(context), "runtime.$currentDate.jsonl")
            .writeText("""{"timestamp":1,"message":"hello"}""")
        val rootAccess = RecordingRootAccess(granted = false)
        LogBundleExporter.rootCommandAccess = rootAccess

        val result = LogBundleExporter.buildLogBundle(context)

        assertNotNull(result.file)
        assertEquals(emptyList<String>(), rootAccess.commands)
    }

    private class RecordingRootAccess(
        private val granted: Boolean,
    ) : LogBundleExporter.RootCommandAccess {
        val commands = mutableListOf<String>()

        override fun refreshRootAccessIfGranted(): Boolean = granted

        override fun runRootCommand(command: String, timeoutMs: Long): BoundedShellResult {
            commands += command
            return BoundedShellResult(0, stdout = listOf("root-output"))
        }
    }
}
