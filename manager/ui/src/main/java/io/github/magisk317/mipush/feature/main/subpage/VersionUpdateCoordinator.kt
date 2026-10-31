package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfDelivery
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfInstallResult
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfLatestCheck
import io.github.magisk317.uikit.update.AppUpdateChecker
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.UpdateCheckResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Which update target a [VersionRowHint] belongs to. */
internal enum class UpdateTarget { Manager, Runtime }

/**
 * Transient state of one update target. Rendered inline in the owning version
 * row's value slot on the overview page — no separate hint line. The row keeps
 * showing the installed version whenever this is [Hidden]; any other state
 * answers "is there something newer, and what happened when you tapped".
 */
sealed interface VersionRowHint {
    /** Nothing probed yet: the row shows the plain version. */
    data object Hidden : VersionRowHint

    data object Checking : VersionRowHint

    data object UpToDate : VersionRowHint

    data class Available(val latestVersionName: String) : VersionRowHint

    /** Runtime in-process install progress (0..100); manager updates report through the dialog. */
    data class Updating(val percent: Int) : VersionRowHint

    data class Failed(val message: String) : VersionRowHint

    /** Play flavor: the runtime cannot be probed or installed in-process. */
    data object Unsupported : VersionRowHint
}

/** Outcome of a silent both-targets check, consumed by the settings report. */
data class VersionCheckReport(
    val manager: UpdateCheckResult,
    val runtime: XmsfLatestCheck,
)

/**
 * Single coordinator for the two update targets this app has: itself (through
 * the dialog-driven [AppUpdateController]) and the XMSF runtime (in-process
 * through [XmsfDelivery]).
 *
 * Checks are silent — [AppUpdateChecker] is queried directly instead of going
 * through the controller, so probing never opens a dialog or mutates the
 * controller's state machine. Both targets are probed in parallel and each
 * reports through its own [VersionRowHint] flow, which is what makes the
 * inline row states and the settings two-line snackbar possible.
 *
 * Hint writes go through [setHint], which auto-clears the terminal no-op
 * states (up to date, failed, unsupported) after a short dwell so the version
 * rows keep showing the installed version again; progress and actionable
 * states (checking, updating, update available) persist until replaced.
 *
 * Installation is sequential and runtime-first on purpose: replacing the
 * manager APK kills this very process, so the runtime must already be in
 * place by the time the new manager starts. The runtime installs in-process
 * (progress rides the hint flow); the manager install reuses the controller,
 * whose dialog is hosted by MainActivity and therefore works from any page.
 *
 * One [Mutex] serializes checks and updates, so tapping both version rows —
 * or tapping again mid-flight — queues behind the running flow instead of
 * racing it.
 */
