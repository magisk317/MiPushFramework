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
    // Production manager is remote-only: gateway/Binder is the primary path and dual-source
    // compare stays off. Comparing* wrappers remain for ViewModel API stability.
    single {
        val remote = RemoteConnectionSnapshotSource(get<ManagerRuntimeClient>())
        ComparingConnectionSnapshotSource(
            inProcessSource = remote,
            remoteSource = remote,
            enableRemoteCompare = false,
        )
    }
    single {
        ComparingApplicationListSource(
            inProcessSource = InProcessApplicationListSource(
                androidContext(),
                get<ManagerApplicationGateway>(),
            ),
            remoteSource = RemoteApplicationListSource(get<ManagerRuntimeClient>()),
            enableRemoteCompare = false,
        )
    }
    single {
        ComparingApplicationDetailSource(
            inProcessSource = InProcessApplicationDetailSource(
                androidContext(),
                get<ManagerApplicationGateway>(),
            ),
            remoteSource = RemoteApplicationDetailSource(get<ManagerRuntimeClient>()),
            enableRemoteCompare = false,
        )
    }
    single {
        ComparingEventListSource(
            primarySource = InProcessEventListSource(get()),
            remoteSource = RemoteEventListSource(get<ManagerRuntimeClient>()),
            enableRemoteCompare = false,
        )
    }
    single {
        ComparingNotificationChannelSource(
            primarySource = InProcessNotificationChannelSource(get()),
            remoteSource = RemoteNotificationChannelSource(get<ManagerRuntimeClient>()),
            enableRemoteCompare = false,
        )
    }
    single {
        ComparingConfigurationCatalogSource(
            remoteSource = RemoteConfigurationCatalogSource(get<ManagerRuntimeClient>()),
            enableRemoteCompare = false,
        )
    }
    single {
        ComparingLogExportSource(
            primarySource = InProcessLogExportSource(get()),
            remoteSource = RemoteLogExportSource(get<ManagerRuntimeClient>()),
            enableRemoteCompare = false,
        )
    }

    viewModel {
        SettingsViewModel(
            get<PreferenceRepository>(),
            get<SettingsManager>(),
            get<ManagerPermissionGateway>(),
        )
    }
    viewModel {
        EventListViewModel(
            get<ComparingEventListSource>(),
            get<ManagerEventGateway>(),
            get<SettingsManager>(),
            get<PreferenceRepository>(),
            androidContext(),
            get<ManagerRuntimeClient>(),
        )
    }
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
    fun startAsRemoteHost(context: Context) {
        ManagerRuntimeFileLog.init(context)

        val appContext = context.applicationContext ?: context
        // Idempotent: activity/widget re-entry after App.onCreate must not restart Koin or re-connect.
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
}
