package io.github.magisk317.mipush.platform.activity.impl

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import io.github.magisk317.mipush.common.R
import io.github.magisk317.mipush.platform.activity.ITopActivity
import io.github.magisk317.mipush.platform.override.ActivityManagerOverride
import io.github.magisk317.mipush.platform.override.AppOpsManagerOverride

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
                AppOpsManagerOverride.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            if (isAllowedMode(mode)) {
                true
            } else {
                val rawMode = runCatching {
                    val method = AppOpsManager::class.java.getMethod(
                        "unsafeCheckOpRawNoThrow",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        String::class.java
                    )
                    method.invoke(
                        appOpsManager,
                        AppOpsManagerOverride.OPSTR_GET_USAGE_STATS,
                        applicationInfo.uid,
                        applicationInfo.packageName
                    ) as Int
                }.getOrNull()
                if (isAllowedMode(rawMode)) {
                    true
                } else {
                    val usageStatsManager =
                        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    val now = System.currentTimeMillis()
                    usageStatsManager?.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        now - ENABLE_CHECK_WINDOW_MS,
                        now
                    )?.isNotEmpty() == true
                }
            }
        } catch (e: ReflectiveOperationException) {
            Log.e(TAG, e.message ?: "", e)
            false
        } catch (e: SecurityException) {
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
            if (level == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true
            }

            queryForegroundPackage(context)?.let { foregroundPackage ->
                val matched = foregroundPackage == packageName
                if (matched) {
                    Log.w(
                        TAG,
                        "Falling back to UsageStatsManager foreground match for $packageName after getPackageImportance=$level"
                    )
                }
                matched
            } ?: false
        } catch (e: SecurityException) {
            Log.e(TAG, "isForeground: usage stats query failed", e)
            Toast.makeText(context, R.string.error_usage_stats, Toast.LENGTH_LONG).show()
            false
        }
    }

    companion object {
        private const val TAG = "ActivityUsageStatsImpl"
        private const val ENABLE_CHECK_WINDOW_MS = 24L * 60L * 60L * 1000L
        private const val FOREGROUND_EVENTS_BOOTSTRAP_WINDOW_MS = 5L * 60L * 1000L
        private const val FOREGROUND_EVENTS_OVERLAP_MS = 1_000L

        @Volatile
        private var lastQueryEndTime = 0L

        @Volatile
        private var lastForegroundPackage: String? = null

        @Volatile
        private var lastForegroundEventTime = 0L

        private fun isAllowedMode(mode: Int?): Boolean {
            return mode == AppOpsManagerOverride.MODE_ALLOWED ||
                mode == AppOpsManagerOverride.MODE_FOREGROUND ||
                mode == AppOpsManagerOverride.MODE_DEFAULT
        }

        @Synchronized
        private fun queryForegroundPackage(context: Context): String? {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val begin = when {
                lastQueryEndTime <= 0L -> now - FOREGROUND_EVENTS_BOOTSTRAP_WINDOW_MS
                now <= lastQueryEndTime -> now - FOREGROUND_EVENTS_BOOTSTRAP_WINDOW_MS
                now - lastQueryEndTime > FOREGROUND_EVENTS_BOOTSTRAP_WINDOW_MS ->
                    now - FOREGROUND_EVENTS_BOOTSTRAP_WINDOW_MS
                else -> (lastQueryEndTime - FOREGROUND_EVENTS_OVERLAP_MS).coerceAtLeast(0L)
            }

            val events = usageStatsManager.queryEvents(begin, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val packageName = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastForegroundPackage = packageName
                        lastForegroundEventTime = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (packageName == lastForegroundPackage && event.timeStamp >= lastForegroundEventTime) {
                            lastForegroundPackage = null
                            lastForegroundEventTime = event.timeStamp
                        }
                    }
                }
            }

            if (lastForegroundPackage == null && lastQueryEndTime <= 0L) {
                bootstrapForegroundFromUsageStats(usageStatsManager, begin, now)
            }

            lastQueryEndTime = now
            return lastForegroundPackage
        }

        private fun bootstrapForegroundFromUsageStats(
            usageStatsManager: UsageStatsManager,
            begin: Long,
            end: Long
        ) {
            val mostRecentlyUsed = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                begin,
                end
            ).maxByOrNull { usageStats ->
                usageStats.lastTimeUsed
            } ?: return
            if (mostRecentlyUsed.lastTimeUsed > 0L) {
                lastForegroundPackage = mostRecentlyUsed.packageName
                lastForegroundEventTime = mostRecentlyUsed.lastTimeUsed
            }
        }
    }
}
