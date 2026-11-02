package io.github.magisk317.mipush.feature.wizard.permission

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.platform.activity.impl.ActivityAccessibilityImpl
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride
import io.github.magisk317.xposed.permission.hasUsageStatsAccessViaAppOps

class UsageStatsPermissionOperator(private val context: Context) : PermissionOperator {
    @SuppressLint("MissingPermission")
    override suspend fun isPermissionGranted(permissionGateway: ManagerPermissionGateway?): Boolean {
        val uid = context.applicationInfo.uid
        val packageName = context.packageName
        // Platform AppOps layers live in the shared kit probe now; the requirement-specific
        // fallback chain (accessibility proxy, usage query, root gateway) stays below.
        if (context.hasUsageStatsAccessViaAppOps(uid, packageName)) return true

        if (ActivityAccessibilityImpl().isEnabled(context)) return true

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 24L * 60L * 60L * 1000L,
            now
        ).isNotEmpty() || isGrantedByShell(packageName, permissionGateway)
    }

    private suspend fun isGrantedByShell(packageName: String, permissionGateway: ManagerPermissionGateway?): Boolean {
        return permissionGateway?.isUsageStatsAllowedByRoot(packageName) == true
    }

    private fun isAllowedMode(mode: Int?): Boolean {
        return mode == AppOpsManagerOverride.MODE_ALLOWED ||
            mode == AppOpsManagerOverride.MODE_FOREGROUND ||
            mode == AppOpsManagerOverride.MODE_DEFAULT
    }

    override suspend fun requestPermissionSilently(permissionGateway: ManagerPermissionGateway?): Boolean {
        val gateway = permissionGateway ?: return false
        return gateway.launchAppOps(
            context,
            AppOpsManagerOverride.OPSTR_GET_USAGE_STATS,
            context.getString(R.string.wizard_title_stats_permission_text)
        )
    }

    override suspend fun requestPermission(permissionGateway: ManagerPermissionGateway?) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}
