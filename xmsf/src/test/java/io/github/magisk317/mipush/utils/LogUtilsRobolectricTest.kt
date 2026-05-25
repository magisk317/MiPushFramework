package io.github.magisk317.mipush.utils

import android.content.Context
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
@Config(sdk = [28])
class LogUtilsRobolectricTest {
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LogUtils.resetForTest()
        LogBundleExporter.clearLogFolders(context)
        LogUtils.init(context)
        LogUtils.setRetentionDays(context, 7)
    }

    @AfterEach
    fun tearDown() {
        LogUtils.resetForTest()
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
        assertTrue(content.text.contains(""""time":"""))
        assertFalse(content.text.contains(""""timestamp":"""))
        assertFalse(content.text.contains(""""source":"""))
        assertFalse(content.text.contains(""""uid":"""))
        assertFalse(content.text.contains(""""threadId":"""))
        assertFalse(content.text.contains(""""throwable":"""))
    }

    @Test
    fun `module logs use route jsonl file`() {
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
        assertTrue(content!!.text.contains(""""route":"sms_hook""""))
        assertTrue(content.text.contains("module hello"))
    }

    @Test
    fun `app logs do not write redundant app route file`() {
        Napier.i("route trim check", tag = "DiagRoute")

        val summary = LogUtils.summarizeFiles(context)
        assertTrue(summary.files.any { it.name.matches(Regex("""runtime\.\d{4}-\d{2}-\d{2}\.jsonl""")) })
        assertFalse(summary.files.any { it.name.matches(Regex("""runtime\.app\.\d{4}-\d{2}-\d{2}\.jsonl""")) })
    }

    @Test
    fun `summary reads new time-only jsonl entries`() {
        val logDir = LogBundleExporter.getLogDir(context)
        val currentDate = LogUtils.currentDateString(Date())
        val file = File(logDir, "runtime.$currentDate.jsonl").apply {
            writeText("""{"time":"$currentDate 23:38:47.000","level":"I","tag":"Diag","message":"new"}""")
        }

        val summary = LogUtils.summarizeFiles(context)
        val info = summary.files.firstOrNull { it.name == file.name }

        assertNotNull(info)
        assertEquals(info!!.firstTimestamp, info.lastTimestamp)
        assertTrue(info.firstTimestamp ?: 0L > 0L)
    }

    @Test
    fun `runtime log file can be deleted by name`() {
        Napier.i("delete me", tag = "DiagDelete")
        val file = LogUtils.summarizeFiles(context).files.first { it.name.startsWith("runtime.") }

        assertTrue(LogUtils.deleteRuntimeLogFile(context, file.name))

        assertFalse(LogUtils.summarizeFiles(context).files.any { it.name == file.name })
        assertFalse(LogUtils.deleteRuntimeLogFile(context, "../${file.name}"))
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

        LogUtils.setRetentionDays(context, 1)

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
    fun `export skips redundant app route logs`() {
        val currentDate = LogUtils.currentDateString(Date())
        val logDir = LogBundleExporter.getLogDir(context)
        File(logDir, "runtime.$currentDate.jsonl")
            .writeText("""{"time":"2026-05-12 10:00:00.000","route":"app","message":"aggregate"}""")
        File(logDir, "runtime.app.$currentDate.jsonl")
            .writeText("""{"time":"2026-05-12 10:00:00.000","route":"app","message":"duplicate"}""")
        File(logDir, "runtime.sms_hook.$currentDate.jsonl")
            .writeText("""{"time":"2026-05-12 10:00:00.000","route":"sms_hook","message":"module"}""")

        val result = LogBundleExporter.buildLogBundle(context)

        val zip = result.file
        assertNotNull(zip)
        val names = ZipFile(zip).use { archive ->
            archive.entries().asSequence().map { it.name }.toList()
        }
        assertTrue(names.any { it == "app/log/runtime.$currentDate.jsonl" })
        assertTrue(names.any { it == "app/log/runtime.sms_hook.$currentDate.jsonl" })
        assertFalse(names.any { it == "app/log/runtime.app.$currentDate.jsonl" })
    }

    @Test
    fun `lsposed rotating logs keep latest two per kind`() {
        val root = File(context.cacheDir, "lsposed_trim_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val logDir = File(root, "log").apply { mkdirs() }
        val verboseOld = File(logDir, "verbose_2026-05-12T15:08:20.327032.log").apply { writeText("v1") }
        val verboseMid = File(logDir, "verbose_2026-05-12T15:08:24.703911.log").apply { writeText("v2") }
        val verboseNew = File(logDir, "verbose_2026-05-12T15:08:36.052652.log").apply { writeText("v3") }
        val modulesOld = File(logDir, "modules_2026-05-12T12:30:03.140636.log").apply { writeText("m1") }
        val modulesMid = File(logDir, "modules_2026-05-12T13:37:13.540595.log").apply { writeText("m2") }
        val modulesNew = File(logDir, "modules_2026-05-12T15:03:24.198501.log").apply { writeText("m3") }
        val props = File(logDir, "props.txt").apply { writeText("props") }
        val kmsg = File(logDir, "kmsg.log").apply { writeText("kmsg") }

        val deleted = LogBundleExporter.trimLsposedRotatingLogs(root)

        assertEquals(2, deleted)
        assertFalse(verboseOld.exists())
        assertTrue(verboseMid.exists())
        assertTrue(verboseNew.exists())
        assertFalse(modulesOld.exists())
        assertTrue(modulesMid.exists())
        assertTrue(modulesNew.exists())
        assertTrue(props.exists())
        assertTrue(kmsg.exists())
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
