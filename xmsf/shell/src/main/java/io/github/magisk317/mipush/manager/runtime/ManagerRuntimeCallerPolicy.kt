package io.github.magisk317.mipush.manager.runtime

import io.github.magisk317.mipush.manager.api.ManagerProtocol

internal object ManagerRuntimeCallerPolicy {
    fun isAllowed(
        callingUid: Int,
        runtimeUid: Int,
        callerPackages: Collection<String>,
        signaturesMatch: Boolean,
    ): Boolean {
        if (callingUid == runtimeUid) return true
        return signaturesMatch && ManagerProtocol.MANAGER_PACKAGE in callerPackages
    }
}
