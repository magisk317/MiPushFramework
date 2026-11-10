package io.github.magisk317.mipush.app.di

import android.content.Context
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.ManagerUnregisterResult
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigListSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerNotificationChannelCommandGateway
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.configuration.ConfigEditorSnapshot
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.configuration.toSummary
import io.github.magisk317.mipush.common.utils.ElapsedTimer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.ShellUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.EventRetentionManager
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.adapter.container
import io.github.magisk317.mipush.runtime.store.event.type.NotificationType
import io.github.magisk317.mipush.runtime.store.event.type.RegistrationType
import io.github.magisk317.mipush.runtime.store.event.type.UnknownType
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import io.github.magisk317.mipush.manager.runtime.read.AndroidManagerApplicationReadSource
import io.github.magisk317.mipush.manager.runtime.read.InstalledApplicationSnapshot
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadPolicy
import io.github.magisk317.mipush.manager.runtime.read.RegistrationEventSnapshot
import io.github.magisk317.mipush.manager.runtime.read.toManagerApplication
import io.github.magisk317.mipush.manager.runtime.read.toStoredApplicationSnapshot
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.utils.RegistrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import io.github.magisk317.mipush.common.logging.StableLogTag

@StableLogTag
private const val FORCE_REGISTER_RESULT_TIMEOUT_MS = 6_000L
private const val FORCE_REGISTER_RESULT_POLL_MS = 250L
private const val FORCE_REGISTER_ROOT_LAUNCH_TIMEOUT_MS = 5_000L

class XmsfManagerApplicationGateway : ManagerApplicationGateway {

    override suspend fun loadApplications(
        context: Context,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ): ManagerApplications {
        val timer = ElapsedTimer()
        val pendingPackages = RegisteredApplicationDb.getPendingRegistrations().map { it.packageName }.toSet()
        var registered = RegisteredApplicationDb.getList(null)
            .filter { includeSystemApps || Utils.isUserApplication(it.packageName) }
            .associateBy { it.packageName }
            .toMutableMap()
        val readSource = AndroidManagerApplicationReadSource(context)
        val catalog = readSource.readInstalledApplications(includeSystemApps)
        val lastReceiveTimes = readSource.readLastReceiveTimes(
            catalog.applications.map(InstalledApplicationSnapshot::packageName),
        )
        data class EnrichedApp(
            val row: RuntimeRegisteredApplicationRow,
            val existServices: Boolean,
            val lastReceiveTimeMs: Long,
        )
        val enrichedApps = catalog.applications
            .map { installed ->
                val base = registered[installed.packageName] ?: RegisteredApplicationDb.registerApplication(installed.packageName)
                val effectiveName = base.appName.takeIf { it.isNotBlank() } ?: installed.appName
                EnrichedApp(
                    row = base.copy(appName = effectiveName),
                    existServices = installed.hasMiPushServices,
                    lastReceiveTimeMs = lastReceiveTimes[base.packageName] ?: 0L,
                )
            }
        reconcileLocalRegistrationState(enrichedApps.map { it.row })
        // Local probing and a server result may update REGISTERED_APPLICATION while this read is
        // in flight. Re-read before building the response so list and detail callers observe the
        // same committed state in the very request that performed reconciliation.
        registered = RegisteredApplicationDb.getList(null)
            .filter { includeSystemApps || Utils.isUserApplication(it.packageName) }
            .associateBy { it.packageName }
            .toMutableMap()
        val apps = enrichedApps
            .map { enriched ->
                enriched.copy(row = registered[enriched.row.packageName] ?: enriched.row)
            }
            .asSequence()
            .map { enriched ->
                enriched.row.toStoredApplicationSnapshot().toManagerApplication(
                    installed = InstalledApplicationSnapshot(
                        packageName = enriched.row.packageName,
                        appName = enriched.row.appName,
                        hasMiPushServices = enriched.existServices,
                    ),
                    lastReceiveTimeMs = enriched.lastReceiveTimeMs,
                    locallyRegistered = false,
                    fallbackToInstalledName = false,
                    deriveAppNamePinYin = true,
                ).copy(registrationPending = enriched.row.packageName in pendingPackages)
            }
            .filter { ManagerApplicationReadPolicy.matchesQuery(it, query) }
            .filter { ManagerApplicationReadPolicy.matchesFilter(it, filterMode) }
            .sortedWith(ManagerApplicationReadPolicy.comparator)
            .toList()
        XLog.tagged("XmsfManagerApplicationGateway").d {
            "manager app list loaded total=${catalog.totalCandidatePackages} shown=${apps.size} ms=${timer.elapsed()}"
        }
        return ManagerApplications(
            registeredPkgs = registered.mapValues { (_, app) ->
                app.toStoredApplicationSnapshot().toManagerApplication(
                    installed = InstalledApplicationSnapshot(
                        packageName = app.packageName,
                        appName = app.appName,
                        hasMiPushServices = false,
                    ),
                    lastReceiveTimeMs = 0L,
                    locallyRegistered = false,
                    fallbackToInstalledName = false,
                    deriveAppNamePinYin = true,
                ).copy(registrationPending = app.packageName in pendingPackages)
            },
            items = apps,
            totalPkg = catalog.totalCandidatePackages,
        )
    }

