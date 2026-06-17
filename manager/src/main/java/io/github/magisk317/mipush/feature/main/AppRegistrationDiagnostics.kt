package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.common.manager.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway

object AppRegistrationDiagnosticsHelper {
    fun load(
        packageName: String,
        registeredType: Int,
        applicationGateway: ManagerApplicationGateway,
    ): ManagerApplicationDiagnostics {
        return applicationGateway.getDiagnostics(packageName, registeredType)
    }
}
