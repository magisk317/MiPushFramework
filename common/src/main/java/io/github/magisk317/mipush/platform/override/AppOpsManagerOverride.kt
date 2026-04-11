package io.github.magisk317.mipush.platform.override

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.os.Build

/**
 * Created by Trumeet on 2018/2/5.
 */
object AppOpsManagerOverride {
    const val MODE_ALLOWED = 0
    const val MODE_IGNORED = 1
    const val MODE_ERRORED = 2
    const val MODE_DEFAULT = 3
    const val MODE_FOREGROUND = 4

    /** Control whether an application is allowed to run in the background. */
    @TargetApi(Build.VERSION_CODES.N)
    const val OP_RUN_IN_BACKGROUND = 63

    const val OP_POST_NOTIFICATION = 11

    /** Retrieve current usage stats via [android.app.usage.UsageStatsManager]. */
    const val OP_GET_USAGE_STATS = 43

    const val OPSTR_GET_USAGE_STATS = "android:get_usage_stats"
    const val OPSTR_SYSTEM_ALERT_WINDOW = "android:system_alert_window"

    @JvmStatic
    fun checkOpNoThrow(
        op: Int,
        uid: Int,
        packageName: String,
        manager: AppOpsManager
    ): Int {
        return runCatching {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.invoke(manager, op, uid, packageName) as Int
        }.getOrElse {
            manager.checkOpNoThrow(opToPublicString(op), uid, packageName)
        }
    }

    private fun opToPublicString(op: Int): String = when (op) {
        OP_GET_USAGE_STATS -> OPSTR_GET_USAGE_STATS
        OP_POST_NOTIFICATION -> "android:post_notification"
        else -> throw IllegalArgumentException("Unsupported app-op: $op")
    }
}
