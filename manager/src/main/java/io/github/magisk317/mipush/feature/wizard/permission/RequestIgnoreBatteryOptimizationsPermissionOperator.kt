package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.magisk317.mipush.app.di.ManagerGatewayAccess
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.platform.service.PushServiceAccessibility

class RequestIgnoreBatteryOptimizationsPermissionOperator(
    private val context: Context
) : PermissionOperator {
    private val permissionGateway: ManagerPermissionGateway
        get() = ManagerGatewayAccess.get()

    override fun isPermissionGranted(): Boolean {
        return PushServiceAccessibility.isInDozeWhiteList(context)
    }

    override fun requestPermissionSilently(): Boolean {
        return permissionGateway.requestIgnoreBatteryOptimizations(context)
    }

    override fun requestPermission() {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
}
