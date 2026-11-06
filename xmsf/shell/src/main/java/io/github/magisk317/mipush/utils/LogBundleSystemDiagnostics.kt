package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.platform.support.BoundedShellRunner

import io.github.magisk317.mipush.platform.support.BoundedShellResult

import io.github.magisk317.mipush.platform.support.AppRootAccessFacade

import io.github.magisk317.mipush.common.utils.logW
import android.content.Context
import android.app.ActivityManager
import android.os.Build
import java.io.File
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.app.MemoryLimitDiagnostics
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.xposed.diagnostics.DiagnosticArchive
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import io.github.magisk317.xposed.diagnostics.DiagnosticShell

interface RootCommandAccess {
    fun refreshRootAccessIfGranted(): Boolean

    fun runRootCommand(
        command: String,
        timeoutMs: Long = BoundedShellRunner.DEFAULT_TIMEOUT_MS,
    ): BoundedShellResult
}

private object DefaultRootCommandAccess : RootCommandAccess {
    override fun refreshRootAccessIfGranted(): Boolean = AppRootAccessFacade.refreshRootAccessIfGranted()

    override fun runRootCommand(command: String, timeoutMs: Long): BoundedShellResult {
        return AppRootAccessFacade.runRootCommand(command, timeoutMs = timeoutMs)
    }
}

internal object LogBundleSystemDiagnostics {
    private const val XMSF_KEEPER_PACKAGE = "com.xiaomi.xmsfkeeper"
    private const val XMSF_KEEPALIVE_DIAGNOSTICS_FILE = "xmsf_keepalive.txt"
    private const val ZYGISK_DIAGNOSTICS_FILE = "zygisk_diagnostics.txt"
    private const val TARGET_PROCESS_DIAGNOSTICS_FILE = "target_process_diagnostics.txt"
    private const val ZYGISK_CONFIG_PATH = "/data/adb/mipush_zygisk/app.conf"
    private const val APPLICATION_EXIT_HISTORY_FILE = "application_exit_history.txt"
    private const val ROOT_DIAGNOSTICS_TIMEOUT_MS = 6_000L
    private const val TARGET_PROCESS_DIAGNOSTICS_TIMEOUT_MS = 12_000L
    private val SYSTEMUI_HOOK_LOG_DIRS = listOf(
        "/data/user/0/com.android.systemui/files/mipush-hook",
        "/data/user_de/0/com.android.systemui/files/mipush-hook",
        "/data/system_ce/0/com.android.systemui/files/mipush-hook",
    )
    var rootCommandAccess: RootCommandAccess = DefaultRootCommandAccess

