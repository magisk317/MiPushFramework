package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.notification.BuilderCompat
import com.xiaomi.xmpush.thrift.ConfigKey

object NotificationGroupHelper {
    private const val EXTRA_SRC_GROUP_NAME = "push_src_group_name"
    private const val EXTRA_SRC_GROUP_TIME = "push_src_group_time"
    private const val GROUP_MASK_FORMAT = "pushmask_%s_%s"
    private const val GROUP_SUMMARY_CHANNEL_ID = "groupSummary"
    private const val GROUP_SUMMARY_TITLE = "GroupSummary"
    private const val GROUP_SUMMARY_VISIBLE_TITLE = "新消息"
    private const val GROUP_SUMMARY_VISIBLE_TEXT = "你有一条新消息"

    private var summaryTitle: SpannableString? = null

    private class AutoGroupItem(
        val childList: MutableList<NotificationInfo> = ArrayList(),
        val summaryList: MutableList<NotificationInfo> = ArrayList(),
    )

    private class NotificationInfo(
        val notifyId: Int,
        val notification: Notification,
    ) {
        override fun toString(): String = "id:$notifyId"
    }

    @JvmStatic
    fun getInstance(): NotificationGroupHelper = this

    fun maskGroup(context: Context, builder: BuilderCompat, group: String): String {
        return maskGroup(context, builder.getExtras(), group)
    }

    // Stock 7.4.67-C d1.h writes the source group into the builder extras before replacing
    // it with a one-shot mask. The Bundle overload lets the current framework Builder use
    // that same restore lifecycle instead of maintaining a second grouping implementation.
    fun maskGroup(context: Context, extras: Bundle, group: String): String {
        if (!isSupportPlatform() || !isEnableLatestNotificationNotIntoGroup(context)) {
            return group
        }
        val now = System.currentTimeMillis()
        extras.putString(EXTRA_SRC_GROUP_NAME, group)
        extras.putLong(EXTRA_SRC_GROUP_TIME, now)
        return GROUP_MASK_FORMAT.format(now, group)
    }

    fun onNotificationNotify(context: Context, notifyId: Int, notification: Notification) {
        if (!isSupportPlatform()) {
            return
        }
        if (isEnableLatestNotificationNotIntoGroup(context)) {
            runCatching { handleRestoreGroup(context, notifyId, notification) }
                .onFailure { MyLog.w("group notify handle restore error $it") }
        }
        if (isEnableNotificationAutoGroup(context)) {
            runCatching { handleAutoGroup(context, notifyId, notification, true) }
                .onFailure { MyLog.w("group notify handle auto error $it") }
        }
    }

    fun onNotificationRemoved(context: Context, statusBarNotification: StatusBarNotification) {
        if (isSupportPlatform() && isEnableNotificationAutoGroup(context) && statusBarNotification.notification != null) {
            runCatching { handleAutoGroup(context, statusBarNotification.id, statusBarNotification.notification, false) }
                .onFailure { MyLog.w("group remove handle auto error $it") }
        }
    }

    private fun cancelGroupSummary(context: Context, packageName: String, group: String) {
        MyLog.i("group cancel summary:$group")
        NotificationManagerHelper.from(context, packageName).cancel(getGroupSummaryNotifyId(packageName, group))
    }

    private fun getActiveNotifications(notificationManager: NotificationManagerHelper?): List<StatusBarNotification>? {
        val notifications = notificationManager?.getActiveNotifications()
        return if (notifications.isNullOrEmpty()) null else notifications
    }

    private fun getGroupSummaryNotifyId(packageName: String, group: String): Int {
        return (GROUP_SUMMARY_TITLE + packageName + group).hashCode()
    }

    private fun getSrcGroup(notification: Notification?): String? {
        return notification?.extras?.getString(EXTRA_SRC_GROUP_NAME)
    }

    private fun getTrueGroup(notification: Notification?): String? {
        if (notification == null) return null
        val group = notification.group
        return if (isMaskGroup(notification)) getSrcGroup(notification) else group
    }