    private fun reconcileLocalRegistrationState(applications: List<RuntimeRegisteredApplicationRow>) {
        val candidates = applications
            .asSequence()
            .filter { it.registeredType == RegisteredAppRegisteredType.NotRegistered }
            .map { it.packageName }
            .toList()
        if (candidates.isEmpty()) return
        val locallyRegistered = RegistrationStateCompat.findPackagesWithValidLocalRegistration(candidates)
        if (locallyRegistered.isEmpty()) return
        applications.forEach { application ->
            if (application.packageName in locallyRegistered) {
                RegistrationStateStore.updateIfChanged(
                    application = application,
                    nextType = RegisteredAppRegisteredType.Registered,
                    source = RegistrationStateStore.Source.LOCAL_PROBE,
                )
            }
        }
    }

    override suspend fun getApplication(context: Context, packageName: String, ignoreNotRegistered: Boolean): ManagerApplication? {
        var application = RegisteredApplicationDb.getRegisteredApplication(packageName)
        if (application == null && ignoreNotRegistered) {
            application = transientRegisteredApplication(
                packageName = packageName,
                appName = Global.applicationNameCache().getAppName(context, packageName).toString(),
                userId = Utils.requireValidUserId(Utils.myUserId()),
            )
        }
        if (application != null &&
            application.registeredType == RegisteredAppRegisteredType.NotRegistered &&
            RegistrationStateCompat.hasValidLocalRegistration(packageName)
        ) {
            RegistrationStateStore.updateIfChanged(
                application = application,
                nextType = RegisteredAppRegisteredType.Registered,
                source = RegistrationStateStore.Source.LOCAL_PROBE,
            )
            // Re-read after state change
            application = RegisteredApplicationDb.getRegisteredApplication(packageName) ?: application
        }
        application ?: return null
        val readSource = AndroidManagerApplicationReadSource(context)
        val lastReceiveTimeMs = readSource.readLastReceiveTime(application.packageName)
        val existServices = readSource.readInstalledApplication(application.packageName)
            ?.hasMiPushServices == true
        return application.toStoredApplicationSnapshot().toManagerApplication(
            installed = InstalledApplicationSnapshot(
                packageName = application.packageName,
                appName = application.appName,
                hasMiPushServices = existServices,
            ),
            lastReceiveTimeMs = lastReceiveTimeMs,
            locallyRegistered = false,
            fallbackToInstalledName = false,
            deriveAppNamePinYin = false,
        ).copy(registrationPending = RegisteredApplicationDb.getPendingRegistration(packageName, application.userId) != null)
    }

    override suspend fun updateApplication(application: ManagerApplication) {
        PackageRegistrationGuard.withPackage(application.packageName, application.userId) {
            val latest = RegisteredApplicationDb.getRegisteredApplication(application.packageName) ?: return@withPackage
            // Legacy transport still carries type/blocked/status; only the policy operation may
            // change admission. A settings snapshot must not undo a concurrent manual revocation.
            RegisteredApplicationDb.update(mergeEditableApplicationSettings(latest, application))
        }
    }

