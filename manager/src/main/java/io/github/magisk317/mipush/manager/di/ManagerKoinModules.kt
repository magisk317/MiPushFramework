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
import io.github.magisk317.mipush.main.viewmodel.AdvancedSettingsViewModel
import io.github.magisk317.mipush.main.viewmodel.ApplicationInfoViewModel
import io.github.magisk317.mipush.main.viewmodel.ApplicationListViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigEditorViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.main.viewmodel.OverviewViewModel
import io.github.magisk317.mipush.main.viewmodel.RequestPermissionViewModel
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.main.viewmodel.ZygiskConfigViewModel
import io.github.magisk317.mipush.manager.SettingsManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val managerKoinModule = module {
    single { SettingsManager(get<ManagerConfigGateway>(), get<ManagerRuntimeActions>(), get<ManagerLogGateway>(), get<io.github.magisk317.mipush.common.manager.ZygiskConfigGateway>()) }

    viewModel { SettingsViewModel(get<PreferenceRepository>(), get<SettingsManager>()) }
    viewModel { AdvancedSettingsViewModel(get<PreferenceRepository>(), get<SettingsManager>()) }
    viewModel { EventListViewModel(get<ManagerEventGateway>(), get<SettingsManager>(), androidContext()) }
    viewModel { ZygiskConfigViewModel(get(), get(), androidContext()) }
    viewModel { ConfigManagerViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
    viewModel { ConfigEditorViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
    viewModel { ApplicationInfoViewModel(get<ManagerApplicationGateway>(), get<SettingsManager>(), androidContext()) }
    viewModel { OverviewViewModel(get<ManagerApplicationGateway>(), androidContext()) }
    viewModel { ApplicationListViewModel(get<ManagerApplicationGateway>(), get<SettingsManager>(), androidContext()) }
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
