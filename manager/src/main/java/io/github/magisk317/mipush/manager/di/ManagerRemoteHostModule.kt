package io.github.magisk317.mipush.manager.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerNotificationGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ZygiskConfigGateway
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.configuration.ConfigCatalogService
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.configuration.ConfigSyncStateStore
import io.github.magisk317.mipush.configuration.LocalConfigRepository
import io.github.magisk317.mipush.manager.configuration.sync.LocalManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.remote.RemoteManagerApplicationGateway
import io.github.magisk317.mipush.manager.remote.RemoteManagerConfigGateway
import io.github.magisk317.mipush.manager.remote.RemoteManagerEventGateway
import io.github.magisk317.mipush.manager.remote.RemoteManagerLogGateway
import io.github.magisk317.mipush.manager.remote.RemoteManagerNotificationGateway
import io.github.magisk317.mipush.manager.remote.RemoteManagerPermissionGateway
import io.github.magisk317.mipush.manager.remote.RemoteManagerRuntimeActions
import io.github.magisk317.mipush.manager.remote.RemoteZygiskConfigGateway
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import io.github.magisk317.mipush.manager.root.ManagerRootAccess

/**
 * Host-side dependencies for the standalone `:mipush` manager process.
 * Runtime state is reached only through [ManagerRuntimeClient] / Binder.
 */
val managerRemoteHostModule = module {
    single<DataStore<Preferences>> { androidContext().dataStore }
    single { PreferenceRepository(get()) }

    single { ConfigSyncStateStore(androidContext()) }
    single { LocalConfigRepository(androidContext()) }
    single { ConfigCatalogService(get()) }
    single { ConfigSyncRepository(get(), get(), get(), get()) }
    single {
        LocalManagerConfigSyncGateway(
            context = androidContext(),
            syncRepository = get(),
            preferenceRepository = get(),
            client = get(),
        )
    }
    single<ManagerConfigSyncGateway> { get<LocalManagerConfigSyncGateway>() }

    single<ManagerApplicationGateway> { RemoteManagerApplicationGateway(get()) }
    single<ManagerEventGateway> { RemoteManagerEventGateway(androidContext(), get()) }
    single<ManagerNotificationGateway> { RemoteManagerNotificationGateway(get()) }
    single<ManagerLogGateway> { RemoteManagerLogGateway(client = get(), appContext = androidContext()) }
    single<ManagerConfigGateway> { RemoteManagerConfigGateway(get(), get()) }
    single<ManagerRuntimeActions> { RemoteManagerRuntimeActions(get()) }
    single { ManagerRootAccess() }
    single<ManagerPermissionGateway> { RemoteManagerPermissionGateway(androidContext(), get(), get()) }
    single<ZygiskConfigGateway> { RemoteZygiskConfigGateway(get()) }
}
