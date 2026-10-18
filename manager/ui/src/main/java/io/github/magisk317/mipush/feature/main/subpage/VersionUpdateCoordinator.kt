package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfDelivery
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfInstallResult
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfLatestCheck
import io.github.magisk317.uikit.update.AppUpdateChecker
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.UpdateCheckResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hint line rendered under a version row on the overview page. The row itself
 * keeps showing the installed version; the hint answers "is there something
 * newer, and what happened when you tapped".
 */
sealed interface VersionRowHint {
    /** Nothing probed yet: render no hint line at all. */
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
 * two-line overview report and the settings two-line snackbar possible.
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
     * Version-row tap handler: check both targets, then install everything
     * that is available — runtime first, manager second. Safe to call from
     * either row.
     */
    suspend fun checkAndUpdate() {
        mutex.withLock {
            val report = checkBothLocked()
            updateRuntime(report.runtime)
            updateManager(report.manager)
        }
    }

    private suspend fun checkBothLocked(): VersionCheckReport {
        _managerHint.value = VersionRowHint.Checking
        _runtimeHint.value = VersionRowHint.Checking
        return coroutineScope {
            val managerDeferred = async { checker.check() }
            val runtimeDeferred = async { deliveryProvider().checkLatestRuntime() }
            val manager = managerDeferred.await()
            val runtime = runtimeDeferred.await()
            _managerHint.value = manager.toHint()
            _runtimeHint.value = runtime.toHint()
            VersionCheckReport(manager = manager, runtime = runtime)
        }
    }

    private suspend fun updateRuntime(runtime: XmsfLatestCheck) {
        val available = runtime as? XmsfLatestCheck.Available ?: return
        val result = deliveryProvider().ensureInstalled(
            targetVersionName = available.latest.versionName,
        ) { percent ->
            // The downloader reports from Dispatchers.IO; StateFlow writes are
            // thread-safe and the UI collects on the main dispatcher.
            _runtimeHint.value = VersionRowHint.Updating(percent)
        }
        _runtimeHint.value = when (result) {
            is XmsfInstallResult.Installed -> VersionRowHint.UpToDate
            is XmsfInstallResult.GuidanceOpened -> VersionRowHint.Unsupported
            is XmsfInstallResult.Failed -> VersionRowHint.Failed(result.message)
        }
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

/** Map a hint to its display line; null renders no hint line at all. */
@Composable
internal fun VersionRowHint.displayText(): String? = when (this) {
    is VersionRowHint.Hidden -> null
    is VersionRowHint.Checking -> stringResource(R.string.version_hint_checking)
    is VersionRowHint.UpToDate -> stringResource(R.string.version_hint_up_to_date)
    is VersionRowHint.Available -> stringResource(R.string.version_hint_available, latestVersionName)
    is VersionRowHint.Updating -> stringResource(R.string.version_hint_updating, percent)
    is VersionRowHint.Failed -> stringResource(R.string.version_hint_failed, message)
    is VersionRowHint.Unsupported -> stringResource(R.string.version_hint_unsupported)
}
