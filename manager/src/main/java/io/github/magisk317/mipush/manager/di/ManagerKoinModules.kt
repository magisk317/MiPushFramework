package io.github.magisk317.mipush.manager.di

import io.github.magisk317.mipush.manager.logging.ManagerRuntimeFileLog
import io.github.magisk317.xposed.logging.LogSanitizerConfig

import android.app.Application
import android.content.Context
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.VERSION_NAME
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
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
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.configuration.RemoteConfigurationCatalogSource
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.ConnectionReconnectRequester
import io.github.magisk317.mipush.manager.migration.ManagerPreferenceMigration
import io.github.magisk317.mipush.manager.launcher.LauncherIconController
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceGateway
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.uikit.shell.AppInitializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.RemoteConnectionReconnectRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
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
            get<io.github.magisk317.mipush.common.manager.ZygiskConfigGateway>(),
        )
    }
    single {
        ManagerRuntimeClient(
            context = androidContext(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).apply { connect() }
    }
    single { RuntimePreferenceGateway(get<ManagerRuntimeClient>(), get<PreferenceRepository>()) }
    // Production manager is remote-only: ViewModels consume Remote* sources directly.
    single { RemoteConnectionSnapshotSource(get<ManagerRuntimeClient>()) }
    single<ConnectionSnapshotSource> { get<RemoteConnectionSnapshotSource>() }
    single { RemoteConnectionReconnectRequester(get<ManagerRuntimeClient>()) }
    single<ConnectionReconnectRequester> { get<RemoteConnectionReconnectRequester>() }
    single { RemoteApplicationListSource(get<ManagerRuntimeClient>()) }
    single { RemoteApplicationDetailSource(get<ManagerRuntimeClient>()) }
    single { RemoteEventListSource(get<ManagerRuntimeClient>()) }
    single { RemoteNotificationChannelSource(get<ManagerRuntimeClient>()) }
    single { RemoteConfigurationCatalogSource(get<ManagerRuntimeClient>()) }
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
            androidContext(),
            get<ManagerRuntimeClient>(),
            get<RuntimePreferenceGateway>(),
        )
    }
    viewModel { ZygiskConfigViewModel(get<SettingsManager>(), get<RemoteApplicationListSource>(), get()) }
    viewModel { ConfigManagerViewModel(get(), get(), get(), androidContext(), get()) }
    viewModel { ConfigEditorViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
    viewModel { ApplicationInfoViewModel(get(), get(), get(), get(), get(), androidContext()) }
    viewModel { OverviewViewModel(get<RemoteApplicationListSource>(), get<ManagerRuntimeClient>(), get<PreferenceRepository>()) }
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
            androidContext(),
            get<ManagerRuntimeClient>(),
        )
    }
    viewModel { RequestPermissionViewModel(get<ManagerPermissionGateway>(), get<PreferenceRepository>(), androidContext()) }
}

object ManagerDependencies {
    @Volatile
    private var modulesLoaded = false

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

    /**
     * Preferred entry for manager UI surfaces. Always boots the remote-host Koin graph.
     * Safe to call repeatedly from activities after [startAsRemoteHost] in Application.
     */
    @Synchronized
    fun ensureStarted(context: Context) {
        startAsRemoteHost(context)
    }

    /**
     * Standalone `:mipush` host path: start a manager-owned Koin container that reaches XMSF only
     * through the authenticated Binder client.
     */
    @Synchronized
    fun startAsRemoteHost(context: Context, vararg hostModules: Module) {
        ManagerRuntimeFileLog.init(context)

        val appContext = context.applicationContext ?: context
        // Idempotent: activity/widget re-entry after App.onCreate must not restart Koin or re-connect.
        if (!modulesLoaded) {
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
            modulesLoaded = true
        } else if (!hostModulesLoaded && hostModules.isNotEmpty()) {
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
        appScope.launch(Dispatchers.IO) {
            val iconId = runCatching {
                koin.get<PreferenceRepository>().selectedLauncherIcon.first()
            }.getOrDefault(LauncherIconController.ICON_DEFAULT)
            LauncherIconController.apply(appContext, iconId)
        }
    }

    inline fun <reified T : Any> get(): T = GlobalContext.get().get()

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