class VersionUpdateCoordinator(
    private val checker: AppUpdateChecker,
    private val controller: AppUpdateController,
    private val deliveryProvider: () -> XmsfDelivery,
) {

    private val mutex = Mutex()

    private val _managerHint = MutableStateFlow<VersionRowHint>(VersionRowHint.Hidden)
    val managerHint: StateFlow<VersionRowHint> = _managerHint.asStateFlow()

    private val _runtimeHint = MutableStateFlow<VersionRowHint>(VersionRowHint.Hidden)
    val runtimeHint: StateFlow<VersionRowHint> = _runtimeHint.asStateFlow()

    // Auto-clear timers for the terminal no-op states; writes come from both the
    // mutex-guarded check flow and the downloader's Dispatchers.IO callbacks.
    private val autoClearScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val autoClearJobs = HashMap<UpdateTarget, Job>()

    private fun setHint(target: UpdateTarget, hint: VersionRowHint) {
        synchronized(autoClearJobs) { autoClearJobs.remove(target)?.cancel() }
        val flow = when (target) {
            UpdateTarget.Manager -> _managerHint
            UpdateTarget.Runtime -> _runtimeHint
        }
        flow.value = hint
        val dwellMillis = when (hint) {
            is VersionRowHint.UpToDate, is VersionRowHint.Unsupported -> 3_000L
            is VersionRowHint.Failed -> 6_000L
            else -> 0L
        }
        if (dwellMillis > 0L) {
            val job = autoClearScope.launch {
                delay(dwellMillis)
                synchronized(autoClearJobs) { autoClearJobs.remove(target) }
                flow.value = VersionRowHint.Hidden
            }
            synchronized(autoClearJobs) { autoClearJobs[target] = job }
        }
    }

    /** Whether this flavor can install the runtime in-process (github only). */
    val canUpdateRuntimeInApp: Boolean get() = deliveryProvider().canInstallDirectly

    /** Open the manual runtime guidance page (play flavor fallback). */
    fun openRuntimeGuidance(context: Context) = deliveryProvider().openGuidance(context)

    /**
     * Silent check of both targets. Never installs and never opens a dialog;
     * the per-row hints are refreshed as a side effect.
     */
    suspend fun checkBoth(): VersionCheckReport = mutex.withLock {
        checkBothLocked()
    }

    /**
     * Version-row tap handlers are per-target: the manager row checks and
     * updates only the manager, the runtime row only the runtime. The
     * both-targets flow below stays for the settings entry, which reports and
     * installs in the fixed runtime-first order.
     */
    suspend fun checkAndUpdateManager() = mutex.withLock {
        setHint(UpdateTarget.Manager, VersionRowHint.Checking)
        val manager = checker.check()
        setHint(UpdateTarget.Manager, manager.toHint())
        if (manager is UpdateCheckResult.Available) {
            // The controller owns the dialog/download/install/relaunch chain
            // and hosts it from MainActivity, so it works from any page.
            controller.check(userInitiated = true)
        }
    }

    suspend fun checkAndUpdateRuntime() = mutex.withLock {
        setHint(UpdateTarget.Runtime, VersionRowHint.Checking)
        val runtime = deliveryProvider().checkLatestRuntime()
        setHint(UpdateTarget.Runtime, runtime.toHint())
        updateRuntime(runtime)
    }

    /**
     * Settings entry: check both targets, then install everything that is
     * available — runtime first, manager second.
     */
    suspend fun checkAndUpdate() {
        mutex.withLock {
            val report = checkBothLocked()
            installLocked(report)
        }
    }

    /**
     * Install what a previous silent check ([checkBoth]) found, in the same
     * fixed order: runtime first, manager second — replacing the manager APK
     * kills this process, so the runtime must already be in place by the time
     * the new manager starts. Progress rides the per-target hint flows; the
     * manager path opens the controller's dialog wherever the user is.
     */
    suspend fun installAvailable(report: VersionCheckReport) = mutex.withLock {
        installLocked(report)
    }

    private suspend fun installLocked(report: VersionCheckReport) {
        updateRuntime(report.runtime)
        updateManager(report.manager)
    }

    private suspend fun checkBothLocked(): VersionCheckReport {
        setHint(UpdateTarget.Manager, VersionRowHint.Checking)
        setHint(UpdateTarget.Runtime, VersionRowHint.Checking)
        return coroutineScope {
            val managerDeferred = async { checker.check() }
            val runtimeDeferred = async { deliveryProvider().checkLatestRuntime() }
            val manager = managerDeferred.await()
            val runtime = runtimeDeferred.await()
            setHint(UpdateTarget.Manager, manager.toHint())
            setHint(UpdateTarget.Runtime, runtime.toHint())
            VersionCheckReport(manager = manager, runtime = runtime)
        }
    }

    private suspend fun updateRuntime(runtime: XmsfLatestCheck) {
        val available = runtime as? XmsfLatestCheck.Available ?: return
        val result = deliveryProvider().ensureInstalled(
            targetVersionName = available.latest.versionName,
        ) { percent ->
            // The downloader reports from Dispatchers.IO; setHint is thread-safe
            // and the UI collects on the main dispatcher.
            setHint(UpdateTarget.Runtime, VersionRowHint.Updating(percent))
        }
        setHint(
            UpdateTarget.Runtime,
            when (result) {
                is XmsfInstallResult.Installed -> VersionRowHint.UpToDate
                is XmsfInstallResult.GuidanceOpened -> VersionRowHint.Unsupported
                is XmsfInstallResult.Failed -> VersionRowHint.Failed(result.message)
            },
        )
    }

    private fun updateManager(manager: UpdateCheckResult) {
        if (manager !is UpdateCheckResult.Available) return
        // The controller owns the dialog/download/install/relaunch chain; a
        // user-initiated re-check here only fires when the silent probe found
        // something the controller did not already know about.
        controller.check(userInitiated = true)
    }

    private fun UpdateCheckResult.toHint(): VersionRowHint = when (this) {
        is UpdateCheckResult.Available -> VersionRowHint.Available(manifest.versionName)
        is UpdateCheckResult.UpToDate -> VersionRowHint.UpToDate
        is UpdateCheckResult.Failed -> VersionRowHint.Failed(reason)
    }

    private fun XmsfLatestCheck.toHint(): VersionRowHint = when (this) {
        is XmsfLatestCheck.Available -> VersionRowHint.Available(latest.versionName)
        is XmsfLatestCheck.UpToDate -> VersionRowHint.UpToDate
        is XmsfLatestCheck.Unsupported -> VersionRowHint.Unsupported
        is XmsfLatestCheck.Failed -> VersionRowHint.Failed(message)
    }
}
