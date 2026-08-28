package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.platform.activity.impl.ActivityAccessibilityImpl

class AccessibilityPermissionOperator(private val context: Context) : PermissionOperator {
    private val delegate = ActivityAccessibilityImpl()

    override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean = delegate.isEnabled(context)

    override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean = false

    override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
