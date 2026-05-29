package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.common.utils.Singleton
import org.koin.core.context.GlobalContext

object ManagerGatewayAccess {
    inline fun <reified T : Any> get(): T {
        return GlobalContext.getOrNull()?.get<T>() ?: Singleton.instance()
    }
}
