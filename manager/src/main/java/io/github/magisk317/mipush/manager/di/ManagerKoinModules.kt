package io.github.magisk317.mipush.manager.di

import io.github.magisk317.mipush.manager.logging.ManagerRuntimeFileLog

import android.content.Context
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
import io.github.magisk317.mipush.main.viewmodel.ZygiskConfigViewModel
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.application.ComparingApplicationDetailSource
import io.github.magisk317.mipush.manager.application.ComparingApplicationListSource
import io.github.magisk317.mipush.manager.application.InProcessApplicationDetailSource
import io.github.magisk317.mipush.manager.application.InProcessApplicationListSource
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.configuration.ComparingConfigurationCatalogSource
import io.github.magisk317.mipush.manager.configuration.RemoteConfigurationCatalogSource
import io.github.magisk317.mipush.manager.events.ComparingEventListSource
import io.github.magisk317.mipush.manager.events.InProcessEventListSource
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.logs.ComparingLogExportSource
import io.github.magisk317.mipush.manager.logs.InProcessLogExportSource
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.manager.notification.ComparingNotificationChannelSource
import io.github.magisk317.mipush.manager.notification.InProcessNotificationChannelSource
import io.github.magisk317.mipush.manager.notification.RemoteNotificationChannelSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.migration.ManagerPreferenceMigration
import io.github.magisk317.mipush.manager.launcher.LauncherIconController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.manager.connection.ComparingConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.InProcessConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
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
    single { InProcessConnectionSnapshotSource(get<SettingsManager>()) }
    single { RemoteConnectionSnapshotSource(get<ManagerRuntimeClient>()) }
    single {
        // Standalone manager is remote-only: primary must be Binder, not SettingsManager empty shell.
        ComparingConnectionSnapshotSource(
            inProcessSource = get<RemoteConnectionSnapshotSource>(),
            remoteSource = get<RemoteConnectionSnapshotSource>(),
            enableRemoteCompare = false,
        )
    }
    single { InProcessApplicationListSource(androidContext(), get<ManagerApplicationGateway>()) }
    single { RemoteApplicationListSource(get<ManagerRuntimeClient>()) }
    single {
        ComparingApplicationListSource(
            inProcessSource = get<InProcessApplicationListSource>(),
            remoteSource = get<RemoteApplicationListSource>(),
            // Primary already goes through RemoteManagerApplicationGateway; skip second remote pass.
            enableRemoteCompare = false,
        )
    }
    single { InProcessApplicationDetailSource(androidContext(), get<ManagerApplicationGateway>()) }
    single { RemoteApplicationDetailSource(get<ManagerRuntimeClient>()) }

    single { InProcessEventListSource(get()) }
    single { RemoteEventListSource(get<ManagerRuntimeClient>()) }
    single { ComparingEventListSource(get(), get(), enableRemoteCompare = false) }
    single { InProcessNotificationChannelSource(get()) }
    single { RemoteNotificationChannelSource(get<ManagerRuntimeClient>()) }
    single { ComparingNotificationChannelSource(get(), get()) }
    single { RemoteConfigurationCatalogSource(get<ManagerRuntimeClient>()) }
    single { ComparingConfigurationCatalogSource(get()) }
    single { InProcessLogExportSource(get()) }
    single { RemoteLogExportSource(get<ManagerRuntimeClient>()) }
    single { ComparingLogExportSource(get(), get()) }
    single {
        ComparingApplicationDetailSource(
            inProcessSource = get<InProcessApplicationDetailSource>(),
            remoteSource = get<RemoteApplicationDetailSource>(),
        )
    }

    viewModel { SettingsViewModel(get<PreferenceRepository>(), get<SettingsManager>(), get<ManagerPermissionGateway>()) }
    viewModel { EventListViewModel(get<ComparingEventListSource>(), get<ManagerEventGateway>(), get<SettingsManager>(), get<PreferenceRepository>(), androidContext(), get<ManagerRuntimeClient>()) }
    viewModel { ZygiskConfigViewModel(get<SettingsManager>(), get<ComparingApplicationListSource>()) }
    viewModel { ConfigManagerViewModel(get(), get(), get(), androidContext(), get()) }
    viewModel { ConfigEditorViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
    viewModel { ApplicationInfoViewModel(get(), get(), get(), get(), androidContext()) }
    viewModel { OverviewViewModel(get<ComparingApplicationListSource>(), get<ManagerRuntimeClient>()) }
    viewModel { ConnectionStatusViewModel(get<ComparingConnectionSnapshotSource>()) }
    viewModel {
        ApplicationListViewModel(
            get<ComparingApplicationListSource>(),
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

    /**
     * XMSF-packaged host path: the runtime Koin container must already expose manager gateways.
     */
    @Synchronized
    fun start(context: Context) {
        if (modulesLoaded) {
            return
        }
        requireHostKoin(context)
        loadKoinModules(managerKoinModule)
        modulesLoaded = true
    }

    /**
     * Standalone `:mipush` host path: start a manager-owned Koin container that reaches XMSF only
     * through the authenticated Binder client.
     */
    @Synchronized
    fun startAsRemoteHost(context: Context) {
        ManagerRuntimeFileLog.init(context)

        val appContext = context.applicationContext ?: context
        if (!modulesLoaded) {
            if (GlobalContext.getOrNull() == null) {
                startKoin {
                    androidContext(appContext)
                    modules(managerRemoteHostModule, managerKoinModule)
                }
            } else {
                loadKoinModules(listOf(managerRemoteHostModule, managerKoinModule))
            }
            modulesLoaded = true
        }
        val koin = GlobalContext.get()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    private fun requireHostKoin(context: Context) {
        if (GlobalContext.getOrNull() != null) {
            return
        }
        val process = runCatching { android.app.Application.getProcessName() }.getOrNull() ?: "unknown"
        error(
            "Koin host container is not started for manager dependencies " +
                "(process=$process, package=${context.packageName}). " +
                "MiPushFrameworkApp must call AppDependencies.start() before ManagerDependencies.start(), " +
                "or the mipush host must call ManagerDependencies.startAsRemoteHost()."
        )
    }
}
