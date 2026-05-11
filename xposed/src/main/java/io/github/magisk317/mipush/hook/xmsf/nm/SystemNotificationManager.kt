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
import java.lang.reflect.InvocationTargetException

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

    private fun Throwable.unwrapSystemCallFailure(): Throwable {
        return when (this) {
            is InvocationTargetException -> targetException ?: cause ?: this
            is XposedHelpers.InvocationTargetError -> cause ?: this
            else -> this
        }
    }

    private inline fun <T> runSystemCall(
        operation: String,
        packageName: String,
        fallback: (Throwable) -> T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (t: Throwable) {
            val cause = t.unwrapSystemCallFailure()
            if (cause is SecurityException) {
                XLog.w(TAG, "$operation: system API blocked for $packageName: ${cause.message}")
            } else {
                XLog.e(TAG, "$operation: system API failed for $packageName", cause)
            }
            fallback(cause)
        }
    }

    private fun localNotificationManager(): NotificationManager? {
        return runCatching {
            AndroidAppHelper.currentApplication().getSystemService(NotificationManager::class.java)
        }.getOrNull()
    }

    private fun isCurrentPackage(packageName: String): Boolean {
        return runCatching {
            AndroidAppHelper.currentApplication().packageName == packageName
        }.getOrDefault(false)
    }

    private fun notifyLocally(tag: String?, id: Int, notification: Notification) {
        runCatching {
            localNotificationManager()?.notify(tag, id, notification)
        }.onFailure {
            XLog.e(TAG, "notify: local fallback failed", it)
        }
    }

    private fun cancelLocally(tag: String?, id: Int) {
        runCatching {
            localNotificationManager()?.cancel(tag, id)
        }.onFailure {
            XLog.e(TAG, "cancel: local fallback failed", it)
        }
    }

    private fun createChannelsLocally(channels: List<NotificationChannel>) {
        runCatching {
            localNotificationManager()?.createNotificationChannels(channels)
        }.onFailure {
            XLog.e(TAG, "createNotificationChannels: local fallback failed", it)
        }
    }

    private fun createGroupsLocally(groups: List<NotificationChannelGroup>) {
        runCatching {
            localNotificationManager()?.createNotificationChannelGroups(groups)
        }.onFailure {
            XLog.e(TAG, "createNotificationChannelGroups: local fallback failed", it)
        }
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ) {
        XLog.d(TAG, "notify() called with: packageName = $packageName, tag = $tag, id = $id, notification = $notification")
        runSystemCall("notify", packageName, fallback = {
            notifyLocally(tag, id, notification)
        }) {
            val methodEnqueueNotificationWithTag = XposedHelpers.findMethodExact(notificationManager.javaClass, "enqueueNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Notification::class.java, Int::class.java)
            val opPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ANDROID_PACKAGE_NAME else packageName
            methodEnqueueNotificationWithTag.invoke(notificationManager, packageName, opPkg, tag, id, notification, getUserId())
        }
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        XLog.d(TAG, "cancel() called with: packageName = $packageName, tag = $tag, id = $id")
        runSystemCall("cancel", packageName, fallback = {
            cancelLocally(tag, id)
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val methodCancelNotificationWithTag = XposedHelpers.findMethodExact(notificationManager.javaClass, "cancelNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java)
                methodCancelNotificationWithTag.invoke(notificationManager, packageName, ANDROID_PACKAGE_NAME, tag, id, getUserId())
            } else {
                val methodCancelNotificationWithTag = XposedHelpers.findMethodExact(notificationManager.javaClass, "cancelNotificationWithTag", String::class.java, String::class.java, Int::class.java, Int::class.java)
                methodCancelNotificationWithTag.invoke(notificationManager, packageName, tag, id, getUserId())
            }
        }
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel>
    ) {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        runSystemCall("createNotificationChannels", packageName, fallback = {
            createChannelsLocally(channels)
        }) {
            val channelsList = XposedHelpers.findConstructorExact("android.content.pm.ParceledListSlice", null, List::class.java)
                .newInstance(channels)
            notificationManager.callMethod("createNotificationChannelsForPackage", packageName, getUid(packageName), channelsList)
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        XLog.d(TAG, "getNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        return runSystemCall("getNotificationChannel", packageName, fallback = {
            RootNotificationHelper.getNotificationChannel(packageName, channelId)
                ?: if (isCurrentPackage(packageName) && !channelId.isNullOrEmpty()) {
                    localNotificationManager()?.getNotificationChannel(channelId)
                } else {
                    null
                }
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java)
                    .invoke(notificationManager, packageName, getUid(packageName), channelId, null, false) as NotificationChannel?
            } else {
                XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, Boolean::class.java)
                    .invoke(notificationManager, packageName, getUid(packageName), channelId, false) as NotificationChannel?
            }
        }
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        XLog.d(TAG, "getNotificationChannels() called with: packageName = $packageName")
        return runSystemCall("getNotificationChannels", packageName, fallback = {
            RootNotificationHelper.getNotificationChannels(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannels
                } else {
                    null
                }
        }) {
            val parceledListSlice = XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .invoke(notificationManager, packageName, getUid(packageName), false)
            @Suppress("UNCHECKED_CAST")
            parceledListSlice?.callMethod("getList") as List<NotificationChannel?>?
        }
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
        runSystemCall("deleteNotificationChannel", packageName, fallback = {
            runCatching {
                if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.deleteNotificationChannel(channelId)
                }
            }.onFailure {
                XLog.e(TAG, "deleteNotificationChannel: local fallback failed", it)
            }
        }) {
            notificationManager.callMethod("deleteNotificationChannel", packageName, channelId)
        }
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

        runSystemCall("createNotificationChannelGroups", packageName, fallback = {
            createGroupsLocally(groups)
        }) {
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
                    val cause = e.unwrapSystemCallFailure()
                    if (cause is SecurityException) {
                        throw e
                    }
                    // ignore
                    // Attempt to invoke virtual method 'boolean android.app.NotificationChannelGroup.isBlocked()' on a null object reference
                }
            }
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String
    ): NotificationChannelGroup? {
        XLog.d(TAG, "getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        return runSystemCall("getNotificationChannelGroup", packageName, fallback = {
            RootNotificationHelper.getNotificationChannelGroup(packageName, groupId)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.getNotificationChannelGroup(groupId)
                } else {
                    null
                }
        }) {
            notificationManager.callMethod("getNotificationChannelGroupForPackage", groupId, packageName, getUid(packageName)) as NotificationChannelGroup?
        }
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        XLog.d(TAG, "getNotificationChannelGroups() called with: packageName = $packageName")
        return runSystemCall("getNotificationChannelGroups", packageName, fallback = {
            RootNotificationHelper.getNotificationChannelGroups(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannelGroups
                } else {
                    null
                }
        }) {
            val parceledListSlice = XposedHelpers.findMethodExact(notificationManager.javaClass, "getNotificationChannelGroupsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .invoke(notificationManager, packageName, getUid(packageName), false)
            @Suppress("UNCHECKED_CAST")
            parceledListSlice?.callMethod("getList") as List<NotificationChannelGroup?>?
        }
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String
    ) {
        XLog.d(TAG, "deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        runSystemCall("deleteNotificationChannelGroup", packageName, fallback = {
            runCatching {
                if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.deleteNotificationChannelGroup(groupId)
                }
            }.onFailure {
                XLog.e(TAG, "deleteNotificationChannelGroup: local fallback failed", it)
            }
        }) {
            notificationManager.callMethod("deleteNotificationChannelGroup", packageName, groupId)
        }
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        XLog.d(TAG, "areNotificationsEnabled() called with: packageName = $packageName")
        return runSystemCall("areNotificationsEnabled", packageName, fallback = {
            RootNotificationHelper.areNotificationsEnabled(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.areNotificationsEnabled() ?: true
                } else {
                    true
                }
        }) {
            notificationManager.callMethod("areNotificationsEnabledForPackage", packageName, getUid(packageName)) as Boolean
        }
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        XLog.d(TAG, "getActiveNotifications() called with: packageName = $packageName")
        return runSystemCall("getActiveNotifications", packageName, fallback = {
            null
        }) {
            val parceledListSlice = notificationManager.callMethod("getAppActiveNotifications", packageName, getUserId())
            @Suppress("UNCHECKED_CAST")
            val list = parceledListSlice?.callMethod("getList") as List<StatusBarNotification>
            list.toTypedArray()
        }
    }

}
