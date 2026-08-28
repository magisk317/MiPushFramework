package io.github.magisk317.mipush.app.di

import android.content.Context
import android.net.Uri
import android.os.Process
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.utils.DiagnosticExportModes
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
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
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.ShellUtils
import io.github.magisk317.mipush.runtime.PushRuntime
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.Locale

class XmsfManagerApplicationGateway : ManagerApplicationGateway {

    override suspend fun loadApplications(
        context: Context,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ): ManagerApplications {
        val timer = ElapsedTimer()
        val registered = RegisteredApplicationDb.getList(null)
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
        val apps = enrichedApps
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
                )
            }
            .filter { ManagerApplicationReadPolicy.matchesQuery(it, query) }
            .filter { ManagerApplicationReadPolicy.matchesFilter(it, filterMode) }
            .sortedWith(ManagerApplicationReadPolicy.comparator)
            .toList()
        Logger.withTag("XmsfManagerApplicationGateway").d {
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
                )
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
            application = RuntimeRegisteredApplicationRow(
                id = null,
                packageName = packageName,
                type = RegisteredAppType.ASK,
                notificationOnRegister = true,
                registeredType = RegisteredAppRegisteredType.NotRegistered,
                appName = Global.applicationNameCache().getAppName(context, packageName).toString(),
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
        )
    }

    override suspend fun updateApplication(application: ManagerApplication) {
        RegisteredApplicationDb.update(application.toRegisteredApplicationRow())
    }

    override suspend fun getDiagnostics(packageName: String, registeredType: Int): ManagerApplicationDiagnostics {
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
        )
    }

    override suspend fun launchTargetAppAndForceRegister(
        context: Context,
        packageName: String,
        registeredType: Int,
    ): ManagerForceRegisterResult {
        // Force-register must actively request elevation (KSU/Magisk prompt).
        // refreshRootAccessIfGranted() returns false when grant state is still unknown.
        if (!PermissionUtils.requestRootAccess()) {
            Logger.withTag("XmsfManagerApplicationGateway").w {
                "force-register aborted: root not granted pkg=$packageName"
            }
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_requires_root),
            )
        }
        val plan = RegistrationHelper.inspectForceRegisterPlan(packageName)
        if (!plan.supportsServiceDispatch && !plan.supportsReceiverFallback && plan.bridgeCandidates.isEmpty()) {
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable),
            )
        }
        runCatching {
            io.github.magisk317.mipush.platform.support.AppRootAccessFacade.runRootCommand("am force-stop $packageName")
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (runCatching { context.startActivity(launchIntent) }.isFailure) {
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        }
        kotlinx.coroutines.delay(500)
        return forceRegisterWithFeedback(context, packageName, registeredType)
    }

    private fun forceRegisterWithFeedback(
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
            runCatching { RegistrationHelper(context, packageName).removeMiPushData() }
        }
        val result = runCatching { RegistrationHelper.tryForceRegister(packageName) }
        if (result.getOrDefault(false)) {
            return ManagerForceRegisterResult(
                succeeded = true,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_sent),
            )
        }
        val cause = result.exceptionOrNull()
        if (cause is NoClassDefFoundError || cause is ClassNotFoundException) {
            return ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_unavailable),
            )
        }
        return if (runCatching { RegistrationHelper.tryForceRegisterFallback(packageName) }.getOrDefault(false)) {
            ManagerForceRegisterResult(
                succeeded = true,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_sent),
            )
        } else {
            ManagerForceRegisterResult(
                succeeded = false,
                message = context.getString(com.xiaomi.xmsf.R.string.force_register_failed),
            )
        }
    }

}