    private fun handleAutoGroup(context: Context, notifyId: Int, notification: Notification, addCurrent: Boolean) {
        val targetPackage = NotificationUtils.getTargetPackage(notification)
        if (TextUtils.isEmpty(targetPackage)) {
            MyLog.w("group auto not extract pkg from notification:$notifyId")
            return
        }
        val activeNotifications = getActiveNotifications(NotificationManagerHelper.from(context, targetPackage!!))
        if (activeNotifications == null) {
            MyLog.w("group auto not get notifications")
            return
        }
        val currentGroup = getTrueGroup(notification)
        val groupedNotifications = HashMap<String?, AutoGroupItem>()
        activeNotifications.forEach { active ->
            if (active.notification != null && active.id != notifyId) {
                putAutoGroupItem(groupedNotifications, active)
            }
        }
        groupedNotifications.forEach { (groupKey, groupItem) ->
            if (!TextUtils.isEmpty(groupKey)) {
                if (addCurrent && groupKey == currentGroup && !isMaskGroup(notification)) {
                    val notificationInfo = NotificationInfo(notifyId, notification)
                    if (isGroupSummary(notification)) {
                        groupItem.summaryList.add(notificationInfo)
                    } else {
                        groupItem.childList.add(notificationInfo)
                    }
                }
                val childCount = groupItem.childList.size
                if (groupItem.summaryList.isEmpty()) {
                    if (addCurrent && childCount >= 2) {
                        showGroupSummary(context, targetPackage, groupKey!!, groupItem.childList.first().notification)
                    }
                } else if (childCount <= 0) {
                    cancelGroupSummary(context, targetPackage, groupKey!!)
                } else if (
                    // Stock 7.4.67-C d1.d refreshes the generated summary timestamp when
                    // config 141 is enabled. The 3.7.9 helper had no update branch, leaving
                    // an existing summary ordered at the time its first children arrived.
                    OnlineConfig.getInstance(context).getBooleanValue(
                        ConfigKey.NotificationGroupUpdateTimeSwitch.value,
                        false,
                    )
                ) {
                    groupItem.summaryList.firstOrNull()?.notification?.let { summary ->
                        summary.`when` = System.currentTimeMillis()
                        showGroupSummary(context, targetPackage, groupKey!!, summary)
                    }
                }
            }
        }
    }

    private fun handleRestoreGroup(context: Context, notifyId: Int, notification: Notification) {
        val targetPackage = NotificationUtils.getTargetPackage(notification)
        if (TextUtils.isEmpty(targetPackage)) {
            MyLog.w("group restore not extract pkg from notification:$notifyId")
            return
        }
        val notificationManager = NotificationManagerHelper.from(context, targetPackage!!)
        val activeNotifications = getActiveNotifications(notificationManager)
        if (activeNotifications == null) {
            MyLog.w("group restore not get notifications")
            return
        }
        activeNotifications.forEach { active ->
            val activeNotification = active.notification
            if (activeNotification != null && isMaskGroup(activeNotification) && active.id != notifyId) {
                val builder = Notification.Builder.recoverBuilder(context, activeNotification)
                builder.setGroup(getSrcGroup(activeNotification))
                suppressNotificationEffects(builder, isGroupSummary(activeNotification))
                notificationManager.notify(active.id, builder.build())
                MyLog.i("group restore notification:${active.id}")
            }
        }
    }

