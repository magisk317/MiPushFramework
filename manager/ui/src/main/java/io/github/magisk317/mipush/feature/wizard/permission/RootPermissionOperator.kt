package io.github.magisk317.mipush.feature.wizard.permission

import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway

class RootPermissionOperator : PermissionOperator {
    override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean {
        return permissionGateway?.hasCachedRootAccess() == true
    }

    override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean {
        return permissionGateway?.refreshRootAccessIfGranted() == true
    }

    override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        permissionGateway?.requestRootAccess()
    }
}
