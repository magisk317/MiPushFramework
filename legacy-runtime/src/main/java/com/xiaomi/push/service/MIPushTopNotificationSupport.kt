package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.reflect.JavaCalls

internal object MIPushTopNotificationSupport {
    const val EXTRA_FREQUENCY = "notification_top_frequency"
    const val EXTRA_PERIOD = "notification_top_period"
    const val EXTRA_REPEAT = "notification_top_repeat"
    const val LOCAL_FLAG = "mipush_n_top_flag"
    const val LOCAL_FREQUENCY = "mipush_n_top_fre"
    const val LOCAL_PERIOD = "mipush_n_top_prd"
    private const val EXTRA_MESSAGE_ID = "message_id"

    @JvmStatic
    fun generateTopNotificationUpdateJobId(notificationId: Int, messageId: String): String {
        return ScheduledJobConstants.TOP_NOTIFICATION_UPDATE_JOB_ID + notificationId + "_" + messageId
    }

    @JvmStatic
    fun buildTopNotificationUpdateJob(
        context: Context,
        packageName: String,
        notificationId: Int,
        messageId: String,
        notification: Notification?,
    ): ScheduledJobManager.Job {
        return object : ScheduledJobManager.Job() {
            override fun getJobId(): String {
                return generateTopNotificationUpdateJobId(notificationId, messageId)
            }

            override fun run() {
                scheduleTopNotificationUpdate(context, packageName, notificationId, messageId, notification)
            }
        }
    }

    @JvmStatic
    fun getNotificationPeriod(extra: Map<String, String>?): Int {
        var period = 0
        val raw = extra?.get(EXTRA_PERIOD)
        if (!TextUtils.isEmpty(raw)) {
            try {
                MyLog.v("prd of top notification is $raw")
                period = raw?.toInt() ?: 0
            } catch (e: Exception) {
                MyLog.e("parsing top notification period error: $e")
            }
        }
        return maxOf(0, period)
    }

    @JvmStatic
    fun getNotificationUpdateFrequency(extra: Map<String, String>?): Int {
        var frequency = 0
        val raw = extra?.get(EXTRA_FREQUENCY)
        if (!TextUtils.isEmpty(raw)) {
            try {
                MyLog.v("fre of top notification is $raw")
                frequency = raw?.toInt() ?: 0
            } catch (e: Exception) {
                MyLog.e("parsing top notification frequency error: $e")
            }
        }
        return maxOf(0, frequency)
    }

    @JvmStatic
    fun shouldRepeatSetTopAndToCommon(extra: Map<String, String>?): Boolean {
        var repeat = true
        val raw = extra?.get(EXTRA_REPEAT)
        if (!TextUtils.isEmpty(raw)) {
            repeat = raw.toBoolean()
            MyLog.v("top notification' repeat is $repeat")
        }
        return repeat
    }

    @JvmStatic
    fun onNotificationRemoved(context: Context, statusBarNotification: StatusBarNotification?) {
        if (statusBarNotification == null || Build.VERSION.SDK_INT < 23 || !NotificationUtils.isNotificationFromXmsf(context, statusBarNotification)) {
            return
        }
        NotificationGroupHelper.getInstance().onNotificationRemoved(context, statusBarNotification)
        val notification = statusBarNotification.notification ?: return
        val messageId = notification.extras.getString(EXTRA_MESSAGE_ID)
        val notificationId = statusBarNotification.id
        if (!notification.extras.getBoolean(LOCAL_FLAG, false) || TextUtils.isEmpty(messageId)) {
            return
        }
        ScheduledJobManager.getInstance(context).cancelJob(generateTopNotificationUpdateJobId(notificationId, messageId!!))
    }

    @JvmStatic
    fun scheduleTopNotificationUpdate(
        context: Context?,
        packageName: String?,
        notificationId: Int,
        messageId: String?,
        sourceNotification: Notification?,
    ) {
        if (context == null || TextUtils.isEmpty(packageName) || TextUtils.isEmpty(messageId) || Build.VERSION.SDK_INT < 26) {
            return
        }
        val manager = NotificationManagerHelper.from(context, packageName!!)
        var notification = sourceNotification
        var justPosted = sourceNotification != null
        if (notification == null) {
            val activeNotifications = manager.getActiveNotifications() ?: return
            notification = null
            justPosted = false
            for (active in activeNotifications) {
                val current = active.notification
                val currentMessageId = current.extras.getString(EXTRA_MESSAGE_ID)
                if (notificationId == active.id && messageId == currentMessageId) {
                    notification = current
                    break
                }
            }
        } else if (messageId != notification.extras.getString(EXTRA_MESSAGE_ID)) {
            return
        }
        notification ?: return

        if (notification.groupAlertBehavior != 1) {
            JavaCalls.setField(notification, "mGroupAlertBehavior", 1)
        }
        val now = System.currentTimeMillis()
        val createdAt = notification.extras.getLong(MIPushNotificationHelper.NOTIFICATION_LOCAL_EXTRA_CREATE_TIME_LONG, 0L)
        val frequency = notification.extras.getInt(LOCAL_FREQUENCY, 0)
        var period = notification.extras.getInt(LOCAL_PERIOD, 0)
        if (period <= 0 || period < frequency) {
            return
        }
        val expireAt = createdAt + period * 1000L
        if (createdAt >= now || now >= expireAt) {
            period = 0
        } else if (frequency > 0) {
            val nextDelay = minOf((expireAt - now) / 1000L, frequency.toLong()).toInt()
            period = nextDelay
            if (nextDelay > 0 && !justPosted) {
                notification.`when` = now
                MyLog.w("update top notification: $messageId")
                manager.notify(notificationId, notification)
            }
        }
        if (period > 0) {
            MyLog.w("schedule top notification next update delay: $period")
            ScheduledJobManager.getInstance(context).cancelJob(generateTopNotificationUpdateJobId(notificationId, messageId!!))
            ScheduledJobManager.getInstance(context).addOneShootJob(
                buildTopNotificationUpdateJob(context, packageName, notificationId, messageId, null),
                period,
            )
            return
        }

        val defaultChannelId = manager.getMipushChannelId(NotificationManagerHelper.DEFAULT_ID)
        if (manager.getNotificationChannel(defaultChannelId) == null) {
            manager.createNotificationChannel(NotificationChannel(defaultChannelId, AppInfoUtils.getAppLabel(context, packageName), 3))
        }
        val builder = Notification.Builder.recoverBuilder(context, notification)
        builder.setChannelId(defaultChannelId)
        JavaCalls.callMethod(builder, "setPriority", 0)
        MyLog.w("update top notification to common: $messageId")
        manager.notify(notificationId, builder.build())
    }
}
