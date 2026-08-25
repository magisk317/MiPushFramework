package io.github.magisk317.mipush.feature.wizard.permission

import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway

interface PermissionOperator {
    suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway? = null): Boolean
    suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway? = null): Boolean
    suspend fun requestPermission(permissionGateway: ManagerPermissionGateway? = null)
}