    override suspend fun getDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics {
        val userId = Utils.requireValidUserId(Utils.myUserId())
        val latestRegistrationEvent = EventDb.queryAsync(
            skip = 0,
            limit = 1,
            types = setOf(
                EventRowType.Registration,
                EventRowType.RegistrationResult,
                EventRowType.UnRegistration,
            ),
            pkg = packageName,
            text = null,
        ).firstOrNull()
        val hasLocalRegistration = RegistrationStateCompat.hasValidLocalRegistration(packageName)
        val regSecCount = Utils.getRegSecs(packageName).size
        return ManagerApplicationDiagnostics(
            hasLocalRegistration = hasLocalRegistration,
            regSecCount = regSecCount,
            latestRegistrationEventResult = latestRegistrationEvent?.result,
            registeredType = registeredType,
            inferenceReason = ManagerApplicationReadPolicy.inferReason(
                registeredType = registeredType,
                latestEvent = latestRegistrationEvent?.let {
                    RegistrationEventSnapshot(type = it.type, result = it.result)
                },
                hasLocalRegistration = hasLocalRegistration,
                hasRegSec = regSecCount > 0,
            ),
            userId = userId,
        )
    }

    override suspend fun setRegistrationPolicy(context: Context, packageName: String, type: Int) =
        io.github.magisk317.mipush.service.RegistrationApprovalCoordinator.setPolicy(context, packageName, type)

    override suspend fun approvePendingRegistration(context: Context, packageName: String) =
        io.github.magisk317.mipush.service.RegistrationApprovalCoordinator.approve(context, packageName)