    fun resetRootCommandAccessForTest() {
        rootCommandAccess = DefaultRootCommandAccess
    }
    internal fun captureApplicationExitHistory(
        context: Context,
        stagingDir: File,
        details: MutableList<String>,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            details += "application exit history skipped: api<30"
            return
        }
        val output = File(stagingDir, "system/$APPLICATION_EXIT_HISTORY_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "application exit history failed: output dir unavailable"
            return
        }
        val manager = context.getSystemService(ActivityManager::class.java)
        if (manager == null) {
            details += "application exit history skipped: activity manager unavailable"
            return
        }
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 12, 0)
        }.getOrElse { error ->
            details += "application exit history failed: ${error.javaClass.simpleName}"
            return
        }
        output.writeText(buildString {
            appendLine("# ApplicationExitInfo history for ${context.packageName}")
            appendLine("# Memory-related rows are marked for Android 17 limiter diagnosis.")
            appendLine("count=${exits.size}")
            exits.forEachIndexed { index, info ->
                val memoryRelated = MemoryLimitDiagnostics.isMemoryRelatedExit(info)
                appendLine()
                appendLine("[$index]")
                appendLine("memoryRelated=$memoryRelated")
                appendLine("reason=${info.reason}")
                appendLine("status=${info.status}")
                appendLine("timestamp=${info.timestamp}")
                appendLine("pssKb=${info.pss}")
                appendLine("rssKb=${info.rss}")
                appendLine("description=${info.description.orEmpty()}")
            }
        })
        details += "application exit history: system/$APPLICATION_EXIT_HISTORY_FILE entries=${exits.size}"
    }

    private data class KeepaliveDiagnosticCommand(
        val title: String,
        val command: String,
    )

    private fun keepaliveDiagnosticCommands(): List<KeepaliveDiagnosticCommand> {
        val packageFields =
            """Package \[|codePath=|resourcePath=|legacyNativeLibraryDir=|versionCode=|versionName=|""" +
                "pkgFlags=|privateFlags=|userId=|firstInstallTime=|lastUpdateTime=|enabled="
        val serviceFields =
            "ServiceRecord|packageName=|processName=|app=|recentCallingPackage=|recentCallingUid=|" +
                "infoAllowStartForeground|Bindings:|Client AppBindRecord|ConnectionRecord|" +
                """caller=|callerPackage|com\.xiaomi\.xmsfkeeper"""
        val logcatFields =
            """XMSFKeeper|xmsfkeeper|caller=com\.xiaomi\.xmsfkeeper|XMPushService"""
        return listOf(
            KeepaliveDiagnosticCommand(
                title = "xmsf package path",
                command = packagePathDiagnosticCommand(Constants.SERVICE_APP_NAME),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsf package flags",
                command = packageFlagsDiagnosticCommand(Constants.SERVICE_APP_NAME, packageFields),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsfkeeper package path",
                command = packagePathDiagnosticCommand(XMSF_KEEPER_PACKAGE),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsfkeeper package flags",
                command = packageFlagsDiagnosticCommand(XMSF_KEEPER_PACKAGE, packageFields),
            ),
            KeepaliveDiagnosticCommand(
                title = "xmsfkeeper process",
                command = "pidof $XMSF_KEEPER_PACKAGE || true; " +
                    "(ps -A || ps) | grep -F ${shQuote(XMSF_KEEPER_PACKAGE)} | grep -v grep || " +
                    "echo ${shQuote("process missing: $XMSF_KEEPER_PACKAGE")}",
            ),
            KeepaliveDiagnosticCommand(
                title = "XMPushService binding",
                command = "dumpsys activity services ${Constants.SERVICE_APP_NAME}/${Constants.XM_PUSH_SERVICE_CLASS} | " +
                    "grep -E ${shQuote(serviceFields)} || " +
                    "echo ${shQuote("binding fields missing: ${Constants.SERVICE_APP_NAME}/${Constants.XM_PUSH_SERVICE_CLASS}")}",
            ),
            KeepaliveDiagnosticCommand(
                title = "recent xmsfkeeper logcat",
                command = "logcat -d -v threadtime -b all | grep -E ${shQuote(logcatFields)} | tail -n 200 || " +
                    "echo ${shQuote("recent xmsfkeeper logcat entries missing")}",
            ),
        )
    }

    private fun packagePathDiagnosticCommand(packageName: String): String =
        "pm path $packageName || echo ${shQuote("package missing: $packageName")}"

    private fun packageFlagsDiagnosticCommand(packageName: String, grepPattern: String): String =
        "dumpsys package $packageName | grep -E ${shQuote(grepPattern)} || " +
            "echo ${shQuote("package fields missing: $packageName")}"

    private fun dumpRootCommandOutput(command: String, output: File): Boolean {
        return runCatching {
            val parent = output.parentFile ?: return false
            if (!ensureDirectory(parent, recreateWhenFile = true)) return false
            val result = rootCommandAccess.runRootCommand(command, timeoutMs = 6_000L)
            if (!result.isSuccess) return false
            output.writeText(result.stdoutText)
            output.exists() && output.length() > 0
        }.getOrDefault(false)
    }
    private fun ensureDirectory(dir: File, recreateWhenFile: Boolean): Boolean {
        return DiagnosticArchive.ensureDirectory(dir, recreateWhenFile, onWarning = { logW(it) })
    }
    internal fun captureLogcat(
        stagingDir: File,
        details: MutableList<String>,
        mode: DiagnosticExportMode,
    ) {
        DiagnosticShell.captureLogcat(
            stagingDir = stagingDir,
            details = details,
            mode = mode,
            suFallback = { output ->
                rootCommandAccess.refreshRootAccessIfGranted() &&
                    dumpRootCommandOutput("logcat -d -v threadtime -b all", output)
            },
            onWarning = { logW(it) },
        )
    }

    internal fun copySystemUiHookLogs(stagingDir: File, details: MutableList<String>) {
        val target = File(stagingDir, "app/log")
        if (!ensureDirectory(target, recreateWhenFile = true)) {
            details += "systemui hook log failed: output dir unavailable"
            return
        }
        var copied = false
        SYSTEMUI_HOOK_LOG_DIRS.forEach { path ->
            val src = File(path)
            if (src.exists() && src.canRead()) {
                src.listFiles()?.filter { it.isFile && it.name.endsWith(".jsonl") }?.forEach { file ->
                    file.copyTo(File(target, file.name), overwrite = true)
                    copied = true
                }
            }
        }
        if (copied) {
            details += "systemui hook log: direct"
            return
        }
        val targetPath = target.absolutePath
        val targetUid = runCatching { android.os.Process.myUid() }.getOrDefault(-1)
        val shellCmd = buildString {
            append("mkdir -p ${shQuote(targetPath)}; ")
            SYSTEMUI_HOOK_LOG_DIRS.forEach { path ->
                append("if [ -d ${shQuote(path)} ]; then ")
                append("cp -f ${shQuote(path)}/*.jsonl ${shQuote("$targetPath/")} 2>/dev/null; ")
                if (targetUid > 0) {
                    append("chown $targetUid:$targetUid ${shQuote("$targetPath/")}*.jsonl 2>/dev/null; ")
                }
                append("chmod a+r ${shQuote("$targetPath/")}*.jsonl 2>/dev/null; fi; ")
            }
        }
        val rootReady = rootCommandAccess.refreshRootAccessIfGranted()
        val copiedViaSu = rootReady && runCatching {
            rootCommandAccess.runRootCommand(shellCmd, timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS).isSuccess
        }.getOrDefault(false) &&
            target.listFiles()?.any { it.name.contains("runtime.hook.systemui") } == true
        if (copiedViaSu) {
            details += "systemui hook log: su"
            return
        }
        details += "systemui hook log missing"
    }
    internal fun captureZygiskDiagnostics(stagingDir: File, details: MutableList<String>) {
        val output = File(stagingDir, "system/$ZYGISK_DIAGNOSTICS_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "zygisk diagnostics failed: output dir unavailable"
            return
        }
        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            output.writeText("status=unavailable\nreason=root_not_granted\n")
            details += "zygisk diagnostics skipped: root not granted"
            return
        }

        val appConfig = rootCommandAccess.runRootCommand(
            "cat ${shQuote(ZYGISK_CONFIG_PATH)}",
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        )
        val deviceConfig = rootCommandAccess.runRootCommand(
            "grep -E ${shQuote("^(#|ro\\.(product|build|miui|hardware|boot\\.hardware|soc)\\.|persist\\.sys\\.(country|oem|miui))")}" +
                " ${shQuote("/data/adb/mipush_zygisk/device.conf")} 2>/dev/null || true",
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        )
        val runtime = rootCommandAccess.runRootCommand(
            """
            for p in /data/adb/modules/mipush_zygisk /data/adb/modules/MiPushZygisk /data/adb/modules/mipushzygisk; do
                if [ -f "${'$'}p/module.prop" ]; then
                    echo "## module.prop:${'$'}p"
                    grep -E '^(id|name|version|versionCode|description|author)=' "${'$'}p/module.prop" || true
                fi
            done
            echo '## mipushcut state'
            cat /data/adb/mipush_zygisk/mipushcut.state 2>/dev/null || true
            echo '## selected properties'
            getprop | grep -E '^(\[mipush\.zygisk\.enabled\]|\[ro\.(product|build|miui|hardware|boot\.hardware|soc)\.|\[persist\.sys\.(country|oem|miui)\.)' || true
            echo '## MiPushZygisk logcat'
            logcat -d -v threadtime -b all -s 'MiPushZygisk:*' | tail -n 2000 || true
            """.trimIndent(),
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        )
        val text = buildString {
            appendLine("schemaVersion=1")
            appendLine("status=available")
            appendLine("configPath=$ZYGISK_CONFIG_PATH")
            appendLine("configReadSuccess=${appConfig.isSuccess}")
            appendLine("configExitCode=${appConfig.exitCode}")
            appendLine("configTimedOut=${appConfig.timedOut}")
            appendLine("configEntryCount=${safeZygiskPackages(appConfig.stdoutText).size}")
            appendLine("[app.conf]")
            appendLine(appConfig.stdoutText.ifBlank { "<empty>" })
            appendLine("[device.conf platform-only]")
            appendLine(deviceConfig.stdoutText.ifBlank { "<empty>" })
            appendLine("[module/property/logcat]")
            appendLine(runtime.stdoutText.ifBlank { "<empty>" })
            if (runtime.stderrText.isNotBlank()) {
                appendLine("[stderr]")
                appendLine(runtime.stderrText)
            }
        }
        output.writeText(text)
        details += "zygisk diagnostics: system/$ZYGISK_DIAGNOSTICS_FILE"
    }

    /**
     * Per-target-app exit reasons and crash lines. `getHistoricalProcessExitReasons` only accepts
     * the calling package, so the previous bundle could never show why a spoofed app died or was
     * killed; the root `dumpsys activity exit-info` call covers every registered package plus the
     * apps in the spoof list.
     */
    internal fun captureTargetProcessDiagnostics(
        context: Context,
        stagingDir: File,
        details: MutableList<String>,
    ) {
        val output = File(stagingDir, "system/$TARGET_PROCESS_DIAGNOSTICS_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "target process diagnostics failed: output dir unavailable"
            return
        }
        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            output.writeText("status=unavailable\nreason=root_not_granted\n")
            details += "target process diagnostics skipped: root not granted"
            return
        }

        val configuredPackages = rootCommandAccess.runRootCommand(
            "cat ${shQuote(ZYGISK_CONFIG_PATH)}",
            timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS,
        ).stdoutText
        val registeredPackages = runCatching {
            RegisteredApplicationDb.getList(null).map { it.packageName }
        }.getOrDefault(emptyList())
        val targets = (listOf(context.packageName, Constants.SERVICE_APP_NAME) +
            safeZygiskPackages(configuredPackages) + registeredPackages)
            .map { it.substringBefore('|').removePrefix("-").trim() }
            .filter(::isSafePackageName)
            .distinct()
            .take(64)
        if (targets.isEmpty()) {
            output.writeText("status=unavailable\nreason=no_target_packages\n")
            details += "target process diagnostics skipped: no target packages"
            return
        }

        val packagePattern = targets.joinToString("|") { it.replace(".", "\\.") }
        val command = buildString {
            targets.forEach { packageName ->
                append("echo ").append(shQuote("## exit-info:$packageName")).append("; ")
                append("dumpsys activity exit-info ").append(shQuote(packageName))
                    .append(" 2>&1 | tail -n 120; ")
            }
            append("echo ").append(shQuote("## crash-logcat")).append("; ")
            append("logcat -d -v threadtime -b crash | grep -E ")
                .append(shQuote(packagePattern)).append(" | tail -n 1500 || true; ")
            append("echo ").append(shQuote("## activity-logcat")).append("; ")
            append("logcat -d -v threadtime -b all | grep -E ")
                .append(shQuote(packagePattern)).append(" | grep -E ")
                .append(shQuote("FATAL EXCEPTION|AndroidRuntime|ANR|am_crash|Process .* has died"))
                .append(" | tail -n 1500 || true")
        }
        val result = rootCommandAccess.runRootCommand(
            command,
            timeoutMs = TARGET_PROCESS_DIAGNOSTICS_TIMEOUT_MS,
        )
        val text = buildString {
            appendLine("schemaVersion=1")
            appendLine("status=${if (result.isSuccess) "available" else "partial"}")
            appendLine("targetCount=${targets.size}")
            appendLine("targets=${targets.joinToString(",")}")
            appendLine("exitCode=${result.exitCode}")
            appendLine("timedOut=${result.timedOut}")
            appendLine("skipped=${result.skipped}")
            appendLine("[stdout]")
            appendLine(result.stdoutText.ifBlank { "<empty>" })
            appendLine("[stderr]")
            appendLine(result.stderrText.ifBlank { "<empty>" })
        }
        output.writeText(text)
        details += "target process diagnostics: system/$TARGET_PROCESS_DIAGNOSTICS_FILE targets=${targets.size}"
    }

    /** Package names from a Zygisk app.conf, so diagnostics can cover the spoofed apps. */
    private fun safeZygiskPackages(content: String): List<String> = content.lineSequence()
        .map { it.trim() }
        .filter {
            it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("profile=") &&
                !it.startsWith("observe=") && !it.startsWith("spoof=")
        }
        .map { it.removePrefix("-") }
        .map { it.substringBefore('|') }
        .filter(::isSafePackageName)
        .distinct()
        .toList()

    private fun isSafePackageName(value: String): Boolean =
        Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(value)

    internal fun captureXmsfKeepaliveDiagnostics(stagingDir: File, details: MutableList<String>) {
        runCatching { com.topjohnwu.superuser.Shell.getShell() }.onFailure { }
        if (!rootCommandAccess.refreshRootAccessIfGranted()) {
            details += "xmsf keepalive diagnostics skipped: root not granted"
            return
        }

        val output = File(stagingDir, "system/$XMSF_KEEPALIVE_DIAGNOSTICS_FILE")
        val parent = output.parentFile
        if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
            details += "xmsf keepalive diagnostics failed: output dir unavailable"
            return
        }

        val sections = keepaliveDiagnosticCommands().map { section ->
            section to rootCommandAccess.runRootCommand(section.command, timeoutMs = ROOT_DIAGNOSTICS_TIMEOUT_MS)
        }
        val text = buildString {
            appendLine("# XMSF keepalive diagnostics")
            appendLine("# Captures Xiaomi system keeper package, process, and XMPushService binding state.")
            appendLine()
            sections.forEach { (section, result) ->
                appendLine("## ${section.title}")
                appendLine("$ ${section.command}")
                appendLine("exitCode=${result.exitCode} timedOut=${result.timedOut} skipped=${result.skipped}")
                val stdout = result.stdoutText.ifBlank { "<empty>" }
                appendLine("[stdout]")
                appendLine(stdout)
                val stderr = result.stderrText.ifBlank { "<empty>" }
                appendLine("[stderr]")
                appendLine(stderr)
                appendLine()
            }
        }
        output.writeText(text)
        details += "xmsf keepalive diagnostics: system/$XMSF_KEEPALIVE_DIAGNOSTICS_FILE"
    }

    private fun shQuote(value: String): String = DiagnosticShell.shQuote(value)
}
