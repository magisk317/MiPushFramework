package io.github.magisk317.mipush.feature.wizard.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.magisk317.mipush.app.di.ManagerGatewayAccess
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway

class NotificationPermissionOperator(private val context: Context) : PermissionOperator {
    private val permissionGateway: ManagerPermissionGateway
        get() = ManagerGatewayAccess.get()

    override fun isPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun requestPermissionSilently(): Boolean {
        return permissionGateway.grantNotificationPermission(context)
    }

    override fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
