package io.github.magisk317.mipush.app.di

import org.koin.core.context.GlobalContext

/**
 * Resolves manager<->xmsf gateway implementations from the active Koin container.
 *
 * Gateways are registered in xmsfCoreKoinModule and started in
 * MiPushFrameworkApp.onCreate (per process), so a container is always available
 * wherever manager code runs.
 */
object ManagerGatewayAccess {
    inline fun <reified T : Any> get(): T {
        val koin = GlobalContext.getOrNull()
            ?: error("Koin is not started; cannot resolve ${T::class.simpleName}")
        return koin.get<T>()
    }
}
