package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.app.di.ManagerGatewayAccess
import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway

object AppRegistrationDiagnosticsHelper {
    fun load(
        packageName: String,
        registeredType: Int
    ): ManagerApplicationDiagnostics {
        val gateway: ManagerApplicationGateway = ManagerGatewayAccess.get()
        return gateway.getDiagnostics(packageName, registeredType)
    }
}
