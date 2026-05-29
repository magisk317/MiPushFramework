package io.github.magisk317.mipush.feature.wizard.permission

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.magisk317.mipush.app.di.ManagerGatewayAccess
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.platform.activity.impl.ActivityAccessibilityImpl
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride

class UsageStatsPermissionOperator(private val context: Context) : PermissionOperator {
    private val permissionGateway: ManagerPermissionGateway
        get() = ManagerGatewayAccess.get()

    override fun isPermissionGranted(): Boolean {
        val uid = context.applicationInfo.uid
        val packageName = context.packageName
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(AppOpsManagerOverride.OPSTR_GET_USAGE_STATS, uid, packageName)
        if (isAllowedMode(mode)) return true

        val rawMode = runCatching {
            val method = AppOpsManager::class.java.getMethod(
                "unsafeCheckOpRawNoThrow",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.invoke(appOps, AppOpsManagerOverride.OPSTR_GET_USAGE_STATS, uid, packageName) as Int
        }.getOrNull()
        if (isAllowedMode(rawMode)) return true

        if (ActivityAccessibilityImpl().isEnabled(context)) return true

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 24L * 60L * 60L * 1000L,
            now
        ).isNotEmpty() || isGrantedByShell(packageName)
    }

    private fun isGrantedByShell(packageName: String): Boolean {
        return permissionGateway.isUsageStatsAllowedByRoot(packageName)
    }

    private fun isAllowedMode(mode: Int?): Boolean {
        return mode == AppOpsManagerOverride.MODE_ALLOWED ||
            mode == AppOpsManagerOverride.MODE_FOREGROUND ||
            mode == AppOpsManagerOverride.MODE_DEFAULT
    }

    override fun requestPermissionSilently(): Boolean {
        return permissionGateway.launchAppOps(
            context,
            AppOpsManagerOverride.OPSTR_GET_USAGE_STATS,
            context.getString(R.string.wizard_title_stats_permission_text)
        )
    }

    override fun requestPermission() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}
