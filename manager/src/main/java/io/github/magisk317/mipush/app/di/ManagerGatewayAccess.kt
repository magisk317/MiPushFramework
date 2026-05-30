package io.github.magisk317.mipush.app.di

import android.app.Application
import android.os.Process
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
            ?: error(koinNotStartedMessage(T::class.qualifiedName ?: T::class.simpleName))
        return koin.get<T>()
    }

    @PublishedApi
    internal fun koinNotStartedMessage(typeName: String?): String {
        val process = runCatching { Application.getProcessName() }.getOrNull() ?: "unknown"
        return "Koin is not started; cannot resolve $typeName " +
            "(process=$process, pid=${Process.myPid()}). " +
            "Koin must be started in MiPushFrameworkApp.onCreate via AppDependencies.start() " +
            "before any gateway access."
    }
}
