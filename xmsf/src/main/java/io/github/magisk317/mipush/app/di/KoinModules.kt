package io.github.magisk317.mipush.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.xiaomi.push.sdk.PushMessageProcessor
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerNotificationGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.config.ConfigCatalogService
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.config.ConfigSyncRepository
import io.github.magisk317.mipush.config.ConfigSyncStateStore
import io.github.magisk317.mipush.config.LocalConfigRepository
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.DatabaseUtils
import io.github.magisk317.mipush.runtime.store.db.AppDatabase
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.MiPushEventListener
import io.github.magisk317.mipush.push.hook.ModernHookHandler
import io.github.magisk317.mipush.service.RegistrationRecorder
import io.github.magisk317.mipush.utils.ConfigValueConverter
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.ConfigurationsLoader
import io.github.magisk317.mipush.utils.IconConfigurations
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.reflect.KClass

val xmsfCoreKoinModule = module {
    single<DataStore<Preferences>> { androidContext().dataStore }
    single<AppDatabase> { DatabaseUtils.getDatabase(androidContext()) }
    single { get<AppDatabase>().eventDao() }
    single { get<AppDatabase>().registeredApplicationDao() }

    single { PreferenceRepository(get()) }
    single { ConfigCenter(get()) }
    single<ManagerConfigGateway> { XmsfManagerConfigGateway(get()) }
    single<ManagerConfigSyncGateway> { XmsfManagerConfigSyncGateway(get(), get()) }
    single<ManagerApplicationGateway> { XmsfManagerApplicationGateway(get()) }
    single<ManagerNotificationGateway> { XmsfManagerNotificationGateway() }
    single<ManagerEventGateway> { XmsfManagerEventGateway(androidContext(), get()) }
    single<ManagerLogGateway> { XmsfManagerLogGateway() }
    single<ManagerPermissionGateway> { XmsfManagerPermissionGateway() }
    single { ConfigurationsLoader(get()) }
    single { Configurations(get()) }
    single { IconConfigurations(get()) }
    single { ConfigValueConverter() }
    single { ModernHookHandler() }
    single { MiPushEventListener() }
    single { RegistrationRecorder() }
    single { RuntimeSettingsAdapter(androidContext(), get()) }
    single<ManagerRuntimeActions> { XmsfManagerRuntimeActions(get(), get()) }
    single { PushMessageProcessor(get()) }
    single { ConfigSyncStateStore(androidContext()) }
    single { LocalConfigRepository(androidContext()) }
    single { ConfigCatalogService(get()) }
    single { ConfigSyncRepository(get(), get(), get(), get()) }
    single { ConfigNavigationHelper(androidContext(), get(), get()) }
    single { EventRepository(androidContext(), get(), get(), get()) }
}

object AppDependencies {
    @Synchronized
    fun start(context: android.content.Context) {
        if (GlobalContext.getOrNull() != null) {
            return
        }
        val appContext = context.applicationContext ?: context
        startKoin {
            androidContext(appContext)
            modules(xmsfCoreKoinModule)
        }
    }

    inline fun <reified T : Any> get(context: android.content.Context): T {
        start(context)
        return GlobalContext.get().get()
    }

    fun <T : Any> get(klass: KClass<T>): T = GlobalContext.get().get(klass)
}
