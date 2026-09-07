package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.xmpush.thrift.ConfigKey

object NotificationManagerPlatformSupport {
    private const val XMSF_FAKE_CONDITION_PROVIDER_PATH = "xmsf_fake_condition_provider_path"
    private const val IS_SYSTEM_HOOK_READY = "is_system_hook_ready"

    private var appContext: Context? = null
    @Volatile
    private var supportFwk = false
    @Volatile
    private var nms: Any? = null

    @JvmStatic
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        ensureServiceLocked(reason = "init")
    }

    /**
     * Early boot may probe before system_server hooks are ready and would otherwise stick
     * on supportFwk=false / nms=null forever. Re-probe until the binder service is usable.
     */
    private fun ensureServiceLocked(reason: String) {
        val applicationContext = appContext ?: return
        if (supportFwk && nms != null) {
            return
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fakeSupported = runCatching {
            JavaCalls.callMethodOrThrow(nm, "isSystemConditionProviderEnabled", XMSF_FAKE_CONDITION_PROVIDER_PATH) as? Boolean
        }.getOrNull() ?: false
        val hookReady = runCatching {
            JavaCalls.callMethodOrThrow(nm, "isSystemConditionProviderEnabled", IS_SYSTEM_HOOK_READY) as? Boolean
        }.getOrNull() ?: false
        val service = runCatching {
            JavaCalls.callMethodOrThrow(nm, "getService")
        }.getOrNull()
        val supported = fakeSupported || hookReady || service != null
        val changed = supported != supportFwk || (service != null && nms == null)
        supportFwk = supported
        if (service != null) {
            nms = service
        }
        if (changed || reason == "init") {
            NotificationManagerHelper.outLog(
                "fwk support probe reason=$reason supported=$supported fake=$fakeSupported hookReady=$hookReady hasService=${nms != null}"
            )
        }
    }

    @JvmStatic
    fun ensureInitialized(context: Context) {
        init(context)
        ensureServiceLocked(reason = "ensure")
    }

    @JvmStatic
    fun isRomSupportNotificationBelongToApp(context: Context): Boolean {
        init(context)
        ensureServiceLocked(reason = "rom-support")
        return isSupportFwk()
    }

    @JvmStatic
    fun isSupportFwk(): Boolean {
        val applicationContext = appContext ?: return false
        ensureServiceLocked(reason = "isSupportFwk")
        return MIUIUtils.isMIUI() &&
            OnlineConfig.getInstance(applicationContext).getBooleanValue(ConfigKey.NotificationBelongToAppSwitch.value, true) &&
            supportFwk
    }

    @JvmStatic
    fun getNm(): NotificationManager {
        val applicationContext = appContext ?: throw IllegalStateException("NotificationManagerPlatformSupport.init must be called first")
        return applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @JvmStatic
    fun getPkgUid(packageName: String): Int {
        if (Build.VERSION.SDK_INT < 24) {
            return -1
        }
        return runCatching {
            val applicationContext = appContext ?: return@runCatching -1
            applicationContext.packageManager.getPackageUid(packageName, 0)
        }.getOrDefault(-1)
    }

    @JvmStatic
    fun getListFromParceledListSlice(slice: Any?): List<*>? {
        if (slice == null) {
            return null
        }
        return runCatching {
            slice.javaClass.getMethod("getList").invoke(slice) as? List<*>
        }.getOrNull()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun newParceledListSlice(list: List<*>): Any {
        return Class.forName("android.content.pm.ParceledListSlice")
            .getConstructor(List::class.java)
            .newInstance(list)
    }

    private fun invokeNotificationService(
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): Any? {
        val service = nms ?: throw IllegalStateException("NotificationManager service unavailable")
        val method = service.javaClass.getMethod(name, *parameterTypes).apply {
            isAccessible = true
        }
        return method.invoke(service, *args)
    }

    @JvmStatic
    internal fun shouldFallbackNotificationCancel(error: Throwable): Boolean =
        error is NoSuchMethodException

    @JvmStatic
    @Throws(Exception::class)
    fun cancel(packageName: String, notificationId: Int) {
        val service = nms ?: throw IllegalStateException("NotificationManager service unavailable")
        val userId = DeviceInfo.getSpaceId()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                // Stock 7.4.67-C g1.a uses the API 30+ five-argument signature. The
                // posting package is opPkg; omitting it can miss the delegated record.
                val operationPackage = appContext?.packageName
                    ?: throw IllegalStateException("NotificationManagerPlatformSupport.init must be called first")
                JavaCalls.callMethodOrThrow(
                    service,
                    "cancelNotificationWithTag",
                    packageName,
                    operationPackage,
                    null,
                    notificationId,
                    userId,
                )
            } else {
                JavaCalls.callMethodOrThrow(
                    service,
                    "cancelNotificationWithTag",
                    packageName,
                    null,
                    notificationId,
                    userId,
                )
            }
        } catch (error: Exception) {
            if (!shouldFallbackNotificationCancel(error)) {
                throw error
            }
            // Android 17/ROM variants may remove the hidden cancel method. The host cancel
            // API is safe only after matching the delegated target marker, concrete user and
            // null tag; never cancel an arbitrary host record with the same numeric id.
            if (userId < 0) {
                return
            }
            @Suppress("DEPRECATION")
            val owned = filterLocalActiveNotifications(packageName, getNm().activeNotifications)
                .any { it.userId == userId && it.tag == null && it.id == notificationId }
            if (owned) {
                val notificationManager = getNm()
                var cancelledAsTarget = false
                try {
                    val method = NotificationManager::class.java.getMethod(
                        "cancelAsPackage",
                        String::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType,
                    )
                    method.invoke(notificationManager, packageName, null, notificationId)
                    cancelledAsTarget = true
                } catch (error: NoSuchMethodException) {
                    NotificationManagerHelper.outLog(
                        "cancelAsPackage unavailable for $packageName/$notificationId: ${error.message}"
                    )
                }
                if (!cancelledAsTarget) {
                    notificationManager.cancel(notificationId)
                }
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createNotificationChannel(packageName: String, notificationChannel: NotificationChannel) {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid != -1) {
            val channels = newParceledListSlice(listOf(notificationChannel))
            invokeNotificationService(
                "createNotificationChannelsForPackage",
                arrayOf(String::class.java, Int::class.javaPrimitiveType!!, channels.javaClass),
                packageName,
                pkgUid,
                channels,
            )
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createNotificationChannelGroup(packageName: String, notificationChannelGroup: NotificationChannelGroup) {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid != -1) {
            invokeNotificationService(
                "updateNotificationChannelGroupForPackage",
                arrayOf(String::class.java, Int::class.javaPrimitiveType!!, NotificationChannelGroup::class.java),
                packageName,
                pkgUid,
                notificationChannelGroup,
            )
        }
    }

    /**
     * Delete a channel under [packageName] via INotificationManager.
     * Method signatures vary by ROM/API; try the known package-scoped overloads.
     * Callers must still verify disappearance — some paths no-op without throwing.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun deleteNotificationChannel(packageName: String, channelId: String) {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid == -1) {
            throw IllegalStateException("package uid unavailable for $packageName")
        }
        val service = nms ?: throw IllegalStateException("NotificationManager service unavailable")
        val errors = mutableListOf<Throwable>()
        // AOSP binder: deleteNotificationChannel(String pkg, String channelId)
        runCatching {
            invokeNotificationService(
                "deleteNotificationChannel",
                arrayOf(String::class.java, String::class.java),
                packageName,
                channelId,
            )
            return
        }.onFailure(errors::add)
        // Some ROMs: deleteNotificationChannel(String pkg, int uid, String channelId)
        runCatching {
            invokeNotificationService(
                "deleteNotificationChannel",
                arrayOf(String::class.java, Int::class.javaPrimitiveType!!, String::class.java),
                packageName,
                pkgUid,
                channelId,
            )
            return
        }.onFailure(errors::add)
        // MIUI-ish: deleteNotificationChannel(String pkg, int uid, String channelId, int callingUid, boolean fromSystemOrSystemUi)
        runCatching {
            invokeNotificationService(
                "deleteNotificationChannel",
                arrayOf(
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                ),
                packageName,
                pkgUid,
                channelId,
                android.os.Process.myUid(),
                true,
            )
            return
        }.onFailure(errors::add)
        throw errors.lastOrNull() ?: IllegalStateException("deleteNotificationChannel unsupported for $packageName/$channelId")
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getActiveNotifications(packageName: String): List<StatusBarNotification>? {
        // Retain the stock/runtime facade for callers that do not carry an explicit user.
        // Product-owned XMSF paths must use the overload below instead of silently inheriting
        // the process' current space.
        return getActiveNotifications(packageName, DeviceInfo.getSpaceId())
    }

    @JvmStatic
    internal fun shouldFallbackActiveNotifications(error: Throwable): Boolean =
        error is NoSuchMethodException

    @JvmStatic
    @Suppress("DEPRECATION") // StatusBarNotification.userId is the only API available in the compile SDK.
    @Throws(Exception::class)
    fun getActiveNotifications(packageName: String, userId: Int): List<StatusBarNotification>? {
        if (userId < 0) {
            return null
        }
        val slice = runCatching {
            invokeNotificationService(
                "getAppActiveNotifications",
                arrayOf(String::class.java, Int::class.javaPrimitiveType!!),
                packageName,
                userId,
            )
        }.getOrElse { error ->
            if (!shouldFallbackActiveNotifications(error)) {
                throw error
            }
            // Android 17/ROM variants may remove the hidden package-scoped method. The
            // host API is already used by the shell compatibility path; keep the same
            // target marker fence and explicit user scope instead of widening the result.
            return filterLocalActiveNotifications(packageName, getNm().activeNotifications)
                .filter { it.userId == userId }
        }
        val items = getListFromParceledListSlice(slice) ?: return null
        return items.mapNotNull { it as? StatusBarNotification }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getNotificationChannelGroup(groupId: String, packageName: String): NotificationChannelGroup? {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid == -1) {
            return null
        }
        return invokeNotificationService(
            "getNotificationChannelGroupForPackage",
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!),
            groupId,
            packageName,
            pkgUid,
        ) as? NotificationChannelGroup
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getNotificationChannels(packageName: String): List<NotificationChannel>? {
        ensureServiceLocked(reason = "getChannels")
        val pkgUid = getPkgUid(packageName)
        if (pkgUid == -1) {
            NotificationManagerHelper.outLog("getNotificationChannels skip pkg=$packageName uid=-1")
            return null
        }
        val service = nms
        if (service == null) {
            NotificationManagerHelper.outLog("getNotificationChannels nms=null pkg=$packageName uid=$pkgUid")
            return null
        }
        val method = service.javaClass.getMethod(
            "getNotificationChannelsForPackage",
            String::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        val slice = method.invoke(service, packageName, pkgUid, false)
        val items = getListFromParceledListSlice(slice) ?: return null
        return items.map { it as NotificationChannel }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getNotificationChannelGroups(packageName: String): List<NotificationChannelGroup>? {
        ensureServiceLocked(reason = "getGroups")
        val pkgUid = getPkgUid(packageName)
        if (pkgUid == -1) {
            NotificationManagerHelper.outLog("getNotificationChannelGroups skip pkg=$packageName uid=-1")
            return null
        }
        val service = nms
        if (service == null) {
            NotificationManagerHelper.outLog("getNotificationChannelGroups nms=null pkg=$packageName uid=$pkgUid")
            return null
        }
        val slice = runCatching {
            invokeNotificationService(
                "getNotificationChannelGroupsForPackage",
                arrayOf(String::class.java, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!),
                packageName,
                pkgUid,
                false,
            )
        }.getOrElse {
            invokeNotificationService(
                "getNotificationChannelGroupsForPackage",
                arrayOf(String::class.java, Int::class.javaPrimitiveType!!),
                packageName,
                pkgUid,
            )
        }
        val items = getListFromParceledListSlice(slice) ?: return null
        return items.map { it as NotificationChannelGroup }
    }

    @JvmStatic
    fun filterLocalActiveNotifications(packageName: String, notifications: Array<StatusBarNotification>?): List<StatusBarNotification> {
        if (notifications.isNullOrEmpty()) {
            return emptyList()
        }
        // Stock 7.4.67-C g1.h filters locally posted XMSF records by the embedded target
        // package. The old local code skipped this outside MIUI, exposing every XMSF post
        // to one app's clear request; applying the stock scope also covers our non-MIUI
        // standard/live-update fallback, where delegated identity is carried in extras.
        return notifications.filter { notification ->
            packageName == NotificationUtils.getTargetPackage(notification.notification)
        }
    }

    @JvmStatic
    fun filterMipushChannels(packageName: String, format: String, channels: List<NotificationChannel>?): List<NotificationChannel>? {
        if (!MIUIUtils.isMIUI() || channels == null) {
            return channels
        }
        val prefix = format.format(packageName, "")
        return channels.filter { it.id.startsWith(prefix) }
    }

    @JvmStatic
    fun notify(packageName: String, notificationId: Int, notification: Notification) {
        val nm = getNm()
        if (Build.VERSION.SDK_INT >= 19) {
            notification.extras.putString("xmsf_target_package", packageName)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            nm.notifyAsPackage(packageName, null, notificationId, notification)
        } else {
            nm.notify(notificationId, notification)
        }
    }
}
