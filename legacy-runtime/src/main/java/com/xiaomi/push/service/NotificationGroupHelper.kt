package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.notification.BuilderCompat
import com.xiaomi.xmpush.thrift.ConfigKey

object NotificationGroupHelper {
    private const val EXTRA_SRC_GROUP_NAME = "push_src_group_name"
    private const val EXTRA_SRC_GROUP_TIME = "push_src_group_time"
    private const val GROUP_MASK_FORMAT = "pushmask_%s_%s"
    private const val GROUP_SUMMARY_CHANNEL_ID = "groupSummary"
    private const val GROUP_SUMMARY_TITLE = "GroupSummary"

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
        if (!isSupportPlatform() || !isEnableLatestNotificationNotIntoGroup(context)) {
            return group
        }
        val now = System.currentTimeMillis()
        val extras = builder.getExtras()
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
        return (JavaCalls.callMethod(notification, "isGroupSummary") as? Boolean) ?: false
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
                .setContentTitle(GROUP_SUMMARY_TITLE)
                .setContentText(GROUP_SUMMARY_TITLE)
                .setSmallIcon(Icon.createWithResource(packageName, iconId))
                .setAutoCancel(true)
                .setGroup(group)
                .setGroupSummary(true)
                .build()
            if (!MIUIUtils.isXMS() && PushConstants.PUSH_SERVICE_PACKAGE_NAME == context.packageName) {
                NotificationUtils.setTargetPackage(summary, packageName)
            }
            if (!MIUIUtils.isGlobalRegion()) {
                summary.extras.putBoolean("miui.showAtTail", true)
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
            JavaCalls.callMethod(builder, "setGroupAlertBehavior", if (summary) 2 else 1)
            true
        } else {
            MyLog.i("not support setGroupAlertBehavior")
            false
        }
    }
}
