package io.github.magisk317.mipush.utils

import android.app.Application
import android.content.Context
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.xposed.logging.LogSanitizerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.io.File
import java.util.Date
import java.util.zip.ZipFile

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
@Execution(ExecutionMode.SAME_THREAD)
class LogUtilsRobolectricTest {
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LogSanitizerConfig.setEnabled(true)
        LogUtils.resetForTest()
        LogSanitizerConfig.setEnabled(true)
        LogBundleExporter.clearLogFolders(context)
        LogUtils.init(context)
        LogUtils.setRetentionDays(context, 7)
    }

    @AfterEach
    fun tearDown() {
        LogUtils.resetForTest()
        LogSanitizerConfig.setEnabled(false)
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
        assertFalse(content.text.contains("token=secret"))
        assertTrue(content.text.contains(""""message":"hello token=***""""))
        assertTrue(content.text.contains(""""time":"""))
        assertFalse(content.text.contains(""""timestamp":"""))
        assertFalse(content.text.contains(""""source":"""))
        assertFalse(content.text.contains(""""uid":"""))
        assertFalse(content.text.contains(""""threadId":"""))
        assertFalse(content.text.contains(""""throwable":"""))
    }

    @Test
    fun `sensitive debug mode explicitly permits plaintext local logs`() {
        LogSanitizerConfig.setEnabled(false)

        Napier.i("token=debug-secret", tag = "DiagSensitive")

        val aggregate = LogUtils.summarizeFiles(context).files
            .first { it.name.matches(Regex("""runtime\.\d{4}-\d{2}-\d{2}\.jsonl""")) }
        val content = LogUtils.readLogFile(context, aggregate.name)
        assertTrue(content!!.text.contains("token=debug-secret"))
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
    fun `retention deletes expired export zip crash staging and legacy artifacts`() {
        val now = Date()
        val logDir = LogBundleExporter.getLogDir(context)
        val crashDir = LogBundleExporter.getCrashDir(context)
        val exportDir = LogBundleExporter.getPrivateExportDir(context)
        val legacyDir = LogBundleExporter.getLegacyCacheLogDir(context)
        logDir.mkdirs()
        crashDir.mkdirs()
        exportDir.mkdirs()
        legacyDir.mkdirs()

        val keepRuntime = File(logDir, "runtime.${LogUtils.currentDateString(now)}.jsonl").apply {
            writeText("""{"time":"keep"}\n""")
        }
        val dropRuntime = File(logDir, "runtime.2020-01-01.jsonl").apply {
            writeText("""{"time":"drop"}\n""")
        }
        val keepCrash = File(crashDir, "Crash_${LogUtils.currentDateString(now)}.txt").apply {
            writeText("keep-crash")
        }
        val dropCrash = File(crashDir, "Crash_2020-01-02.txt").apply {
            writeText("drop-crash")
        }
        val keepZip = File(exportDir, "mipush_logs_${LogUtils.currentDateString(now)}_12-00-00.zip").apply {
            writeText("keep-zip")
        }
        val dropZip = File(exportDir, "mipush_logs_2020-06-25_12-02-11.zip").apply {
            writeText("drop-zip")
        }
        val dropStaging = File(exportDir, ".tmp_mipush_logs_2020-07-02_11-30-53").apply {
            mkdirs()
            File(this, "partial.jsonl").writeText("staging")
        }
        val dropLegacy = File(legacyDir, "old.txt").apply {
            writeText("legacy")
            setLastModified(1_577_836_800_000L) // 2020-01-01 UTC-ish
        }

        LogUtils.setRetentionDays(context, 2)

        assertTrue(keepRuntime.exists())
        assertFalse(dropRuntime.exists())
        assertTrue(keepCrash.exists())
        assertFalse(dropCrash.exists())
        assertTrue(keepZip.exists())
        assertFalse(dropZip.exists())
        assertFalse(dropStaging.exists())
        assertFalse(dropLegacy.exists())
    }

    @Test
    fun `export redacts token values in bundled logs`() {
        val currentDate = LogUtils.currentDateString(Date())
        // Runtime jsonl is trusted as append-time sanitized and is skipped during export sanitize
        // for speed. Verify export still redacts other staged text sources such as crash logs.
        val crashDir = LogBundleExporter.getCrashDir(context).apply { mkdirs() }
        File(crashDir, "Crash_$currentDate.txt").writeText(
            "ipc_token=secret token=plain phone=13800138000 sender=13800138000 code=123456",
        )
        Napier.i("ipc_token=secret token=plain phone=13800138000", tag = "token-test")

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
        assertFalse(text.contains("13800138000"))
        assertFalse(text.contains("code=123456"))
        assertTrue(
            text.contains("token=***") ||
                text.contains("payload[len=") ||
                text.contains("sender[len="),
        )
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

    @Test
    fun `export captures xmsf keepalive diagnostics when root is granted`() {
        val currentDate = LogUtils.currentDateString(Date())
        File(LogBundleExporter.getLogDir(context), "runtime.$currentDate.jsonl")
            .writeText("""{"timestamp":1,"message":"hello"}""")
        val rootAccess = RecordingRootAccess(granted = true) { command ->
            when {
                command.startsWith("pm path com.xiaomi.xmsfkeeper") ->
                    BoundedShellResult(0, stdout = listOf("package:/product/app/XMSFKeeperAll/XMSFKeeperAll.apk"))
                command.startsWith("dumpsys package com.xiaomi.xmsfkeeper") ->
                    BoundedShellResult(0, stdout = listOf("pkgFlags=[ SYSTEM HAS_CODE PERSISTENT ALLOW_CLEAR_USER_DATA ]"))
                command.startsWith("pidof com.xiaomi.xmsfkeeper") ->
                    BoundedShellResult(0, stdout = listOf("1234", "u0_a195 1234 com.xiaomi.xmsfkeeper"))
                command.startsWith("dumpsys activity services com.xiaomi.xmsf/com.xiaomi.push.service.XMPushService") ->
                    BoundedShellResult(
                        0,
                        stdout = listOf(
                            "ConnectionRecord{... com.xiaomi.xmsfkeeper ...}",
                            "infoAllowStartForeground=[callingPackage: com.xiaomi.xmsfkeeper; code:PROC_STATE_PERSISTENT]",
                        ),
                    )
                else -> BoundedShellResult(0, stdout = listOf("root-output"))
            }
        }
        LogBundleExporter.rootCommandAccess = rootAccess

        val result = LogBundleExporter.buildLogBundle(context)

        val zip = result.file
        assertNotNull(zip)
        val diagnosticText = ZipFile(zip).use { archive ->
            val entry = archive.getEntry("system/xmsf_keepalive.txt")
            assertNotNull(entry)
            archive.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        assertTrue(result.details.contains("xmsf keepalive diagnostics"))
        assertTrue(rootAccess.commands.any { it.contains("com.xiaomi.xmsfkeeper") })
        assertTrue(rootAccess.commands.any { it.contains("dumpsys activity services") })
        assertTrue(diagnosticText.contains("xmsfkeeper package flags"))
        assertTrue(diagnosticText.contains("PERSISTENT"))
        assertTrue(diagnosticText.contains("XMPushService binding"))
        assertTrue(diagnosticText.contains("com.xiaomi.xmsfkeeper"))
    }

    private class RecordingRootAccess(
        private val granted: Boolean,
        private val responder: (String) -> BoundedShellResult = {
            BoundedShellResult(0, stdout = listOf("root-output"))
        },
    ) : LogBundleExporter.RootCommandAccess {
        val commands = mutableListOf<String>()

        override fun refreshRootAccessIfGranted(): Boolean = granted

        override fun runRootCommand(command: String, timeoutMs: Long): BoundedShellResult {
            commands += command
            return responder(command)
        }
    }
}
