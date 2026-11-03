package io.github.magisk317.mipush.feature.wizard.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootTarget

class LiveUpdatePermissionOperator(private val context: Context) : PermissionOperator {
    override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean =
        permissionGateway?.isLiveUpdatePermissionSupported() == true &&
            permissionGateway.isLiveUpdatePermissionGranted(context)

    override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean =
        permissionGateway?.grantLiveUpdatePermission(context) == true

    override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        val runtimeRootGranted = try {
            permissionGateway?.requestRootAccess(ManagerRootTarget.RUNTIME)?.runtime?.isGranted == true
        } catch (_: Exception) {
            false
        }
        if (runtimeRootGranted) {
            val granted = try {
                permissionGateway?.grantLiveUpdatePermission(context) == true
            } catch (_: Exception) {
                false
            }
            if (granted) return
        }
        val promotionIntent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ManagerProtocol.RUNTIME_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(promotionIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, ManagerProtocol.RUNTIME_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
