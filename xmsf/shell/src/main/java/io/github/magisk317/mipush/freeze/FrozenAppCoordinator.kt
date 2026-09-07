package io.github.magisk317.mipush.freeze

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PROCESS_NAME
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_SCREEN_OFF
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_TIMED
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_TASK_REMOVED
import io.github.magisk317.mipush.common.ACTION_FREEZE_LAUNCH_ACTIVATED
import io.github.magisk317.mipush.common.EXTRA_FREEZE_PACKAGE
import io.github.magisk317.mipush.common.EXTRA_FREEZE_USER_ID
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.data.FreezeSettingsSnapshot
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** Packages the coordinator must never freeze or refreeze, mirroring the README freeze guidance. */
internal object FrozenPackageGuard {
    private const val MANAGER_PACKAGE_NAME = "io.github.magisk317.mipush"
    private const val ANDROID_PACKAGE_NAME = "android"

    fun isRefreezeForbidden(packageName: String): Boolean =
        packageName == XMSF_PACKAGE_NAME ||
            packageName == MANAGER_PACKAGE_NAME ||
            packageName == ANDROID_PACKAGE_NAME
}

/**
 * Coordinates unfreeze-on-launch and policy-driven refreeze for apps the user froze with
 * `pm disable-user`. Frozen apps still receive notifications through the standard publish
 * chain; clicking one lands in the XMSF service click route, where [handleLaunchActivation]
 * re-enables the package and records it for refreeze.
 *
 * Refreeze policies: never, on screen off (pending set persists across process death), or
 * after a configurable delay. Only packages unfrozen by this coordinator are ever refrozen;
 * transient skips (foreground use, recent launch, command failure) are retried on the next
 * screen-off pass or one-minute timed retry instead of being dropped.
 */
