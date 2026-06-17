package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.platform.service.PushServiceAccessibility

class RequestIgnoreBatteryOptimizationsPermissionOperator(
    private val context: Context
) : PermissionOperator {
    override fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean {
        return PushServiceAccessibility.isInDozeWhiteList(context)
    }

    override fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean {
        val gateway = permissionGateway ?: return false
        return gateway.requestIgnoreBatteryOptimizations(context)
    }

    override fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
}
