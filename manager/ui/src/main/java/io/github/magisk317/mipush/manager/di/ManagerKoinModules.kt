package io.github.magisk317.mipush.manager.di

import io.github.magisk317.mipush.manager.logging.ManagerRuntimeFileLog
import io.github.magisk317.xposed.logging.LogSanitizerConfig

import android.app.Application
import android.content.Context
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.VERSION_NAME
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.main.viewmodel.ApplicationInfoViewModel
import io.github.magisk317.mipush.main.viewmodel.ApplicationListViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigEditorViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.main.viewmodel.OverviewViewModel
import io.github.magisk317.mipush.main.viewmodel.ConnectionStatusViewModel
import io.github.magisk317.mipush.main.viewmodel.RequestPermissionViewModel
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.main.viewmodel.XmppServerViewModel
import io.github.magisk317.mipush.main.viewmodel.ZygiskConfigViewModel
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.events.EventListCacheStore
import io.github.magisk317.mipush.manager.events.EventListBackgroundSyncCoordinator
import io.github.magisk317.mipush.manager.events.EventListCacheStoreRegistry
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.application.ApplicationListCacheStore
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.configuration.RemoteConfigurationCatalogSource
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelSource
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelCommand
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeRecovery
import io.github.magisk317.mipush.manager.client.DefaultManagerRuntimeCallScheduler
import io.github.magisk317.mipush.manager.client.ManagerRuntimeCallScheduler
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.ConnectionReconnectRequester
import io.github.magisk317.mipush.manager.migration.ManagerPreferenceMigration
import io.github.magisk317.mipush.manager.root.ManagerRootAccess
import io.github.magisk317.mipush.manager.remote.PageRemoteCallAdapter
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceGateway
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.uikit.shell.AppInitializer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.RemoteConnectionReconnectRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val managerKoinModule = module {
    single {
        SettingsManager(
            get<ManagerRuntimeActions>(),
            get<ManagerLogGateway>(),
            get<io.github.magisk317.mipush.manager.application.ZygiskConfigGateway>(),
        )
    }
    single {
        val rootAccess = get<ManagerRootAccess>()
        ManagerRuntimeClient(
            context = androidContext(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            runtimeRecovery = ManagerRuntimeRecovery(rootAccess::recoverXmsfForUser),
        ).apply { connect() }
    }
    single { RuntimePreferenceGateway(get<ManagerRuntimeClient>(), get<PreferenceRepository>(), get<ManagerRuntimeCallScheduler>()) }
    single<ManagerRuntimeCallScheduler> {
        DefaultManagerRuntimeCallScheduler(
            availabilityProvider = { get<ManagerRuntimeClient>().availability.value },
        )
    }
    single { PageRemoteCallAdapter(get<ManagerRuntimeCallScheduler>()) }
    // Production manager is remote-only: ViewModels consume Remote* sources directly.
    single { RemoteConnectionSnapshotSource(get<ManagerRuntimeClient>()) }
    single<ConnectionSnapshotSource> { get<RemoteConnectionSnapshotSource>() }
    single { RemoteConnectionReconnectRequester(get<ManagerRuntimeClient>()) }
    single<ConnectionReconnectRequester> { get<RemoteConnectionReconnectRequester>() }
    single { RemoteApplicationListSource(get<ManagerRuntimeClient>(), get<PageRemoteCallAdapter>()) }
    single { RemoteApplicationDetailSource(get<ManagerRuntimeClient>()) }
    single { RemoteEventListSource(get<ManagerRuntimeClient>(), get<PageRemoteCallAdapter>()) }
    single { RemoteNotificationChannelSource(get<ManagerRuntimeClient>()) }
    single { RemoteNotificationChannelCommand(get<ManagerRuntimeClient>()) }
    single { RemoteConfigurationCatalogSource(get<ManagerRuntimeClient>(), get<PageRemoteCallAdapter>()) }
    single { RemoteLogExportSource(get<ManagerRuntimeClient>()) }

    viewModel {
        SettingsViewModel(
            get<PreferenceRepository>(),
            get<SettingsManager>(),
            get<ManagerPermissionGateway>(),
            get<RuntimePreferenceGateway>(),
            get<ManagerRuntimeClient>(),
        )
    }
    viewModel { XmppServerViewModel(get<ManagerConfigGateway>()) }
    viewModel {
        EventListViewModel(
            get<RemoteEventListSource>(),
            get<ManagerEventGateway>(),
            get<SettingsManager>(),
            get<PreferenceRepository>(),
            androidApplication(),
            get<ManagerRuntimeClient>(),
            get<RuntimePreferenceGateway>(),
            get<EventListCacheStore>(),
        )
    }
    single { EventListCacheStoreRegistry.get(androidContext()) }
    single { ApplicationListCacheStore(androidContext()) }
    single {
        EventListBackgroundSyncCoordinator(
            context = androidContext(),
            source = get<RemoteEventListSource>(),
            cacheStore = get<EventListCacheStore>(),
            parentScope = get<ManagerRuntimeClient>().scopeForBackgroundWork(),
        )
    }
    viewModel { ZygiskConfigViewModel(get<SettingsManager>(), get<RemoteApplicationListSource>(), get()) }
    viewModel { ConfigManagerViewModel(get(), get(), get(), androidApplication(), get()) }
    viewModel { ConfigEditorViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidApplication()) }
    viewModel { ApplicationInfoViewModel(get(), get(), get(), get(), get(), get(), androidApplication()) }
    viewModel { OverviewViewModel(get<RemoteApplicationListSource>(), get<ManagerRuntimeClient>(), get<PreferenceRepository>(), get<ApplicationListCacheStore>()) }
    viewModel {
        ConnectionStatusViewModel(
            get<ConnectionSnapshotSource>(),
            get<ConnectionReconnectRequester>(),
        )
    }
    viewModel {
        ApplicationListViewModel(
            get<RemoteApplicationListSource>(),
            get<SettingsManager>(),
            get<PreferenceRepository>(),
            androidApplication(),
            get<ManagerRuntimeClient>(),
            get<ApplicationListCacheStore>(),
        )
    }
    viewModel { RequestPermissionViewModel(get<ManagerPermissionGateway>(), get<PreferenceRepository>()) }
}

