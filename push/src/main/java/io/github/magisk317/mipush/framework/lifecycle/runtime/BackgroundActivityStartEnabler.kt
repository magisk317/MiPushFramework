package io.github.magisk317.mipush.framework.lifecycle.runtime

import android.app.Notification
import android.app.Notification.GROUP_ALERT_SUMMARY
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.service.ForegroundHelper.Companion.CHANNEL_STATUS

@RequiresApi(29)
object BackgroundActivityStartEnabler {

    private var whitelistedNotification: Notification? = null
    private const val TAG = "MPF.BAFE"

    @JvmStatic
    fun clonePendingIntentForBackgroundActivityStart(pi: PendingIntent): PendingIntent? {
        val source = whitelistedNotification ?: return null
        source.contentIntent = pi
        val parcel = Parcel.obtain()
        try {
            source.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val copied = Notification.CREATOR.createFromParcel(parcel)
            val whitelisted = copied.contentIntent
            copied.contentIntent = null
            return whitelisted
        } finally {
            parcel.recycle()
            source.contentIntent = null
        }
    }

    @JvmStatic
    fun initialize(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = tryGetValidPushStatusChannelId(context, nm) ?: return
        notifyPushStatusInitializing(context, channelId, nm)
        scheduleCapture(nm, 5)
    }

    private fun notifyPushStatusInitializing(
        context: Context,
        channelId: String,
        nm: NotificationManager
    ) {
        val n = Notification.Builder(context, channelId)
            .setTimeoutAfter(5_000)
            .setContentTitle("Initializing...")
            .setOngoing(true)
            .setGroup(TAG)
            .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .build()
        nm.notify(TAG, 0, n)
    }

    private fun tryGetValidPushStatusChannelId(
        context: Context,
        nm: NotificationManager
    ): String? {
        var channelId = CHANNEL_STATUS
        var channelPostfix = 0
        while (true) {
            var channel = nm.getNotificationChannel(channelId)
            if (channel == null) {
                if (CHANNEL_STATUS == channelId) {
                    channelId = CHANNEL_STATUS + (++channelPostfix)
                    continue
                }
                channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.notification_category_alive),
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
                break
            } else {
                if (channel.importance > NotificationManager.IMPORTANCE_NONE) break
                if (channelPostfix == 16) {
                    Log.e(TAG, "Failed to obtain available notification channel.")
                    return null
                }
                channelId = CHANNEL_STATUS + (++channelPostfix)
            }
        }
        return channelId
    }

    private fun scheduleCapture(nm: NotificationManager, retries: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            val notifications = nm.activeNotifications
            findPushStatusInitializingNotification(nm, notifications)
            if (pushStatusInitializingNotificationExists()) {
                deleteTemporaryChannel(nm)
            } else if (retries == 0) {
                Log.e(TAG, "Failed to capture active notification.")
                nm.cancel(TAG, 0)
            } else {
                Log.i(TAG, "Wait to capture active notification.")
                scheduleCapture(nm, retries - 1)
            }
        }, 500)
    }

    private fun deleteTemporaryChannel(nm: NotificationManager) {
        val channelId = whitelistedNotification?.channelId ?: return
        if (CHANNEL_STATUS != channelId) {
            nm.deleteNotificationChannel(channelId)
        }
    }

    private fun pushStatusInitializingNotificationExists(): Boolean = whitelistedNotification != null

    private fun findPushStatusInitializingNotification(
        nm: NotificationManager,
        notifications: Array<StatusBarNotification>
    ) {
        for (notification in notifications) {
            if (notification.id == 0 && TAG == notification.tag) {
                whitelistedNotification = notification.notification
                nm.cancel(TAG, 0)
                break
            }
        }
    }
}
