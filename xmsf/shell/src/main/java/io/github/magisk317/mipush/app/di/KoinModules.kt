package io.github.magisk317.mipush.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigSyncGateway
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerNotificationChannelCommandGateway
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.configuration.ConfigCatalogService
import io.github.magisk317.mipush.configuration.ConfigSyncObserver
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.configuration.ConfigSyncStateStore
import io.github.magisk317.mipush.configuration.LocalConfigRepository
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.common.notification.NotificationAvailabilityReader
import io.github.magisk317.mipush.notification.XmsfNotificationAvailabilityReader
import io.github.magisk317.mipush.runtime.store.DatabaseUtils
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeStoreDatabase
import io.github.magisk317.mipush.service.runtime.RuntimeProcessorBindings
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.MiPushEventListener
import io.github.magisk317.mipush.push.hook.ModernHookHandler
import io.github.magisk317.mipush.utils.ConfigValueConverter
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.ConfigurationsLoader
import io.github.magisk317.mipush.utils.IconConfigurations
import io.github.magisk317.xposed.logging.MagiskOtel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.reflect.KClass

val xmsfCoreKoinModule = module {
    single<DataStore<Preferences>> { androidContext().dataStore }
    single<RuntimeStoreDatabase> { DatabaseUtils.getDatabase(androidContext()) }
    single { get<RuntimeStoreDatabase>().eventDao() }
    single { get<RuntimeStoreDatabase>().deletedEventDao() }
    single { get<RuntimeStoreDatabase>().registeredApplicationDao() }

    single { PreferenceRepository(get()) }
    single { ConfigCenter(androidContext(), get()) }
    single<ManagerConfigGateway> { XmsfManagerConfigGateway(get(), get()) }
    single<ManagerConfigSyncGateway> { XmsfManagerConfigSyncGateway(get(), get()) }
    single<ManagerApplicationGateway> { XmsfManagerApplicationGateway() }
    single<ManagerNotificationChannelCommandGateway> { XmsfManagerNotificationChannelCommandGateway() }
    single<ManagerEventGateway> { XmsfManagerEventGateway(androidContext(), get()) }
    single<ManagerLogGateway> { XmsfManagerLogGateway() }
    single<ManagerPermissionGateway> { XmsfManagerPermissionGateway() }
    single<io.github.magisk317.mipush.common.manager.ZygiskConfigGateway> { XmsfZygiskConfigGateway() }
    single { ConfigurationsLoader(get()) }
    single { Configurations(get()) }
    single { IconConfigurations(get()) }
    single { ConfigValueConverter() }
    single { ModernHookHandler() }
    single { MiPushEventListener() }
    single { RuntimeProcessorBindings.createPushMessageProcessor(get()) }
    single { RuntimeSettingsAdapter(androidContext(), get(), get()) }
    single<ManagerRuntimeActions> { XmsfManagerRuntimeActions(get()) }
    single<ConfigSyncObserver> {
        ConfigSyncObserver { count ->
            MagiskOtel.event(
                name = "push.control",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "config_sync",
                    "reason" to "upsert_all",
                    "found_count" to count.toString(),
                ),
                statusOk = true,
            )
        }
    }
    single { ConfigSyncStateStore(androidContext(), get()) }
    single { LocalConfigRepository(androidContext()) }
    single { ConfigCatalogService(get()) }
    single { ConfigSyncRepository(get(), get(), get(), get()) }
    single { ConfigNavigationHelper(androidContext(), get(), get()) }
    single<NotificationAvailabilityReader> { XmsfNotificationAvailabilityReader(androidContext()) }
    single { EventRepository(androidContext(), get(), get(), get(), get()) }
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
