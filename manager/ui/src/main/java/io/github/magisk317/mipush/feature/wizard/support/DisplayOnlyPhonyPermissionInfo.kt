package io.github.magisk317.mipush.feature.wizard.support

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.PermissionOperator

abstract class DisplayOnlyPhonyPermissionInfo(
    protected val context: Context
) : PermissionInfo, PermissionOperator {
    override val permissionOperator: PermissionOperator
        get() = this

    override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean {
        return true
    }

    override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean {
        return false
    }

    override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
    }
}
