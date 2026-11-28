package io.github.magisk317.mipush.feature.main.subpage

import android.content.Context
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfDelivery
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfInstallResult
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfLatestCheck
import io.github.magisk317.uikit.update.AppUpdateChecker
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.UpdateCheckResult
import io.github.magisk317.uikit.update.UpdateManifest
import io.github.magisk317.uikit.update.UpdateUiState
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfRuntimeManifest
import io.github.magisk317.mipush.manager.xmsfdelivery.XmsfVersionComparator
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

    data object WaitingForSystemConfirmation : VersionRowHint

    data object Installed : VersionRowHint

    data class Failed(val message: String) : VersionRowHint

    /** This build cannot probe or install the runtime in-process. */
    data object Unsupported : VersionRowHint
}

/** Outcome of a silent both-targets check, consumed by the settings report. */
data class VersionCheckReport(
    val manager: UpdateCheckResult,
    val runtime: XmsfLatestCheck,
)

internal interface PairedOtaJournal {
    data class Pending(
        val managerVersionCode: Long,
        val managerVersionName: String,
        val runtimeVersionName: String,
    )

    fun read(): Pending?
    fun begin(manager: UpdateManifest, runtime: XmsfRuntimeManifest)
    fun clear()
}

internal class SharedPreferencesPairedOtaJournal(context: Context) : PairedOtaJournal {
    private val preferences = (context.applicationContext ?: context)
        .getSharedPreferences("paired_ota", Context.MODE_PRIVATE)

    override fun read(): PairedOtaJournal.Pending? {
        val managerVersionCode = preferences.getLong(KEY_MANAGER_VERSION_CODE, 0L)
        val managerVersionName = preferences.getString(KEY_MANAGER_VERSION_NAME, null)
        val runtimeVersionName = preferences.getString(KEY_RUNTIME_VERSION_NAME, null)
        return if (managerVersionCode > 0L &&
            !managerVersionName.isNullOrBlank() &&
            !runtimeVersionName.isNullOrBlank()
        ) {
            PairedOtaJournal.Pending(managerVersionCode, managerVersionName, runtimeVersionName)
        } else {
            null
        }
    }

    override fun begin(manager: UpdateManifest, runtime: XmsfRuntimeManifest) {
        preferences.edit()
            .putLong(KEY_MANAGER_VERSION_CODE, manager.versionCode.toLong())
            .putString(KEY_MANAGER_VERSION_NAME, manager.versionName)
            .putString(KEY_RUNTIME_VERSION_NAME, runtime.versionName)
            .commit()
    }

    override fun clear() {
        preferences.edit()
            .remove(KEY_MANAGER_VERSION_CODE)
            .remove(KEY_MANAGER_VERSION_NAME)
            .remove(KEY_RUNTIME_VERSION_NAME)
            .commit()
    }

    private companion object {
        const val KEY_MANAGER_VERSION_CODE = "manager_version_code"
        const val KEY_MANAGER_VERSION_NAME = "manager_version_name"
        const val KEY_RUNTIME_VERSION_NAME = "runtime_version_name"
    }
}

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
 * CI installation is sequential and manager-first: the manager APK owns the
 * process and must be replaced before a runtime whose manifest requires the
 * target manager can be installed. The journal resumes the runtime step after
 * the manager process restarts.
 *
 * One [Mutex] serializes checks and updates, so tapping both version rows —
 * or tapping again mid-flight — queues behind the running flow instead of
 * racing it.
 */
