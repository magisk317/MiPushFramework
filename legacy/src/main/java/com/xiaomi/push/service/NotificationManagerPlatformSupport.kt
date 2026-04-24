package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

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

internal object NotificationManagerPlatformSupport {
    private const val XMSF_FAKE_CONDITION_PROVIDER_PATH = "xmsf_fake_condition_provider_path"

    private var appContext: Context? = null
    private var supportFwk = false
    private var nms: Any? = null

    @JvmStatic
    fun init(context: Context) {
        if (appContext != null) {
            return
        }
        appContext = context.applicationContext
        val nm = getNm()
        val supported = (JavaCalls.callMethod(nm, "isSystemConditionProviderEnabled", XMSF_FAKE_CONDITION_PROVIDER_PATH) as? Boolean) ?: false
        NotificationManagerHelper.outLog("fwk is support.init:$supported")
        supportFwk = supported
        if (supported) {
            nms = JavaCalls.callMethod(nm, "getService")
        }
    }

    @JvmStatic
    fun isRomSupportNotificationBelongToApp(context: Context): Boolean {
        init(context)
        return isSupportFwk()
    }

    @JvmStatic
    fun isSupportFwk(): Boolean {
        val applicationContext = appContext ?: return false
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

    @JvmStatic
    @Throws(Exception::class)
    fun cancel(packageName: String, notificationId: Int) {
        JavaCalls.callMethodOrThrow(nms, "cancelNotificationWithTag", packageName, null, notificationId, DeviceInfo.getSpaceId())
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createNotificationChannel(packageName: String, notificationChannel: NotificationChannel) {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid != -1) {
            JavaCalls.callMethodOrThrow(
                nms,
                "createNotificationChannelsForPackage",
                packageName,
                pkgUid,
                newParceledListSlice(listOf(notificationChannel)),
            )
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createNotificationChannelGroup(packageName: String, notificationChannelGroup: NotificationChannelGroup) {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid != -1) {
            JavaCalls.callMethodOrThrow(nms, "updateNotificationChannelGroupForPackage", packageName, pkgUid, notificationChannelGroup)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getActiveNotifications(packageName: String): List<StatusBarNotification>? {
        val spaceId = DeviceInfo.getSpaceId()
        if (spaceId == -1) {
            return null
        }
        val slice = JavaCalls.callMethod(nms, "getAppActiveNotifications", packageName, spaceId)
        val items = getListFromParceledListSlice(slice) ?: return null
        return items.map { it as StatusBarNotification }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getNotificationChannelGroup(groupId: String, packageName: String): NotificationChannelGroup? {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid == -1) {
            return null
        }
        return JavaCalls.callMethod(nms, "getNotificationChannelGroupForPackage", groupId, packageName, pkgUid) as? NotificationChannelGroup
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getNotificationChannels(packageName: String): List<NotificationChannel>? {
        val pkgUid = getPkgUid(packageName)
        if (pkgUid == -1) {
            return null
        }
        val slice = JavaCalls.callMethod(nms, "getNotificationChannelsForPackage", packageName, pkgUid, false)
        val items = getListFromParceledListSlice(slice) ?: return null
        return items.map { it as NotificationChannel }
    }

    @JvmStatic
    fun filterLocalActiveNotifications(packageName: String, notifications: Array<StatusBarNotification>?): List<StatusBarNotification> {
        val isMiui = MIUIUtils.isMIUI()
        if (notifications.isNullOrEmpty()) {
            return emptyList()
        }
        return notifications.filter { notification ->
            !isMiui || packageName == NotificationUtils.getTargetPackage(notification.notification)
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
