package top.trumeet.common.ita.impl

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import top.trumeet.common.R
import top.trumeet.common.ita.ITopActivity
import top.trumeet.common.override.ActivityManagerOverride

/**
 * Created by zts1993 on 2018/2/18.
 */
class ActivityUsageStatsImpl : ITopActivity {

    override fun isEnabled(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "", e)
            false
        }
    }

    override fun guideToEnable(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    override fun isAppForeground(context: Context, packageName: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val level = ActivityManagerOverride.getPackageImportance(packageName, am)
            level == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } catch (e: RuntimeException) {
            Toast.makeText(context, R.string.error_usage_stats, Toast.LENGTH_LONG).show()
            false
        }
    }

    companion object {
        private const val TAG = "ActivityUsageStatsImpl"
    }
}
