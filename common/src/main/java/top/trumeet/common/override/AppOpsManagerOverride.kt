package top.trumeet.common.override

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.os.Build

/**
 * Created by Trumeet on 2018/2/5.
 */
object AppOpsManagerOverride {

    /** Control whether an application is allowed to run in the background.  */
    @TargetApi(Build.VERSION_CODES.N)
    const val OP_RUN_IN_BACKGROUND = 63 // AppOpsManager.OP_RUN_IN_BACKGROUND

    const val OP_POST_NOTIFICATION = 11 // AppOpsManager.OP_POST_NOTIFICATION

    /** Retrieve current usage stats via [android.app.usage.UsageStatsManager].  */
    const val OP_GET_USAGE_STATS = 43 // AppOpsManager.OP_GET_USAGE_STATS

    @JvmStatic
    fun checkOpNoThrow(
        op: Int,
        uid: Int,
        packageName: String,
        manager: AppOpsManager
    ): Int {
        return manager.checkOpNoThrow(op, uid, packageName)
    }
}