internal class VersionUpdateCoordinator(
    private val checker: AppUpdateChecker,
    private val controller: AppUpdateController,
    private val pairedOtaJournal: PairedOtaJournal,
    private val deliveryProvider: () -> XmsfDelivery,
) {

    private val mutex = Mutex()

    private val _managerHint = MutableStateFlow<VersionRowHint>(VersionRowHint.Hidden)
    val managerHint: StateFlow<VersionRowHint> = _managerHint.asStateFlow()

    private val _runtimeHint = MutableStateFlow<VersionRowHint>(VersionRowHint.Hidden)
    val runtimeHint: StateFlow<VersionRowHint> = _runtimeHint.asStateFlow()

    private val _pairedVisible = MutableStateFlow(false)
    val pairedVisible: StateFlow<Boolean> = _pairedVisible.asStateFlow()

    private val _pairedStarted = MutableStateFlow(false)
    val pairedStarted: StateFlow<Boolean> = _pairedStarted.asStateFlow()

    private val _managerCompleted = MutableStateFlow(false)
    val managerCompleted: StateFlow<Boolean> = _managerCompleted.asStateFlow()

    private val _managerInstallStarted = MutableStateFlow(false)
    val managerInstallStarted: StateFlow<Boolean> = _managerInstallStarted.asStateFlow()

    private var latestReport: VersionCheckReport? = null

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
            is VersionRowHint.Installed, is VersionRowHint.WaitingForSystemConfirmation -> 0L
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

    /** Check both CI targets and expose one confirmation surface without installing yet. */
    suspend fun checkAndShowPaired(): VersionCheckReport? = mutex.withLock {
        val report = checkBothLocked()
        preparePaired(report)
        report.takeIf { hasPairedUpdate(it) }
    }

    /** Start the already checked pair from the single CI OTA dialog. */
    suspend fun startPairedUpdate() = mutex.withLock {
        val pending = pairedOtaJournal.read()
        if (pending != null && checker.currentVersionCode().toLong() < pending.managerVersionCode) {
            if (controller.state.value is UpdateUiState.Available) {
                _pairedVisible.value = true
                _pairedStarted.value = true
                controller.download()
            }
            return@withLock
        }
        val report = latestReport ?: checkBothLocked().also(::preparePaired)
        if (!hasPairedUpdate(report)) return@withLock
        _pairedVisible.value = true
        _pairedStarted.value = true
        val manager = report.manager as? UpdateCheckResult.Available
        val runtime = report.runtime
        if (manager != null) {
            if (runtime is XmsfLatestCheck.Available) {
                pairedOtaJournal.begin(manager.manifest, runtime.latest)
            }
            controller.download()
        } else {
            _managerCompleted.value = true
            updateRuntime(runtime)
        }
    }

    /** Called by the paired dialog once the manager APK is downloaded and ready. */
    fun installManager(context: Context) {
        if (_managerInstallStarted.value) return
        _managerInstallStarted.value = true
        setHint(UpdateTarget.Manager, VersionRowHint.Updating(100))
        controller.install(context)
    }

    fun retryManager() {
        _managerInstallStarted.value = false
        controller.retry()
    }

    /** Retry the runtime leg after the system installer or download reports failure. */
    suspend fun retryRuntime() = mutex.withLock {
        val pending = pairedOtaJournal.read() ?: return@withLock
        _pairedVisible.value = true
        _pairedStarted.value = true
        _managerCompleted.value = checker.currentVersionCode().toLong() >= pending.managerVersionCode
        if (!_managerCompleted.value) return@withLock
        resumePendingRuntime()
    }

    /** A return from the system installer with the old manager still present means retry is valid. */
    fun onHostResumed() {
        val pending = pairedOtaJournal.read()
        val targetCode = (latestReport?.manager as? UpdateCheckResult.Available)?.manifest?.versionCode
            ?: pending?.managerVersionCode?.toInt()
            ?: return
        if (checker.currentVersionCode() < targetCode && controller.state.value is UpdateUiState.ReadyToInstall) {
            _managerInstallStarted.value = false
            val targetName = (latestReport?.manager as? UpdateCheckResult.Available)?.manifest?.versionName
                ?: pending?.managerVersionName.orEmpty()
            setHint(UpdateTarget.Manager, VersionRowHint.Available(targetName))
        }
    }

    val canRetryPairedRuntime: Boolean
        get() = _managerCompleted.value && pairedOtaJournal.read() != null

    fun dismissPaired() {
        if (_pairedStarted.value && !managerCompleted.value && pairedOtaJournal.read() != null) return
        _pairedVisible.value = false
        if (_managerHint.value is VersionRowHint.Installed) _managerHint.value = VersionRowHint.Hidden
        if (_runtimeHint.value is VersionRowHint.Installed) _runtimeHint.value = VersionRowHint.Hidden
    }

    private fun preparePaired(report: VersionCheckReport) {
        latestReport = report
        if (!hasPairedUpdate(report)) {
            _pairedVisible.value = false
            return
        }
        _managerInstallStarted.value = false
        _managerCompleted.value = report.manager !is UpdateCheckResult.Available
        _pairedStarted.value = false
        _pairedVisible.value = true
        (report.manager as? UpdateCheckResult.Available)?.let { controller.adoptAvailable(it) }
    }

    private fun hasPairedUpdate(report: VersionCheckReport): Boolean =
        report.manager is UpdateCheckResult.Available || report.runtime is XmsfLatestCheck.Available

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
        val report = checkBothLocked()
        installLocked(report)
    }

    suspend fun checkAndUpdateRuntime() = mutex.withLock {
        setHint(UpdateTarget.Runtime, VersionRowHint.Checking)
        val runtime = deliveryProvider().checkLatestRuntime()
        setHint(UpdateTarget.Runtime, runtime.toHint())
        updateRuntime(runtime)
    }

    /**
     * Cold-start entry point. CI builds always reach this method through the
     * shared startup policy; non-CI builds retain their existing
     * channel policy. A pending manager-first transaction is resumed before
     * probing a new update.
     */
    suspend fun checkOnStartup() = mutex.withLock {
        if (pairedOtaJournal.read() != null) {
            val pending = pairedOtaJournal.read() ?: return@withLock
            _pairedVisible.value = true
            _managerCompleted.value = checker.currentVersionCode().toLong() >= pending.managerVersionCode
            if (!_managerCompleted.value) {
                _pairedStarted.value = false
                _managerInstallStarted.value = false
                val manager = checker.check()
                setHint(UpdateTarget.Manager, manager.toHint())
                if (manager is UpdateCheckResult.Available) {
                    setHint(UpdateTarget.Runtime, VersionRowHint.Available(pending.runtimeVersionName))
                    preparePaired(VersionCheckReport(manager, XmsfLatestCheck.UpToDate))
                }
                if (manager !is UpdateCheckResult.Available) {
                    _managerHint.value = when (manager) {
                        is UpdateCheckResult.Failed -> VersionRowHint.Failed(manager.reason)
                        is UpdateCheckResult.UpToDate -> VersionRowHint.Failed(
                            "paired manager target ${pairedOtaJournal.read()?.managerVersionName} is not installed",
                        )
                        is UpdateCheckResult.Available -> error("unreachable")
                    }
                }
                return@withLock
            }
            _pairedStarted.value = true
            _managerInstallStarted.value = false
            _managerHint.value = VersionRowHint.Installed
            resumePendingRuntime()
            return@withLock
        }
        val report = checkBothLocked()
        preparePaired(report)
    }

    /**
     * Settings entry for the legacy release path. CI/debug uses the paired
     * dialog and its manager-first transaction instead.
     */
    suspend fun checkAndUpdate() {
        mutex.withLock {
            val report = checkBothLocked()
            installLocked(report)
        }
    }

    /**
     * Install what a previous silent check ([checkBoth]) found, in the same
     * existing release controller and runtime delivery behavior.
     */
    suspend fun installAvailable(report: VersionCheckReport) = mutex.withLock {
        installLocked(report)
    }

    private suspend fun installLocked(report: VersionCheckReport) {
        val manager = report.manager as? UpdateCheckResult.Available
        val currentManagerVersionCode = manager?.currentVersionCode?.toLong()
        val runtime = report.runtime
        if (manager != null) {
            pairedRuntimeReason(manager.manifest, runtime)?.let { reason ->
                setHint(UpdateTarget.Runtime, VersionRowHint.Failed(reason))
                return
            }
        }
        if (manager != null &&
            runtime !is XmsfLatestCheck.Available &&
            runtime !is XmsfLatestCheck.UpToDate
        ) {
            return
        }
        if (manager != null && runtime is XmsfLatestCheck.Available &&
            currentManagerVersionCode != null &&
            runtime.latest.minManagerVersionCode > currentManagerVersionCode
        ) {
            pairedOtaJournal.begin(manager.manifest, runtime.latest)
            updateManager(report.manager)
            return
        }
        val runtimeResult = updateRuntime(
            runtime = runtime,
            targetManagerVersionCode = currentManagerVersionCode,
        )
        if (runtime is XmsfLatestCheck.Available &&
            runtimeResult !is XmsfInstallResult.Installed
        ) {
            return
        }
        updateManager(report.manager)
    }

    private suspend fun checkBothLocked(): VersionCheckReport {
        setHint(UpdateTarget.Manager, VersionRowHint.Checking)
        setHint(UpdateTarget.Runtime, VersionRowHint.Checking)
        return coroutineScope {
            val manager = checker.check()
            val targetManagerVersionCode =
                (manager as? UpdateCheckResult.Available)?.manifest?.versionCode?.toLong()
            val runtime = async {
                deliveryProvider().checkLatestRuntime(targetManagerVersionCode)
            }.await()
            setHint(UpdateTarget.Manager, manager.toHint())
            setHint(UpdateTarget.Runtime, runtime.toHint())
            VersionCheckReport(manager = manager, runtime = runtime).also { latestReport = it }
        }
    }

    private suspend fun updateRuntime(
        runtime: XmsfLatestCheck,
        targetManagerVersionCode: Long? = null,
    ): XmsfInstallResult? {
        val available = runtime as? XmsfLatestCheck.Available ?: return null
        val result = deliveryProvider().ensureInstalled(
            targetVersionName = available.latest.versionName,
            targetManagerVersionCode = targetManagerVersionCode,
            onProgress = { percent ->
            // The downloader reports from Dispatchers.IO; setHint is thread-safe
            // and the UI collects on the main dispatcher.
            setHint(UpdateTarget.Runtime, VersionRowHint.Updating(percent))
            },
            onWaitingForSystemConfirmation = {
                setHint(UpdateTarget.Runtime, VersionRowHint.WaitingForSystemConfirmation)
            },
        )
        setHint(
            UpdateTarget.Runtime,
            when (result) {
                is XmsfInstallResult.Installed -> if (_pairedStarted.value) {
                    VersionRowHint.Installed
                } else {
                    VersionRowHint.UpToDate
                }
                is XmsfInstallResult.GuidanceOpened -> VersionRowHint.Unsupported
                is XmsfInstallResult.Failed -> VersionRowHint.Failed(result.message)
            },
        )
        return result
    }

    private suspend fun resumePendingRuntime() {
        val pending = pairedOtaJournal.read() ?: return
        if (checker.currentVersionCode().toLong() < pending.managerVersionCode) {
            return
        }
        val result = deliveryProvider().ensureInstalled(
            targetVersionName = pending.runtimeVersionName,
            targetManagerVersionCode = pending.managerVersionCode,
            onProgress = { percent ->
                setHint(UpdateTarget.Runtime, VersionRowHint.Updating(percent))
            },
            onWaitingForSystemConfirmation = {
                setHint(UpdateTarget.Runtime, VersionRowHint.WaitingForSystemConfirmation)
            },
        )
        when (result) {
            is XmsfInstallResult.Installed -> {
                pairedOtaJournal.clear()
                setHint(UpdateTarget.Runtime, VersionRowHint.Installed)
            }
            else -> setHint(
                UpdateTarget.Runtime,
                when (result) {
                    is XmsfInstallResult.GuidanceOpened -> VersionRowHint.Unsupported
                    is XmsfInstallResult.Failed -> VersionRowHint.Failed(result.message)
                    is XmsfInstallResult.Installed -> VersionRowHint.UpToDate
                },
            )
        }
    }

    private fun pairedRuntimeReason(
        manager: UpdateManifest,
        runtime: XmsfLatestCheck,
    ): String? {
        if (!manager.force) return null
        return when (runtime) {
            is XmsfLatestCheck.Available -> {
                val latest = runtime.latest
                when {
                    latest.versionName != manager.versionName ->
                        "runtime version does not match manager version"
                    manager.commit.isNotBlank() &&
                        latest.commit.isNotBlank() &&
                        manager.commit != latest.commit ->
                        "runtime commit does not match manager commit"
                    else -> null
                }
            }
            XmsfLatestCheck.UpToDate -> {
                val installed = deliveryProvider().status.value.installedVersionName
                if (installed != null &&
                    XmsfVersionComparator.isNewer(manager.versionName, installed)
                ) {
                    "installed runtime predates manager version"
                } else {
                    null
                }
            }
            XmsfLatestCheck.Unsupported -> "runtime update is not supported by this build"
            is XmsfLatestCheck.Failed -> runtime.message
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
