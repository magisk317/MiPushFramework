package io.github.magisk317.mipush.feature.wizard.permission

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.platform.activity.impl.ActivityAccessibilityImpl
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.ShellUtils

class UsageStatsPermissionOperator(private val context: Context) : PermissionOperator {
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
        val commands = listOf(
            "appops get $packageName GET_USAGE_STATS",
            "appops get $packageName android:get_usage_stats",
            "cmd appops get $packageName GET_USAGE_STATS"
        )
        return commands.any { command ->
            val result = ShellUtils.execCmd(command, true, true)
            val output = buildString {
                append(result.successMsg.orEmpty())
                append('\n')
                append(result.errorMsg.orEmpty())
            }
            output.contains("GET_USAGE_STATS: allow", ignoreCase = true) ||
                output.contains("android:get_usage_stats: allow", ignoreCase = true)
        }
    }

    private fun isAllowedMode(mode: Int?): Boolean {
        return mode == AppOpsManagerOverride.MODE_ALLOWED ||
            mode == AppOpsManagerOverride.MODE_FOREGROUND ||
            mode == AppOpsManagerOverride.MODE_DEFAULT
    }

    override fun requestPermissionSilently(): Boolean {
        return PermissionUtils.lunchAppOps(
            context,
            AppOpsManagerOverride.OPSTR_GET_USAGE_STATS,
            context.getString(R.string.wizard_title_stats_permission_text)
        )
    }

    override fun requestPermission() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}
