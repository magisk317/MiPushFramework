package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.app.SettingsManager
import io.github.magisk317.mipush.feature.main.ApplicationIconCache
import io.github.magisk317.mipush.main.viewmodel.AdvancedSettingsViewModel
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val managerKoinModule = module {
    single { SettingsManager(get(), get()) }
    single { ApplicationIconCache(androidContext()) }

    viewModelOf(::SettingsViewModel)
    viewModelOf(::AdvancedSettingsViewModel)
    viewModelOf(::EventListViewModel)
}
