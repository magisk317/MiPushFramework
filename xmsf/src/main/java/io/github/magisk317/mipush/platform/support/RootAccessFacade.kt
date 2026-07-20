package io.github.magisk317.mipush.platform.support

import com.topjohnwu.superuser.Shell
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
        return probeRootAccess()
    }

    fun requestRootAccess(): Boolean {
        runCatching { requestRootGrant() }
        return probeRootAccess()
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

    private fun probeRootAccess(): Boolean {
        val result = runner.run("id -u", ShellCommandMode.ROOT, timeoutMs = 3_000L)
        val available = result.isSuccess && result.stdout.firstOrNull()?.trim() == "0"
        rootAccessCache.set(available)
        lastProbeAt.set(System.currentTimeMillis())
        return available
    }

    companion object {
        private const val PROBE_TTL_MS = 10_000L
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
