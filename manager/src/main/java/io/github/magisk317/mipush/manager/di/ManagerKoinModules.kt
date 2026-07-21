package io.github.magisk317.mipush.manager.di

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
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.connection.ComparingConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.InProcessConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
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
        ComparingConnectionSnapshotSource(
            inProcessSource = get<InProcessConnectionSnapshotSource>(),
            remoteSource = get<RemoteConnectionSnapshotSource>(),
        )
    }
    single { InProcessApplicationListSource(androidContext(), get<ManagerApplicationGateway>()) }
    single { RemoteApplicationListSource(get<ManagerRuntimeClient>()) }
    single {
        ComparingApplicationListSource(
            inProcessSource = get<InProcessApplicationListSource>(),
            remoteSource = get<RemoteApplicationListSource>(),
        )
    }
    single { InProcessApplicationDetailSource(androidContext(), get<ManagerApplicationGateway>()) }
    single { RemoteApplicationDetailSource(get<ManagerRuntimeClient>()) }
    single {
        ComparingApplicationDetailSource(
            inProcessSource = get<InProcessApplicationDetailSource>(),
            remoteSource = get<RemoteApplicationDetailSource>(),
        )
    }

    viewModel { SettingsViewModel(get<PreferenceRepository>(), get<SettingsManager>(), get<ManagerPermissionGateway>()) }
    viewModel { EventListViewModel(get<ManagerEventGateway>(), get<SettingsManager>(), get<PreferenceRepository>(), androidContext()) }
    viewModel { ZygiskConfigViewModel(get<SettingsManager>(), get<ComparingApplicationListSource>()) }
    viewModel { ConfigManagerViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
    viewModel { ConfigEditorViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
    viewModel {
        ApplicationInfoViewModel(
            get<ManagerApplicationGateway>(),
            get<ComparingApplicationDetailSource>(),
            get<SettingsManager>(),
            androidContext(),
        )
    }
    viewModel { OverviewViewModel(get<ComparingApplicationListSource>()) }
    viewModel { ConnectionStatusViewModel(get<ComparingConnectionSnapshotSource>()) }
    viewModel {
        ApplicationListViewModel(
            get<ComparingApplicationListSource>(),
            get<SettingsManager>(),
            get<PreferenceRepository>(),
            androidContext(),
        )
    }
    viewModel { RequestPermissionViewModel(get<ManagerPermissionGateway>(), get<PreferenceRepository>(), androidContext()) }
}

object ManagerDependencies {
    @Volatile
    private var modulesLoaded = false

    @Synchronized
    fun start(context: Context) {
        if (modulesLoaded) {
            return
        }
        requireHostKoin(context)
        loadKoinModules(managerKoinModule)
        modulesLoaded = true
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
                "MiPushFrameworkApp must call AppDependencies.start() before ManagerDependencies.start()."
        )
    }
}
