package io.github.magisk317.mipush.app.di

import android.content.Context
import io.github.magisk317.mipush.app.SettingsManager
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.main.ApplicationIconCache
import io.github.magisk317.mipush.main.viewmodel.AdvancedSettingsViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigEditorViewModel
import io.github.magisk317.mipush.main.viewmodel.ConfigManagerViewModel
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.dsl.module

val managerKoinModule = module {
    single { SettingsManager(get<ManagerConfigGateway>(), get<ManagerRuntimeActions>(), get<ManagerApplicationGateway>(), get<ManagerLogGateway>()) }
    single { ApplicationIconCache(androidContext()) }

    viewModel { SettingsViewModel(get<PreferenceRepository>(), get<SettingsManager>()) }
    viewModel { AdvancedSettingsViewModel(get<PreferenceRepository>(), get<SettingsManager>()) }
    viewModel { EventListViewModel(get<ManagerEventGateway>(), get<SettingsManager>(), androidContext()) }
    viewModel { ConfigManagerViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
    viewModel { ConfigEditorViewModel(get<PreferenceRepository>(), get<ManagerConfigSyncGateway>(), get<ManagerConfigGateway>(), androidContext()) }
}

object ManagerDependencies {
    @Volatile
    private var modulesLoaded = false

    @Synchronized
    fun start(context: Context) {
        if (modulesLoaded) {
            return
        }
        val appContext = context.applicationContext ?: context
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(appContext)
                modules(managerKoinModule)
            }
        } else {
            loadKoinModules(managerKoinModule)
        }
        modulesLoaded = true
    }
}