object ManagerDependencies {
    private enum class BootstrapMode {
        APP_SHELL,
        REMOTE_HOST,
    }

    @Volatile
    private var bootstrapMode: BootstrapMode? = null

    @Volatile
    private var hostModulesLoaded = false

    @Volatile
    private var appInitializersStarted = false

    @Volatile
    private var logSanitizationSyncStarted = false

    @Volatile
    private var runtimePreferenceSyncStarted = false

    @Volatile
    private var analyticsSyncStarted = false

    @Volatile
    private var maintenanceSyncStarted = false

    @Volatile
    private var standaloneEventSyncStarted = false

    /** Load manager definitions into the Koin host already created by MiPushFrameworkApp. */
    @Synchronized
    fun startFromAppShell(context: Context) {
        ManagerRuntimeFileLog.init(context)
        when (bootstrapMode) {
            BootstrapMode.APP_SHELL -> return
            BootstrapMode.REMOTE_HOST -> error("Manager dependencies already use the remote host")
            null -> Unit
        }

        requireAppShellKoin(context)
        loadKoinModules(managerKoinModule)
        bootstrapMode = BootstrapMode.APP_SHELL
    }

    fun onMaintenanceTick(sequence: Long, action: String) {
        if (!maintenanceSyncStarted) {
            synchronized(this) {
                if (!maintenanceSyncStarted) {
                    maintenanceSyncStarted = true
                }
            }
        }
        if (bootstrapMode != BootstrapMode.APP_SHELL) return
        GlobalContext.getOrNull()?.get<EventListBackgroundSyncCoordinator>()
            ?.onMaintenanceTick(sequence, action)
    }

