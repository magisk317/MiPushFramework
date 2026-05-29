package io.github.magisk317.mipush.feature.wizard.permission

import io.github.magisk317.mipush.app.di.ManagerGatewayAccess
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway

class RootPermissionOperator : PermissionOperator {
    private val permissionGateway: ManagerPermissionGateway
        get() = ManagerGatewayAccess.get()

    override fun isPermissionGranted(): Boolean = permissionGateway.hasCachedRootAccess()

    override fun requestPermissionSilently(): Boolean = false

    override fun requestPermission() {
        permissionGateway.requestRootAccess()
    }
}