class FrozenAppCoordinator(
    private val appContext: Context,
    private val preferenceRepository: PreferenceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val pendingMutex = Mutex()
    private val timedMutex = Mutex()
    private val timedJobs = mutableMapOf<String, Job>()

    /** Packages launched through this coordinator; refreeze is skipped within [RECENT_LAUNCH_WINDOW_MS]. */
    private val recentLaunches = mutableMapOf<String, Long>()
    private val recentLaunchLock = Mutex()
    private var lastScreenOffRefreezeAt = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            scope.launch {
                val now = System.currentTimeMillis()
                if (now - lastScreenOffRefreezeAt < SCREEN_OFF_THROTTLE_MS) {
                    logD { "freeze screen-off throttled" }
                    return@launch
                }
                lastScreenOffRefreezeAt = now
                refreezePending("refreeze_screen_off")
            }
        }
    }

    /** Registers the screen-off listener once, in the XMSF main process only. */
    fun ensureStarted() {
        if (!isMainXmsfProcess() || !started.compareAndSet(false, true)) return
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure {
            logW("freeze screen-off receiver registration failed", it)
        }
        logI("freeze coordinator started")
    }

    /**
     * Activation hook from PushMessageProcessor.activeApp. Re-enables a frozen target and
     * records it for refreeze; keeps the legacy unconditional enable when the feature is off
     * or the settings snapshot cannot be read. Suspending: callers must block the click route
     * until this returns so the subsequent pull-up sees the package enabled and ready.
     */
    suspend fun handleLaunchActivation(targetPackage: String) {
        if (targetPackage.isBlank()) return
        val settings = runCatching {
            preferenceRepository.freezeSettingsSnapshot()
        }.getOrNull()
        if (settings == null) {
            logW { "freeze settings snapshot unavailable, unconditional enable pkg=$targetPackage" }
            enablePackage(targetPackage)
            return
        }
        if (!settings.enabled) {
            enablePackage(targetPackage)
            return
        }
        when (componentEnabledState(targetPackage)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> {
                if (enablePackage(targetPackage).isSuccess) {
                    waitForPackageReady(targetPackage)
                    markRecentLaunch(targetPackage)
                    logI("freeze unfroze pkg=$targetPackage policy=${settings.refreezePolicy}")
                    emitFreezeEvent("ok", "unfreeze", targetPackage)
                    scope.launch { recordPendingRefreeze(targetPackage, settings) }
                } else {
                    emitFreezeEvent("error", "unfreeze_failed", targetPackage)
                }
            }
            else -> {
                logD { "freeze skip enable, package already enabled pkg=$targetPackage" }
                emitFreezeEvent("skip", "already_enabled", targetPackage)
            }
        }
    }

    private suspend fun markRecentLaunch(packageName: String) {
        recentLaunchLock.withLock {
            recentLaunches[packageName] = System.currentTimeMillis()
        }
    }

    private suspend fun isRecentLaunch(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val launchedAt = recentLaunchLock.withLock {
            recentLaunches.entries.removeAll { now - it.value > RECENT_LAUNCH_WINDOW_MS }
            recentLaunches[packageName]
        }
        return launchedAt != null && now - launchedAt < RECENT_LAUNCH_WINDOW_MS
    }

    private suspend fun recordPendingRefreeze(
        packageName: String,
        settings: FreezeSettingsSnapshot,
    ) {
        if (FrozenPackageGuard.isRefreezeForbidden(packageName)) {
            emitFreezeEvent("skip", "track_protected", packageName)
            return
        }
        when (settings.refreezePolicy) {
            FREEZE_REFREEZE_POLICY_SCREEN_OFF -> pendingMutex.withLock {
                preferenceRepository.addPendingRefreezePackage(packageName)
            }
            FREEZE_REFREEZE_POLICY_TIMED ->
                scheduleTimedRefreeze(packageName, settings.refreezeDelayMinutes)
            FREEZE_REFREEZE_POLICY_TASK_REMOVED ->
                notifyTaskRemovedTracking(packageName)
            else -> logD { "freeze refreeze disabled by policy pkg=$packageName" }
        }
    }

    private suspend fun scheduleTimedRefreeze(packageName: String, delayMinutes: Int) {
        cancelTimedRefreeze(packageName)
        val minutes = delayMinutes.coerceAtLeast(1)
        timedMutex.withLock {
            timedJobs[packageName] = scope.launch {
                delay(minutes * MILLIS_PER_MINUTE)
                timedMutex.withLock { timedJobs.remove(packageName) }
                // Transient skips (foreground use, recent launch) retry after a short delay
                // so a timed refreeze is never silently dropped.
                if (!refreeze(packageName, "refreeze_timed")) {
                    scheduleTimedRefreeze(packageName, TIMED_RETRY_MINUTES)
                }
            }
        }
        logI("freeze scheduled timed refreeze pkg=$packageName minutes=$minutes")
    }

    private suspend fun cancelTimedRefreeze(packageName: String) {
        timedMutex.withLock {
            timedJobs.remove(packageName)?.cancel()
        }
    }

    private suspend fun refreezePending(reason: String) {
        val targets = pendingMutex.withLock {
            val current = preferenceRepository.pendingRefreezePackages()
            if (current.isNotEmpty()) preferenceRepository.clearPendingRefreezePackages()
            current
        }
        for (packageName in targets) {
            // Re-queue transient skips so the next screen-off pass retries them.
            if (!refreeze(packageName, reason)) {
                pendingMutex.withLock { preferenceRepository.addPendingRefreezePackage(packageName) }
            }
        }
    }

    /**
     * Freezes [packageName] per policy. Returns true when the package is settled (frozen now,
     * already frozen, protected, or uninstalled); false marks a transient skip that callers
     * should retry later.
     */
    private suspend fun refreeze(packageName: String, reason: String): Boolean {
        cancelTimedRefreeze(packageName)
        if (FrozenPackageGuard.isRefreezeForbidden(packageName)) {
            emitFreezeEvent("skip", "refreeze_protected", packageName)
            return true
        }
        if (!isPackageInstalled(packageName)) {
            emitFreezeEvent("skip", "refreeze_not_installed", packageName)
            return true
        }
        if (isPackageFrozen(packageName)) {
            emitFreezeEvent("skip", "refreeze_not_needed", packageName)
            return true
        }
        if (isRecentLaunch(packageName)) {
            logD { "freeze skip refreeze, recent launch pkg=$packageName" }
            emitFreezeEvent("skip", "refreeze_recent_launch", packageName)
            return false
        }
        if (isPackageForeground(packageName)) {
            logD { "freeze skip refreeze, package is in foreground pkg=$packageName" }
            emitFreezeEvent("skip", "refreeze_foreground", packageName)
            return false
        }
        val userId = currentUserId()
        val result = runCatching {
            AppRootAccessFacade.runRootCommand("pm disable-user --user $userId ${shellQuote(packageName)}")
        }.getOrElse {
            logW { "pm disable-user failed pkg=$packageName error=${it.localizedMessage}" }
            emitFreezeEvent("error", "refreeze_failed", packageName)
            return false
        }
        return if (result.isSuccess) {
            // Park the frozen package in stopped state so alarms and other wake paths skip it.
            runCatching {
                AppRootAccessFacade.runRootCommand(
                    "cmd package set-stopped-state --user $userId ${shellQuote(packageName)} true",
                )
            }
            logI("freeze refroze pkg=$packageName reason=$reason")
            emitFreezeEvent("ok", reason, packageName)
            true
        } else {
            logW { "pm disable-user failed pkg=$packageName skipped=${result.skipped}" }
            emitFreezeEvent("error", "refreeze_failed", packageName)
            false
        }
    }

    private fun enablePackage(targetPackage: String, userId: Int = currentUserId()): BoundedShellResult {
        val quoted = shellQuote(targetPackage)
        val result = runCatching {
            AppRootAccessFacade.runRootCommand("pm enable --user $userId $quoted")
        }.getOrElse {
            logW { "pm enable failed pkg=$targetPackage error=${it.localizedMessage}" }
            return BoundedShellResult.skipped(it.javaClass.simpleName)
        }
        if (result.isSuccess) {
            // Clear stopped state so the newly enabled package can receive broadcasts.
            runCatching {
                AppRootAccessFacade.runRootCommand(
                    "cmd package set-stopped-state --user $userId $quoted false",
                )
            }
        }
        return result
    }

    private suspend fun waitForPackageReady(packageName: String, timeoutMs: Long = PACKAGE_READY_TIMEOUT_MS) {
        var waited = 0L
        while (waited < timeoutMs) {
            val state = componentEnabledState(packageName)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            ) {
                return
            }
            delay(PACKAGE_READY_STEP_MS)
            waited += PACKAGE_READY_STEP_MS
        }
        logW { "freeze waitPackagesReady timeout pkg=$packageName after ${waited}ms" }
    }

    private fun isPackageFrozen(packageName: String): Boolean = when (componentEnabledState(packageName)) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        -> true
        else -> false
    }

    private fun componentEnabledState(packageName: String): Int = runCatching {
        appContext.packageManager.getApplicationEnabledSetting(packageName)
    }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        appContext.packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /**
     * Tell system_server's FreezeTaskRemovedHook to track this package. On task removal the
     * hook will refreeze it directly via PM (system uid, no root). Broadcast is guarded by
     * [KEEPALIVE_PREF_READ_PERMISSION] so only XMSF can trigger it.
     */
    private fun notifyTaskRemovedTracking(packageName: String) {
        runCatching {
            val intent = Intent(ACTION_FREEZE_LAUNCH_ACTIVATED).apply {
                putExtra(EXTRA_FREEZE_PACKAGE, packageName)
                putExtra(EXTRA_FREEZE_USER_ID, currentUserId())
            }
            appContext.sendBroadcast(intent)
        }.onFailure {
            logW { "freeze task-removed tracking broadcast failed pkg=$packageName" }
        }
    }

    private fun isPackageForeground(packageName: String): Boolean = runCatching {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        am.runningAppProcesses?.any {
            it.processName == packageName &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } == true
    }.getOrDefault(false)

    private fun isMainXmsfProcess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return Application.getProcessName() == XMSF_PROCESS_NAME
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun currentUserId(): Int = Process.myUid() / PER_USER_RANGE

    private fun emitFreezeEvent(result: String, reason: String, targetPackage: String) {
        MagiskOtel.event(
            name = "push.freeze",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "freeze_flow",
                "reason" to reason,
                "target_package" to targetPackage,
            ),
            statusOk = result != "error",
        )
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val RECENT_LAUNCH_WINDOW_MS = 10_000L
        const val SCREEN_OFF_THROTTLE_MS = 1500L
        const val PACKAGE_READY_TIMEOUT_MS = 2200L
        const val PACKAGE_READY_STEP_MS = 50L
        const val PER_USER_RANGE = 100_000
        const val TIMED_RETRY_MINUTES = 1
    }
}