    private fun isEnableLatestNotificationNotIntoGroup(context: Context): Boolean {
        return isEnableNotificationAutoGroup(context) &&
            NotificationManagerHelper.isRomSupportNotificationBelongToApp(context) &&
            OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.LatestNotificationNotIntoGroupSwitch.value, false)
    }

    private fun isEnableNotificationAutoGroup(context: Context): Boolean {
        return OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.NotificationAutoGroupSwitch.value, true)
    }

    private fun isGroupSummary(notification: Notification?): Boolean {
        if (notification == null) {
            return false
        }
        return notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    private fun isMaskGroup(notification: Notification): Boolean {
        val group = notification.group ?: return false
        val extras = notification.extras ?: return false
        val timestamp = extras.getLong(EXTRA_SRC_GROUP_TIME)
        return group == GROUP_MASK_FORMAT.format(timestamp, getSrcGroup(notification))
    }

    private fun isSupportPlatform(): Boolean = Build.VERSION.SDK_INT >= 24

    private fun putAutoGroupItem(map: MutableMap<String?, AutoGroupItem>, statusBarNotification: StatusBarNotification) {
        val group = getTrueGroup(statusBarNotification.notification)
        val item = map.getOrPut(group) { AutoGroupItem() }
        val info = NotificationInfo(statusBarNotification.id, statusBarNotification.notification)
        if (isGroupSummary(statusBarNotification.notification)) {
            item.summaryList.add(info)
        } else {
            item.childList.add(info)
        }
    }

    private fun showGroupSummary(context: Context, packageName: String, group: String, notification: Notification) {
        val builder = BuilderCompat(context)
        try {
            if (TextUtils.isEmpty(group)) {
                MyLog.w("group show summary group is null")
                return
            }
            val iconId = NotificationUtils.getIdForSmallIconFromTargetPkg(context, packageName)
            if (iconId == 0) {
                MyLog.w("group show summary not get icon from $packageName")
                return
            }
            val notificationManager = NotificationManagerHelper.from(context, packageName)
            if (Build.VERSION.SDK_INT >= 26) {
                val channelId = notificationManager.getGroupSummaryChannelId(notification.channelId, GROUP_SUMMARY_CHANNEL_ID)
                val channel: NotificationChannel? = notificationManager.getNotificationChannel(channelId)
                if (channelId == GROUP_SUMMARY_CHANNEL_ID && channel == null) {
                    notificationManager.createNotificationChannel(NotificationChannel(channelId, "group_summary", 3))
                }
                builder.setChannelId(channelId)
            } else {
                builder.setPriority(0).setDefaults(-1)
            }
            suppressNotificationEffects(builder, true)
            val summary = builder
                // Stock 7.4.67-C d1.a replaced the 3.7.9 literal title and
                // miui.showAtTail flag with a user-visible title plus a transparent
                // GroupSummary suffix. Keep that internal marker without displaying it.
                .setContentTitle(getSummaryTitle(context))
                .setContentText(GROUP_SUMMARY_VISIBLE_TEXT)
                .setSmallIcon(Icon.createWithResource(packageName, iconId))
                .setAutoCancel(true)
                .setGroup(group)
                .setGroupSummary(true)
                .build()
            if (Build.VERSION.SDK_INT >= 31) {
                // Stock 7.4.67-C d1.k adds the target launch intent on S+. Without it,
                // tapping this synthesized summary has no destination on current Android.
                summary.contentIntent = getLaunchPendingIntent(context, packageName)
            }
            if (!MIUIUtils.isXMS() && PushConstants.PUSH_SERVICE_PACKAGE_NAME == context.packageName) {
                NotificationUtils.setTargetPackage(summary, packageName)
            }
            val summaryId = getGroupSummaryNotifyId(packageName, group)
            notificationManager.notify(summaryId, summary)
            MyLog.i("group show summary notify:$summaryId")
        } catch (e: Exception) {
            MyLog.w("group show summary error $e")
        }
    }

    private fun suppressNotificationEffects(builder: BuilderCompat, summary: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            builder.setGroupAlertBehavior(if (summary) 2 else 1)
            true
        } else {
            MyLog.i("not support setGroupAlertBehavior")
            false
        }
    }

    private fun suppressNotificationEffects(builder: Notification.Builder, summary: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            builder.setGroupAlertBehavior(if (summary) 2 else 1)
            true
        } else {
            MyLog.i("not support setGroupAlertBehavior")
            false
        }
    }

    private fun getLaunchPendingIntent(context: Context, packageName: String): PendingIntent? {
        return runCatching {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
        }.getOrNull()
    }

    private fun getSummaryTitle(context: Context): SpannableString {
        summaryTitle?.let { return it }
        val displayMetrics = context.resources?.displayMetrics
        val blankCount = displayMetrics
            ?.let { maxOf(it.heightPixels, it.widthPixels) }
            ?.takeIf { it > 0 }
            ?.div(16)
            ?: 200
        val text = buildString(GROUP_SUMMARY_VISIBLE_TITLE.length + blankCount + GROUP_SUMMARY_TITLE.length) {
            append(GROUP_SUMMARY_VISIBLE_TITLE)
            repeat(blankCount) { append(' ') }
            append(GROUP_SUMMARY_TITLE)
        }
        return SpannableString(text).also { title ->
            title.setSpan(
                ForegroundColorSpan(0),
                GROUP_SUMMARY_VISIBLE_TITLE.length,
                title.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            summaryTitle = title
        }
    }
}
