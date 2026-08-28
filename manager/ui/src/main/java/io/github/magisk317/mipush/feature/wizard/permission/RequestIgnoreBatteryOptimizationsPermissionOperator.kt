package io.github.magisk317.mipush.feature.wizard.permission

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.platform.service.PushServiceAccessibility

class RequestIgnoreBatteryOptimizationsPermissionOperator(
    private val context: Context
) : PermissionOperator {
    override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean {
        return PushServiceAccessibility.isInDozeWhiteList(context)
    }

    override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean {
        val gateway = permissionGateway ?: return false
        return gateway.requestIgnoreBatteryOptimizations(context)
    }

    // Called only by the permission wizard's explicit user action for the XMSF push process.
    @SuppressLint("BatteryLife")
    override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        // XMSF owns the push process and is the package whose Doze state is checked.
        intent.data = Uri.parse("package:${Constants.SERVICE_APP_NAME}")
        context.startActivity(intent)
    }
}
