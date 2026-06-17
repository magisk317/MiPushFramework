package io.github.magisk317.mipush.feature.wizard.permission

import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway

interface PermissionOperator {
    fun isPermissionGranted(permissionGateway: ManagerPermissionGateway? = null): Boolean
    fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway? = null): Boolean
    fun requestPermission(permissionGateway: ManagerPermissionGateway? = null)
}
