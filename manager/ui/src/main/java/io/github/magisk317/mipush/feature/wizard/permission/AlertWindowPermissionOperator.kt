package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride

class AlertWindowPermissionOperator(private val context: Context) : PermissionOperator {
    override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean {
        return Settings.canDrawOverlays(context)
    }

    override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean {
        val gateway = permissionGateway ?: return false
        return gateway.launchAppOps(
            context,
            AppOpsManagerOverride.OPSTR_SYSTEM_ALERT_WINDOW,
            context.getString(R.string.wizard_title_alert_window_text)
        )
    }

    override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