    override suspend fun unregisterApplication(
        context: Context,
        packageName: String,
    ): ManagerUnregisterResult {
        // The app-side MiPush data wipe runs as root, so elevation is mandatory.
        if (!PermissionUtils.requestRootAccess()) {
            logW("unregister aborted for $packageName: root not granted")
            recordUnregisterEvent(packageName, "unregister_abort_root_denied", EventRowResultType.DENY_USER)
            return ManagerUnregisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.unregister_requires_root),
            )
        }
        val userId = Utils.requireValidUserId(Utils.myUserId())
        return PackageRegistrationGuard.withPackage(packageName, userId) {
            unregisterLocked(context, packageName, userId)
        }
    }

    private fun unregisterLocked(context: Context, packageName: String, userId: Int): ManagerUnregisterResult {
        val application = RegisteredApplicationDb.getRegisteredApplication(packageName)
            ?: return ManagerUnregisterResult(false, context.getString(com.xiaomi.xmsf.R.string.unregister_not_registered))
        val registration = PushShellBridgeHolder.registration()
        val appId = registration.rememberedAppId(context, packageName)
        // Block first. Retried local cleanup remains available after a partial revocation.
        RegisteredApplicationDb.update(application.copy(
            blocked = true,
            type = 3, // DENY; retained legacy registration-policy wire value.
        ))
        PackageRegistrationGuard.recordPolicyMutation(packageName, userId)
        if (!appId.isNullOrBlank()) {
            val accepted = runCatching {
                registration.reportAppAbsent(context, packageName, appId, "manager_unregister")
            }.getOrDefault(false)
            if (!accepted) {
                return ManagerUnregisterResult(false, context.getString(com.xiaomi.xmsf.R.string.unregister_report_failed))
            }
        } else if (application.registeredType == RegisteredAppRegisteredType.Registered || !application.blocked) {
            return ManagerUnregisterResult(false, context.getString(com.xiaomi.xmsf.R.string.unregister_app_id_missing))
        }
        RegisteredApplicationDb.clearPendingRegistration(packageName, userId)
        registration.forgetRegisteredPackage(context, packageName)
        registration.forgetPendingRegistration(context, packageName)
        registration.markPackageUnregistered(context, packageName)
        RegistrationStateStore.updateIfChanged(
            application = RegisteredApplicationDb.getRegisteredApplication(packageName) ?: application,
            nextType = RegisteredAppRegisteredType.Unregistered,
            source = RegistrationStateStore.Source.MANUAL,
        )
        // Link the block: registration/notification gates ignore this app and dependent switches
        // clear, mirroring ApplicationInfoStatePolicy.withBlocked on the manager side.
        RegisteredApplicationDb.getRegisteredApplication(packageName)?.let { current ->
            RegisteredApplicationDb.update(
                current.copy(
                    blocked = true,
                    islandEnabled = false,
                    islandFocusNotification = false,
                    clickFallbackEnabled = false,
                    suppressWhenDisabled = false,
                    countLimitExempt = false,
                )
            )
        }
        // Drop cached offline registration payloads/tasks so the next bind cannot resurrect the regId.
        PushRuntimePendingPacketStore.discardPackage(packageName, userId)
        PushRuntimeRegistrationTaskStore.clear(packageName, userId)
        Utils.removeRegSec(packageName, userId)
        Utils.removeLastReceiveTime(packageName, userId)
        PushRuntime.clearPackageTransientState(packageName, userId)
        PushRuntime.observeUnregistration(
            packageName = packageName,
            source = "XmsfManagerApplicationGateway.unregisterApplication",
            reason = "manual_unregister",
            androidUserId = userId,
        )
        PushShellBridgeHolder.packageState().clearPackageDataShellState(context, packageName, userId)
        MiPushRuntimeBridge.clearPackageTransientState(packageName, userId)
        val wiped = runCatching { RegistrationHelper(context, packageName).removeMiPushData(userId) }
            .getOrDefault(false)
        logI("unregister: wiped app-side MiPush data for $packageName user=$userId success=$wiped")
        recordUnregisterEvent(packageName, "unregister_submitted wiped=$wiped", if (wiped) EventRowResultType.OK else EventRowResultType.DENY_DISABLED)
        return ManagerUnregisterResult(
            succeeded = wiped,
            message = context.getString(if (wiped) com.xiaomi.xmsf.R.string.unregister_completed else com.xiaomi.xmsf.R.string.unregister_partial),
        )
    }

    private fun recordUnregisterEvent(packageName: String, info: String, result: Int) {
        runCatching {
            runBlocking {
                EventDb.insertEventAsync(result, UnknownType(EventRowType.UnRegistration, info, packageName, null))
            }
        }.onFailure {
            logW("unregister event write failed for $packageName info=$info error=${it.message}")
        }
    }

    override suspend fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        // Force-register must actively request elevation (KSU/Magisk prompt).
        // refreshRootAccessIfGranted() returns false when grant state is still unknown.
        if (!PermissionUtils.requestRootAccess()) {
            XLog.tagged("XmsfManagerApplicationGateway").w {
                "force-register aborted: root not granted pkg=$packageName"
            }
            logW("force register aborted for $packageName: root not granted")
            recordForceRegisterStage(
                packageName,
                "force_register_abort_root_denied",
                EventRowResultType.DENY_USER,
            )
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_requires_root),
            )
        }
        val plan = RegistrationHelper.inspectForceRegisterPlan(packageName)
        if (!plan.supportsServiceDispatch && !plan.supportsReceiverFallback && plan.bridgeCandidates.isEmpty()) {
            logW("force register aborted for $packageName: ${plan.summary()}")
            recordForceRegisterStage(
                packageName,
                "force_register_abort_unsupported_components",
                EventRowResultType.DENY_DISABLED,
            )
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable),
            )
        }
        if (context.packageManager.getLaunchIntentForPackage(packageName) == null) {
            logW("force register aborted for $packageName: no launch intent")
            recordForceRegisterStage(
                packageName,
                "force_register_abort_no_launch_intent",
                EventRowResultType.DENY_DISABLED,
            )
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        }
        val forceUserId = Utils.requireValidUserId(Utils.myUserId())
        val admission = PackageRegistrationGuard.withPackage(packageName, forceUserId) {
            if (!PushShellBridgeHolder.registration().flushAppAbsentBeforeRegister(context, packageName)) {
                null
            } else {
                prepareForceRegistrationAdmission(packageName, forceUserId)
            }
        } ?: return ManagerForceRegisterResult(false, context.getString(com.xiaomi.xmsf.R.string.unregister_pending))
        return withRegistrationRollback(
            isConfirmed = { it.succeeded },
            rollback = { restoreForceRegistrationAdmission(admission) },
        ) {
            executeForceRegister(context, packageName, registeredType)
        }
    }

    private suspend fun executeForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        // Root's ActivityManager path first. This gateway runs inside the background xmsf
        // process, so Context.startActivity() is subject to Android's background-launch policy
        // even though the Manager UI initiated the call; `am start -W` is not, and it reports
        // whether the launch actually happened. Never force-stop: a stopped app has no live
        // process, so every dispatch channel fails — that was the "已拒绝" (DENY_DISABLED) case.
        // Toggle symmetry with manual unregister (which links blocked=true): clear the block
        // before launching, or every registration gate ignores this app and the result times out.
        recordForceRegisterStage(packageName, "force_register_relaunch")
        val rootLaunched = withContext(Dispatchers.IO) {
            launchTargetAppViaRoot(context, packageName)
        }
        if (!rootLaunched) {
            logW("force register: root launch failed for $packageName, falling back to SDK pull-up")
            recordForceRegisterStage(packageName, "force_register_root_launch_failed")
            runCatching {
                withContext(Dispatchers.IO) {
                    RegistrationHelper.launchAppForForceRegister(context, packageName)
                }
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                logW("force register: SDK pull up failed for $packageName: ${it.localizedMessage}")
                recordForceRegisterStage(packageName, "force_register_relaunch_failed")
            }
        } else {
            recordForceRegisterStage(packageName, "force_register_root_launch_succeeded")
            delay(500L)
        }
        return forceRegisterWithFeedback(context, packageName, registeredType)
    }

    private fun launchTargetAppViaRoot(context: Context, packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        val component = launchIntent.component?.flattenToShortString() ?: return false
        val userId = Utils.requireValidUserId(Utils.myUserId())
        val enable = AppRootAccessFacade.runRootCommand(
            "pm enable --user $userId ${shellQuote(packageName)}",
            timeoutMs = FORCE_REGISTER_ROOT_LAUNCH_TIMEOUT_MS,
        )
        if (!enable.isSuccess) {
            logW("force register: pm enable failed pkg=$packageName exitCode=${enable.exitCode}")
        }
        val result = AppRootAccessFacade.runRootCommand(
            "am start --user $userId -W -n ${shellQuote(component)}",
            timeoutMs = FORCE_REGISTER_ROOT_LAUNCH_TIMEOUT_MS,
        )
        val output = (result.stdoutText + "\n" + result.stderrText).trim()
        val failed = output.contains("Error", ignoreCase = true) ||
            output.contains("Exception", ignoreCase = true)
        logI(
            "force register root launch pkg=$packageName component=$component " +
                "success=${result.isSuccess && !failed} exitCode=${result.exitCode} " +
                "timedOut=${result.timedOut}",
        )
        return result.isSuccess && !failed
    }

    private fun recordForceRegisterStage(
        packageName: String,
        info: String,
        result: Int = EventRowResultType.OK,
    ) {
        runCatching {
            runBlocking {
                EventDb.insertEventAsync(result, RegistrationType(info, packageName, null))
            }
        }.onFailure {
            logW("force register stage event write failed for $packageName info=$info error=${it.message}")
        }
    }

    private suspend fun forceRegisterWithFeedback(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        // launchTargetAppAndForceRegister already requested and verified root for this operation.
        // Do not trigger a second Magisk/KernelSU authorization request after launching the app.
        if (
            registeredType != ManagerApplication.RegisteredType.REGISTERED &&
            RegistrationStateCompat.hasLocalRegistrationArtifacts(packageName)
        ) {
            val wiped = runCatching { RegistrationHelper(context, packageName).removeMiPushData() }
                .getOrDefault(false)
            logI("force register: wiped app-side MiPush data for $packageName success=$wiped")
            recordForceRegisterStage(packageName, "force_register_wiped_app_data success=$wiped")
        }
        val dispatchStartedAt = System.currentTimeMillis()
        val result = runCatching { RegistrationHelper.tryForceRegister(packageName) }
        if (result.getOrDefault(false)) {
            return awaitForceRegisterResult(context, packageName, dispatchStartedAt)
        }
        val cause = result.exceptionOrNull()
        if (cause is NoClassDefFoundError || cause is ClassNotFoundException) {
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable),
            )
        }

        val fallbackStartedAt = System.currentTimeMillis()
        return if (runCatching { RegistrationHelper.tryForceRegisterFallback(packageName) }.getOrDefault(false)) {
            awaitForceRegisterResult(context, packageName, fallbackStartedAt)
        } else {
            ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        }
    }

    /**
     * "Dispatched" is not "registered". The old flow reported success as soon as the platform
     * accepted the intent, which is why the logs show `force register ... dispatched` for apps
     * that never sent a REGISTER_APP at all. Wait for the real outcome and say which one it was.
     */
    private suspend fun awaitForceRegisterResult(
        context: Context,
        packageName: String,
        startedAtMs: Long,
    ): ManagerForceRegisterResult {
        var registerRequestSeen = false
        val deadline = SystemClock.uptimeMillis() + FORCE_REGISTER_RESULT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val application = RegisteredApplicationDb.getRegisteredApplication(packageName)
            if (application?.registeredType == RegisteredAppRegisteredType.Registered) {
                recordForceRegisterStage(packageName, "force_register_confirmed")
                return ManagerForceRegisterResult(
                    succeeded = true,
                    message = context.getString(com.xiaomi.xmsf.R.string.force_register_confirmed),
                )
            }
            registerRequestSeen = registerRequestSeen || hasRegisterRequestSince(packageName, startedAtMs)
            delay(FORCE_REGISTER_RESULT_POLL_MS)
        }

        registerRequestSeen = registerRequestSeen || hasRegisterRequestSince(packageName, startedAtMs)
        val reason = if (registerRequestSeen) {
            "force_register_server_result_timeout"
        } else {
            "force_register_request_not_seen"
        }
        logW("force register result timeout pkg=$packageName reason=$reason")
        recordForceRegisterStage(
            packageName,
            "$reason timeoutMs=$FORCE_REGISTER_RESULT_TIMEOUT_MS",
            EventRowResultType.DENY_DISABLED,
        )
        return ManagerForceRegisterResult(
            succeeded = false,
            message = context.getString(com.xiaomi.xmsf.R.string.force_register_timeout),
        )
    }

    private suspend fun hasRegisterRequestSince(packageName: String, startedAtMs: Long): Boolean =
        EventDb.queryAsync(
            skip = 0,
            limit = 32,
            types = setOf(EventRowType.Registration),
            pkg = packageName,
            text = null,
        ).any { event ->
            event.date >= startedAtMs && event.info.isNullOrBlank()
        }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

}

internal fun transientRegisteredApplication(
    packageName: String,
    appName: String,
    userId: Int,
): RuntimeRegisteredApplicationRow = RuntimeRegisteredApplicationRow(
    id = null,
    packageName = packageName,
    userId = Utils.requireValidUserId(userId),
    type = RegisteredAppType.ALLOW,
    notificationOnRegister = true,
    registeredType = RegisteredAppRegisteredType.NotRegistered,
    appName = appName,
)

/** Applies presentation options while preserving fresh runtime and admission state. */
internal fun mergeEditableApplicationSettings(
    latest: RuntimeRegisteredApplicationRow,
    submitted: ManagerApplication,
): RuntimeRegisteredApplicationRow = latest.copy(
    notificationOnRegister = submitted.notificationOnRegister,
    islandEnabled = !latest.blocked && submitted.islandEnabled,
    islandFocusNotification = !latest.blocked && submitted.islandFocusNotification,
    clickFallbackEnabled = !latest.blocked && submitted.clickFallbackEnabled,
    suppressWhenDisabled = !latest.blocked && submitted.suppressWhenDisabled,
    countLimitExempt = !latest.blocked && submitted.countLimitExempt,
)
