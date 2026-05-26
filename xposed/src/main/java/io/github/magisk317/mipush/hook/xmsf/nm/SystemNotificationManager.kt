package io.github.magisk317.mipush.hook.xmsf.nm

import android.app.*
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.callMethod
import io.github.magisk317.mipush.xposed.callStaticMethod
import io.github.magisk317.mipush.xposed.currentApplication
import io.github.magisk317.mipush.xposed.HookInvocationTargetError
import io.github.magisk317.mipush.xposed.findHookConstructorExact
import io.github.magisk317.mipush.xposed.findHookMethodExact
import io.github.magisk317.mipush.xposed.setField
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.InvocationTargetException

object SystemNotificationManager {
    private const val TAG = "SystemNotificationManager"
    private val missingPackageWarnings = mutableSetOf<String>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    private val notificationManager: Any = NotificationManager::class.java.callStaticMethod("getService")!!

    private fun getUid(packageName: String): Int {
        return currentApplication()!!.packageManager.getPackageUid(packageName, 0)
    }

    private fun resolveUid(packageName: String, operation: String): Int? {
        return try {
            getUid(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            if (missingPackageWarnings.add("$operation|$packageName")) {
                XLog.w(TAG, "$operation: target package not installed, skip target system API for $packageName")
            }
            null
        } catch (e: SecurityException) {
            XLog.d(TAG, "$operation: system API blocked for $packageName, will use root fallback")
            null
        }
    }

    private fun getUserId(): Int {
        return currentApplication()?.callMethod("getUserId") as Int? ?: 0
    }

    private fun Throwable.unwrapSystemCallFailure(): Throwable {
        return when (this) {
            is InvocationTargetException -> targetException ?: cause ?: this
            is HookInvocationTargetError -> cause ?: this
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
            currentApplication()?.getSystemService(NotificationManager::class.java)
        }.getOrNull()
    }

    private fun isCurrentPackage(packageName: String): Boolean {
        return runCatching {
            currentApplication()?.packageName == packageName
        }.getOrDefault(false)
    }

    private fun notifyLocally(tag: String?, id: Int, notification: Notification): Boolean {
        return runCatching {
            localNotificationManager()?.notify(tag, id, notification)
            true
        }.onFailure {
            XLog.e(TAG, "notify: local fallback failed", it)
        }.getOrDefault(false)
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
    ): Boolean {
        XLog.d(TAG, "notify() pkg=$packageName tag=$tag id=$id channel=${notification.channelId} group=${notification.group}")
        if (!isCurrentPackage(packageName) && resolveUid(packageName, "notify") == null) {
            XLog.d(TAG, "notify() package not installed, falling back to local: $packageName")
            return notifyLocally(tag, id, notification)
        }
        return runSystemCall("notify", packageName, fallback = {
            XLog.d(TAG, "notify() system call failed, falling back to local: $packageName id=$id")
            notifyLocally(tag, id, notification)
        }) {
            val methodEnqueueNotificationWithTag = findHookMethodExact(notificationManager.javaClass, "enqueueNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Notification::class.java, Int::class.java)
            val opPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ANDROID_PACKAGE_NAME else packageName
            methodEnqueueNotificationWithTag.invoke(notificationManager, packageName, opPkg, tag, id, notification, getUserId())
            XLog.d(TAG, "notify() enqueue OK pkg=$packageName id=$id")
            true
        }
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        XLog.d(TAG, "cancel() pkg=$packageName tag=$tag id=$id")
        runSystemCall("cancel", packageName, fallback = {
            XLog.d(TAG, "cancel() system call failed, falling back to local: $packageName id=$id")
            cancelLocally(tag, id)
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val methodCancelNotificationWithTag = findHookMethodExact(notificationManager.javaClass, "cancelNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java)
                methodCancelNotificationWithTag.invoke(notificationManager, packageName, ANDROID_PACKAGE_NAME, tag, id, getUserId())
            } else {
                val methodCancelNotificationWithTag = findHookMethodExact(notificationManager.javaClass, "cancelNotificationWithTag", String::class.java, String::class.java, Int::class.java, Int::class.java)
                methodCancelNotificationWithTag.invoke(notificationManager, packageName, tag, id, getUserId())
            }
        }
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel>
    ) {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        val uid = resolveUid(packageName, "createNotificationChannels")
        if (uid == null) {
            createChannelsLocally(channels)
            return
        }
        runSystemCall("createNotificationChannels", packageName, fallback = {
            createChannelsLocally(channels)
        }) {
            val channelsList = findHookConstructorExact("android.content.pm.ParceledListSlice", null, List::class.java)
                .newInstance(channels)
            notificationManager.callMethod("createNotificationChannelsForPackage", packageName, uid, channelsList)
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        XLog.d(TAG, "getNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        val uid = resolveUid(packageName, "getNotificationChannel")
        if (uid == null) {
            return RootNotificationHelper.getNotificationChannel(packageName, channelId)
                ?: if (isCurrentPackage(packageName) && !channelId.isNullOrEmpty()) {
                    localNotificationManager()?.getNotificationChannel(channelId)
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannel", packageName, fallback = {
            RootNotificationHelper.getNotificationChannel(packageName, channelId)
                ?: if (isCurrentPackage(packageName) && !channelId.isNullOrEmpty()) {
                    localNotificationManager()?.getNotificationChannel(channelId)
                } else {
                    null
                }
        }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                findHookMethodExact(notificationManager.javaClass, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java)
                    .invoke(notificationManager, packageName, uid, channelId, null, false) as NotificationChannel?
            } else {
                findHookMethodExact(notificationManager.javaClass, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, Boolean::class.java)
                    .invoke(notificationManager, packageName, uid, channelId, false) as NotificationChannel?
            }
        }
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        XLog.d(TAG, "getNotificationChannels() called with: packageName = $packageName")
        val uid = resolveUid(packageName, "getNotificationChannels")
        if (uid == null) {
            return RootNotificationHelper.getNotificationChannels(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannels
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannels", packageName, fallback = {
            RootNotificationHelper.getNotificationChannels(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannels
                } else {
                    null
                }
        }) {
            val parceledListSlice = findHookMethodExact(notificationManager.javaClass, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .invoke(notificationManager, packageName, uid, false)
            @Suppress("UNCHECKED_CAST")
            parceledListSlice?.callMethod("getList") as List<NotificationChannel?>?
        }
    }

    fun findPreferredTargetChannel(
        packageName: String,
        preferredChannelId: String?
    ): NotificationChannel? {
        if (preferredChannelId.isNullOrBlank()) {
            return null
        }
        val channels = getNotificationChannels(packageName)
            .orEmpty()
            .filterNotNull()
            .filter { it.importance != NotificationManager.IMPORTANCE_NONE }
        if (channels.isEmpty()) {
            return null
        }
        return channels.firstOrNull { it.id == preferredChannelId }
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
        val uid = resolveUid(packageName, "createNotificationChannelGroups")
        if (uid == null) {
            createGroupsLocally(groups)
            return
        }

        // 无法指定 uid，调用成功也不会生效
        // void createNotificationChannelGroups(String pkg, in ParceledListSlice channelGroupList);
        // val list = findHookConstructorExact("android.content.pm.ParceledListSlice", null, List::class.java)
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
                        uid,
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
        val uid = resolveUid(packageName, "getNotificationChannelGroup")
        if (uid == null) {
            return RootNotificationHelper.getNotificationChannelGroup(packageName, groupId)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.getNotificationChannelGroup(groupId)
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannelGroup", packageName, fallback = {
            RootNotificationHelper.getNotificationChannelGroup(packageName, groupId)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.getNotificationChannelGroup(groupId)
                } else {
                    null
                }
        }) {
            notificationManager.callMethod("getNotificationChannelGroupForPackage", groupId, packageName, uid) as NotificationChannelGroup?
        }
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        XLog.d(TAG, "getNotificationChannelGroups() called with: packageName = $packageName")
        val uid = resolveUid(packageName, "getNotificationChannelGroups")
        if (uid == null) {
            return RootNotificationHelper.getNotificationChannelGroups(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannelGroups
                } else {
                    null
                }
        }
        return runSystemCall("getNotificationChannelGroups", packageName, fallback = {
            RootNotificationHelper.getNotificationChannelGroups(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.notificationChannelGroups
                } else {
                    null
                }
        }) {
            val parceledListSlice = findHookMethodExact(notificationManager.javaClass, "getNotificationChannelGroupsForPackage", String::class.java, Int::class.java, Boolean::class.java)
                .invoke(notificationManager, packageName, uid, false)
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
        val uid = resolveUid(packageName, "areNotificationsEnabled")
        if (uid == null) {
            return RootNotificationHelper.areNotificationsEnabled(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.areNotificationsEnabled() ?: true
                } else {
                    true
                }
        }
        return runSystemCall("areNotificationsEnabled", packageName, fallback = {
            RootNotificationHelper.areNotificationsEnabled(packageName)
                ?: if (isCurrentPackage(packageName)) {
                    localNotificationManager()?.areNotificationsEnabled() ?: true
                } else {
                    true
                }
        }) {
            notificationManager.callMethod("areNotificationsEnabledForPackage", packageName, uid) as Boolean
        }
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        return runSystemCall("getActiveNotifications", packageName, fallback = {
            XLog.d(TAG, "getActiveNotifications() system call failed for $packageName, returning null")
            null
        }) {
            val parceledListSlice = notificationManager.callMethod("getAppActiveNotifications", packageName, getUserId())
            @Suppress("UNCHECKED_CAST")
            val list = parceledListSlice?.callMethod("getList") as List<StatusBarNotification>
            val ids = list.map { "${it.id}" }.joinToString(",")
            XLog.d(TAG, "getActiveNotifications() pkg=$packageName count=${list.size} ids=[$ids]")
            list.toTypedArray()
        }
    }

}
