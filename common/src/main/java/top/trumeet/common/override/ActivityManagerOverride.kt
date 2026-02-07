package top.trumeet.common.override

import android.app.ActivityManager
import androidx.annotation.RequiresPermission

/**
 * Created by Trumeet on 2018/2/18.
 */
object ActivityManagerOverride {
    @JvmStatic
    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    fun getPackageImportance(packageName: String, manager: ActivityManager): Int {
        return try {
            val method = ActivityManager::class.java.getMethod("getPackageImportance", String::class.java)
            (method.invoke(manager, packageName) as? Int) ?: ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
        } catch (_: Throwable) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
        }
    }
}
