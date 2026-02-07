package top.trumeet.mipushframework.wizard.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.xiaomi.xmsf.R
import top.trumeet.common.override.AppOpsManagerOverride
import top.trumeet.common.utils.Utils
import top.trumeet.mipushframework.utils.PermissionUtils

class UsageStatsPermissionOperator(private val context: Context) : PermissionOperator {
    override fun isPermissionGranted(): Boolean {
        val result = Utils.checkOp(context, AppOpsManagerOverride.OP_GET_USAGE_STATS)
        return result == AppOpsManager.MODE_ALLOWED
    }

    override fun requestPermissionSilently() {
        PermissionUtils.lunchAppOps(
            context,
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            context.getString(R.string.wizard_title_stats_permission_text)
        )
    }

    override fun requestPermission() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}
