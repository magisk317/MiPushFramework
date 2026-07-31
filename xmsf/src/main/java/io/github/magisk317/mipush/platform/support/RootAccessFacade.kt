package io.github.magisk317.mipush.platform.support

import android.os.Process
import com.topjohnwu.superuser.Shell
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.utils.PrivilegeElevator
import java.util.concurrent.atomic.AtomicReference

class RootAccessFacade(
    private val runner: BoundedShellRunner,
    private val rootGrantState: () -> Boolean?,
    private val requestRootGrant: () -> Unit,
) {
    private val rootAccessCache = AtomicReference<Boolean?>(null)

    // `id -u` root 探测是一次 su 进程往返；记录列表逐行触发时会重复探测同一稳定状态。
    // root 授权在一个进程周期内极少变化，这里对探测结果做 TTL 节流，避免每行一次 su 往返。
    private val lastProbeAt = java.util.concurrent.atomic.AtomicLong(0L)

    fun hasCachedRootAccess(): Boolean = rootAccessCache.get() == true

    fun refreshRootAccessIfGranted(): Boolean {
        val granted = rootGrantState()
        if (granted == false) {
            rootAccessCache.set(false)
            return false
        }
        if (granted != true && rootAccessCache.get() != true) {
            return false
        }
        // TTL 内直接复用上次探测结果，跳过 `id -u` 往返。
        val cached = rootAccessCache.get()
        if (cached != null && System.currentTimeMillis() - lastProbeAt.get() < PROBE_TTL_MS) {
            return cached
        }
        return probeRootAccess(source = "refresh")
    }

    fun requestRootAccess(): Boolean {
        runCatching { requestRootGrant() }
            .onFailure { this.logW("root_request role=xmsf error=${it.javaClass.simpleName}") }
        return probeRootAccess(source = "request")
    }

    fun runRootCommand(
        command: String,
        timeoutMs: Long = BoundedShellRunner.DEFAULT_TIMEOUT_MS,
        requestAuthorization: Boolean = false,
    ): BoundedShellResult {
        val hasRoot = if (requestAuthorization) {
            requestRootAccess()
        } else {
            refreshRootAccessIfGranted()
        }
        if (!hasRoot) {
            return BoundedShellResult.skipped("root not granted")
        }
        val result = runner.run(command, ShellCommandMode.ROOT, timeoutMs)
        if (!result.isSuccess && rootGrantState() == false) {
            rootAccessCache.set(false)
        }
        return result
    }

    fun runShellCommand(
        command: String,
        timeoutMs: Long = BoundedShellRunner.DEFAULT_TIMEOUT_MS,
    ): BoundedShellResult = runner.run(command, ShellCommandMode.USER, timeoutMs)

    private fun probeRootAccess(source: String): Boolean {
        val result = runner.run("id -u", ShellCommandMode.ROOT, timeoutMs = 3_000L)
        val available = result.isSuccess && result.stdout.firstOrNull()?.trim() == "0"
        rootAccessCache.set(available)
        lastProbeAt.set(System.currentTimeMillis())
        val uid = runCatching { Process.myUid() }.getOrDefault(UNKNOWN_UID)
        val userId = if (uid >= 0) uid / PER_USER_RANGE else UNKNOWN_USER_ID
        val stderr = result.stderrText
            .replace('\n', ' ')
            .take(MAX_LOGGED_STDERR_LENGTH)
            .ifBlank { "none" }
        this.logI(
            "root_probe role=xmsf source=$source userId=$userId uid=$uid " +
                "grantState=${rootGrantState()} available=$available exitCode=${result.exitCode} " +
                "timeout=${result.timedOut} skipped=${result.skipped} stderr=$stderr",
        )
        return available
    }

    companion object {
        private const val PROBE_TTL_MS = 10_000L
        private const val PER_USER_RANGE = 100_000
        private const val MAX_LOGGED_STDERR_LENGTH = 160
        private const val UNKNOWN_UID = -1
        private const val UNKNOWN_USER_ID = -1
    }
}

object AppRootAccessFacade {
    private val delegate = RootAccessFacade(
        runner = DefaultBoundedShellRunner,
        rootGrantState = { Shell.isAppGrantedRoot() },
        requestRootGrant = { PrivilegeElevator.tryToElevate() },
    )

    fun hasCachedRootAccess(): Boolean = delegate.hasCachedRootAccess()

    fun refreshRootAccessIfGranted(): Boolean = delegate.refreshRootAccessIfGranted()

    fun requestRootAccess(): Boolean = delegate.requestRootAccess()

    fun runRootCommand(
        command: String,
        timeoutMs: Long = BoundedShellRunner.DEFAULT_TIMEOUT_MS,
        requestAuthorization: Boolean = false,
    ): BoundedShellResult = delegate.runRootCommand(command, timeoutMs, requestAuthorization)

    fun runShellCommand(
        command: String,
        timeoutMs: Long = BoundedShellRunner.DEFAULT_TIMEOUT_MS,
    ): BoundedShellResult = delegate.runShellCommand(command, timeoutMs)
}
