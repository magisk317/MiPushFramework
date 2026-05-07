package io.github.magisk317.mipush.hook.xmsf.nm

import android.app.*
import android.os.Build
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XposedHelpers
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.callMethod
import io.github.magisk317.mipush.xposed.callStaticMethod
import io.github.magisk317.mipush.xposed.setField
import org.lsposed.hiddenapibypass.HiddenApiBypass

object SystemNotificationManager {
    private const val TAG = "SystemNotificationManager"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    private val notificationManager: Any = NotificationManager::class.java.callStaticMethod("getService")!!

    private fun getUid(packageName: String): Int {
        return AndroidAppHelper.currentApplication().packageManager.getPackageUid(packageName, 0)
    }

    private fun getUserId(): Int {
        return AndroidAppHelper.currentApplication().callMethod("getUserId") as Int? ?: 0
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ) {
        XLog.d(TAG, "notify() called with: packageName = $packageName, tag = $tag, id = $id, notification = $notification")

        //enqueueNotificationWithTag(String pkg, String opPkg, String tag, int id, Notification notification, int userId)
        val methodEnqueueNotificationWithTag = XposedHelpers.findMethodExact(notificationManager.javaClass, "enqueueNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Notification::class.java, Int::class.java)
        val opPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ANDROID_PACKAGE_NAME else packageName
        methodEnqueueNotificationWithTag.invoke(notificationManager, packageName, opPkg, tag, id, notification, getUserId())
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        XLog.d(TAG, "cancel() called with: packageName = $packageName, tag = $tag, id = $id")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
            val methodCancelNotificationWithTag = XposedHelpers.findMethodExact(notificationManager.javaClass, "cancelNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java)
            methodCancelNotificationWithTag.invoke(notificationManager, packageName, ANDROID_PACKAGE_NAME, tag, id, getUserId())
        } else {
            //  public void cancelNotificationWithTag(String pkg, String tag, int id, int userId)
            val methodCancelNotificationWithTag = XposedHelpers.findMethodExact(notificationManager.javaClass, "cancelNotificationWithTag", String::class.java, String::class.java, Int::class.java, Int::class.java)
            methodCancelNotificationWithTag.invoke(notificationManager, packageName, tag, id, getUserId())
        }
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel>
    ) {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channels = $channels")

        val channelsList = XposedHelpers.findConstructorExact("android.content.pm.ParceledListSlice", null, List::class.java)
            .newInstance(channels)
        notificationManager.callMethod("createNotificationChannelsForPackage", packageName, getUid(packageName), channelsList)
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channelId = $channelId")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, String conversationId, boolean includeDeleted);
            XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java)
                .invoke(notificationManager, packageName, getUid(packageName), channelId, null, false) as NotificationChannel?
        } else {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, boolean includeDeleted);
            XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, Boolean::class.java)
                .invoke(notificationManager, packageName, getUid(packageName), channelId, false) as NotificationChannel?
        }
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        XLog.d(TAG, "getNotificationChannels() called with: packageName = $packageName")
        //ParceledListSlice getNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted);
        val parceledListSlice = XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .invoke(notificationManager, packageName, getUid(packageName), false)
        @Suppress("UNCHECKED_CAST")
        val list = parceledListSlice?.callMethod("getList") as List<NotificationChannel?>?
        return list
    }

    fun findPreferredTargetChannel(
        packageName: String,
        preferredChannelId: String?
    ): NotificationChannel? {
        val channels = getNotificationChannels(packageName)
            .orEmpty()
            .filterNotNull()
            .filter { it.importance != NotificationManager.IMPORTANCE_NONE }
        if (channels.isEmpty()) {
            return null
        }
        if (!preferredChannelId.isNullOrEmpty()) {
            channels.firstOrNull { it.id == preferredChannelId }?.let { return it }
        }
        channels.firstOrNull { it.id == NotificationChannel.DEFAULT_CHANNEL_ID }?.let { return it }
        return channels.firstOrNull()
    }

    fun deleteNotificationChannel(
        packageName: String,
        channelId: String
    ) {
        XLog.d(TAG, "deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        notificationManager.callMethod("deleteNotificationChannel", packageName, channelId)
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup>
    ) {
        XLog.d(TAG, "createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")

        // 无法指定 uid，调用成功也不会生效
        // void createNotificationChannelGroups(String pkg, in ParceledListSlice channelGroupList);
        // val list = XposedHelpers.findConstructorExact("android.content.pm.ParceledListSlice", null, List::class.java)
        //     .newInstance(groups)
        // notificationManager.callMethod("createNotificationChannelGroups", packageName, list)

        groups.forEach {
            it.setField("mName", "Mi Push", String::class.java)

            // 无法 hook
            // void createNotificationChannelGroup(String pkg, int uid, NotificationChannelGroup group, boolean fromApp, boolean fromListener)
            // notificationManager.callMethod("createNotificationChannelGroup", packageName, getUid(packageName), it, true, false)
            try {
                // void updateNotificationChannelGroupForPackage(String pkg, int uid, in NotificationChannelGroup group);
                // 因 createNotificationChannelGroup 的 fromApp 为 false，首次创建会产生 NullPointerException
                notificationManager.callMethod(
                    "updateNotificationChannelGroupForPackage",
                    packageName,
                    getUid(packageName),
                    it
                )
            } catch (e: Throwable) {
                // ignore
                // Attempt to invoke virtual method 'boolean android.app.NotificationChannelGroup.isBlocked()' on a null object reference
            }
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String
    ): NotificationChannelGroup? {
        XLog.d(TAG, "getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        //NotificationChannelGroup getNotificationChannelGroupForPackage(String groupId, String pkg, int uid);
        return notificationManager.callMethod("getNotificationChannelGroupForPackage", groupId, packageName, getUid(packageName)) as NotificationChannelGroup?
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        XLog.d(TAG, "getNotificationChannelGroups() called with: packageName = $packageName")

        //ParceledListSlice getNotificationChannelGroupsForPackage(String pkg, int uid, boolean includeDeleted);
        val parceledListSlice = XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelGroupsForPackage", String::class.java, Int::class.java, Boolean::class.java)
            .invoke(notificationManager, packageName, getUid(packageName), false)
        @Suppress("UNCHECKED_CAST")
        val list = parceledListSlice?.callMethod("getList") as List<NotificationChannelGroup?>?
        return list
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String
    ) {
        XLog.d(TAG, "deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        //void deleteNotificationChannelGroup(String pkg, String channelGroupId);
        notificationManager.callMethod("deleteNotificationChannelGroup", packageName, groupId)
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        XLog.d(TAG, "areNotificationsEnabled() called with: packageName = $packageName")
        return notificationManager.callMethod("areNotificationsEnabledForPackage", packageName, getUid(packageName)) as Boolean
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        XLog.d(TAG, "getActiveNotifications() called with: packageName = $packageName")
        val parceledListSlice = notificationManager.callMethod("getAppActiveNotifications", packageName, getUserId())
        @Suppress("UNCHECKED_CAST")
        val list = parceledListSlice?.callMethod("getList") as List<StatusBarNotification>
        return list.toTypedArray()
    }

}
