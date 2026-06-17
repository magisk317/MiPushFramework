package io.github.magisk317.mipush.feature.wizard.permission

import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway

class RootPermissionOperator : PermissionOperator {
    override fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean {
        return permissionGateway?.hasCachedRootAccess() == true
    }

    override fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean = false

    override fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        permissionGateway?.requestRootAccess()
    }
}
