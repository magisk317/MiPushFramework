package io.github.magisk317.mipush.feature.main

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.content.edit
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge

internal object WelcomeIslandNotifier {
    private const val PREFS_NAME = "mipush_welcome_island"
    private const val KEY_LAST_NOTIFIED_UPDATE_TIME = "last_notified_update_time"

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ACTION_SHOW_ISLAND = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    private const val NOTIFICATION_ID = 0x4d495057

    fun notifyAfterInstallOrUpdate(context: Context) {
        val appContext = context.applicationContext ?: context
        val lastUpdateTime = appContext.currentInstallUpdateTime() ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedUpdateTime = prefs.getLong(KEY_LAST_NOTIFIED_UPDATE_TIME, 0L)
        if (!shouldNotifyForInstall(lastUpdateTime, lastNotifiedUpdateTime)) return

        prefs.edit {
            putLong(KEY_LAST_NOTIFIED_UPDATE_TIME, lastUpdateTime)
        }
        appContext.sendBroadcast(appContext.createWelcomeIslandIntent())
    }

    internal fun shouldNotifyForInstall(
        lastUpdateTime: Long,
        lastNotifiedUpdateTime: Long,
    ): Boolean {
        return lastUpdateTime > 0L && lastUpdateTime != lastNotifiedUpdateTime
    }

    private fun Context.currentInstallUpdateTime(): Long? {
        return runCatching {
            PackageManagerCompatBridge.getPackageInfo(packageManager, packageName, 0).lastUpdateTime
        }.getOrNull()
    }

    private fun Context.createWelcomeIslandIntent(): Intent {
        return Intent(ACTION_SHOW_ISLAND).apply {
            setPackage(SYSTEM_UI_PACKAGE)
            putExtra("title", getString(R.string.welcome_island_title))
            putExtra("content", getString(R.string.welcome_island_content))
            putExtra("icon", Icon.createWithResource(this@createWelcomeIslandIntent, R.mipmap.ic_app))
            putExtra("notificationId", NOTIFICATION_ID)
            putExtra("timeoutSecs", 5)
            putExtra("firstFloat", false)
            putExtra("enableFloat", false)
            putExtra("showNotification", false)
            putExtra("sourcePackage", packageName)
            putExtra("sourceChannelId", "mipush_welcome")
            putExtra("contentIntent", createContentIntent())
            putExtra("isOngoing", false)
            putExtra("showIslandIcon", true)
            putExtra("clearBeforePost", true)
        }
    }

    private fun Context.createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
