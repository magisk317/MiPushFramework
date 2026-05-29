package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.magisk317.mipush.app.di.ManagerGatewayAccess
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride

class AlertWindowPermissionOperator(private val context: Context) : PermissionOperator {
    private val permissionGateway: ManagerPermissionGateway
        get() = ManagerGatewayAccess.get()

    override fun isPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    override fun requestPermissionSilently(): Boolean {
        return permissionGateway.launchAppOps(
            context,
            AppOpsManagerOverride.OPSTR_SYSTEM_ALERT_WINDOW,
            context.getString(R.string.wizard_title_alert_window_text)
        )
    }

    override fun requestPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