    /**
     * Standalone `:mipush` host path: start a manager-owned Koin container that reaches XMSF only
     * through the authenticated Binder client.
     */
    @Synchronized
    fun startAsRemoteHost(context: Context, vararg hostModules: Module) {
        ManagerRuntimeFileLog.init(context)

        val appContext = context.applicationContext ?: context
        when (bootstrapMode) {
            BootstrapMode.APP_SHELL -> error("Manager dependencies already use the app-shell host")
            BootstrapMode.REMOTE_HOST -> Unit
            null -> {
                val initialModules = listOf(managerRemoteHostModule, managerKoinModule) + hostModules
                if (GlobalContext.getOrNull() == null) {
                    startKoin {
                        androidContext(appContext)
                        modules(initialModules)
                    }
                } else {
                    loadKoinModules(initialModules)
                }
                hostModulesLoaded = hostModules.isNotEmpty()
                bootstrapMode = BootstrapMode.REMOTE_HOST
            }
        }
        if (!hostModulesLoaded && hostModules.isNotEmpty()) {
            loadKoinModules(hostModules.toList())
            hostModulesLoaded = true
        }
        val koin = GlobalContext.get()
        if (!appInitializersStarted && hostModulesLoaded) {
            val application = appContext as? Application
            if (application != null) {
                appInitializersStarted = true
                koin.getAll<AppInitializer>().forEach { initializer ->
                    initializer.init(application)
                }
            }
        }
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        if (!logSanitizationSyncStarted) {
            logSanitizationSyncStarted = true
            appScope.launch {
                koin.get<PreferenceRepository>().isLogSanitizationEnabled
                    .catch { LogSanitizerConfig.syncSanitizationEnabled(null) }
                    .collect { LogSanitizerConfig.syncSanitizationEnabled(it) }
            }
        }
        if (!runtimePreferenceSyncStarted) {
            runtimePreferenceSyncStarted = true
            koin.get<RuntimePreferenceGateway>().startReconnectSync(appScope)
        }
        if (!analyticsSyncStarted) {
            analyticsSyncStarted = true
            appScope.launch {
                koin.get<PreferenceRepository>().isAnalyticsEnabled.collect { enabled ->
                    configureAnalytics(appContext, enabled)
                }
            }
        }
        ManagerPreferenceMigration.schedule(
            scope = appScope,
            client = koin.get(),
            preferenceRepository = koin.get(),
        )
        if (!standaloneEventSyncStarted) {
            standaloneEventSyncStarted = true
            koin.get<EventListBackgroundSyncCoordinator>().startStandalonePeriodicRefresh()
        }
    }

    inline fun <reified T : Any> get(): T = GlobalContext.get().get()

    private fun requireAppShellKoin(context: Context) {
        if (GlobalContext.getOrNull() != null) {
            return
        }
        val process = runCatching { Application.getProcessName() }.getOrNull() ?: "unknown"
        error(
            "Koin host is not ready for app-shell manager startup " +
                "(process=$process, package=${context.packageName}). " +
                "MiPushFrameworkApp must start AppDependencies before invoking the host hook.",
        )
    }

    private fun configureAnalytics(context: Context, enabled: Boolean) {
        val systemOtelEnabled =
            System.getProperty("magisk.otel.enabled")?.equals("true", ignoreCase = true) == true
        MagiskOtel.configureForInstallation(
            context,
            MagiskOtel.Config(
                enabled = BuildConfig.DEBUG || enabled || systemOtelEnabled,
                serviceName = "mipushframework",
                serviceVersion = VERSION_NAME,
                projectId = "83955143",
                projectName = "MiPushFramework",
                environment = if (BuildConfig.DEBUG) "debug" else "release",
            ),
        )
    }
}
