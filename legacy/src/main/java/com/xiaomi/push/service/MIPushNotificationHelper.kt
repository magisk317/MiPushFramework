package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.app.ActivityManager
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Pair
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.util.Arrays
import java.util.LinkedList

object MIPushNotificationHelper {
    private const val ALLOW_DYNAMIC_ICON_ON_MIUI = "__adiom"
    const val ANDROID_RESOURCE = "android.resource://"
    private const val DYNAMIC_ICON_URI = "__dynamic_icon_uri"
    const val EXTRA_PARAM_NOTIFY_FOREGROUND = "notify_foreground"
    const val EXTRA_PARAM_SHOW_AT_TAIL = "miui.showAtTail"
    const val FROM_NOTIFICATION = "mipush_notified"
    const val MAX_DOWNLOAD_ONLINE_PICTURE_WAIT = 180
    const val MAX_NOTIFY_ID_CACHE_SIZE = 100
    private const val MESSAGE_TYPE_INDEX = "satuigmo"
    const val MIUI_PACKAGE_NAME = "miui_package_name"
    const val NOTIFICATION_CUSTOM_BUILDER_SET_TITLE = "custom_builder_set_title"
    const val NOTIFICATION_EXTRA_SHOW_AT_TAIL = "miui.showAtTail"
    const val NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING = "target_package"
    const val NOTIFICATION_IMAGE_TEXT_COLOR = "notification_image_text_color"
    const val NOTIFICATION_IS_SUMMARY = "notification_is_summary"
    const val NOTIFICATION_LOCAL_EXTRA_CREATE_TIME_LONG = "mipush_org_when"
    const val NOTIFICATION_SHOW_WHEN = "notification_show_when"
    const val NOTIFICATION_SOUND_URI = "sound_uri"
    const val NOTIFICATION_TICKER = "ticker"
    const val NOTIFY_ALL = -1
    const val NOTIFY_INTERVAL = 10_000L
    const val NO_NOTIFY_ID = -2
    private const val PREF_KEY_NOTIFY_TYPE = "pref_notify_type"
    private val notifyContainerCache = LinkedList<Pair<Int, XmPushActionContainer>>()

    @JvmField
    var lastNotify: Long = 0

    class GetNotificationResult {
        @JvmField var notification: Notification? = null
        @JvmField var trafficSize: Long = 0
    }

    class NotifyPushMessageInfo {
        @JvmField var targetPkgName: String? = null
        @JvmField var traffic: Long = 0
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        return MIPushNotificationViewSupport.drawableToBitmap(drawable)
    }

    @JvmStatic
    fun clearLocalNotifyType(context: Context, packageName: String) {
        context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).edit().remove(packageName).commit()
    }

    @JvmStatic
    fun clearNotification(context: Context, packageName: String) {
        MIPushNotificationCacheSupport.clearNotification(context, packageName, notifyContainerCache)
    }

    @JvmStatic
    fun clearNotification(context: Context, packageName: String, notificationId: Int) {
        MIPushNotificationCacheSupport.clearNotification(context, packageName, notificationId, notifyContainerCache)
    }

    @JvmStatic
    fun clearNotification(context: Context, packageName: String, title: String?, description: String?) {
        MIPushNotificationCacheSupport.clearNotification(context, packageName, title.orEmpty(), description.orEmpty(), notifyContainerCache)
    }

    @JvmStatic
    fun getInterfaceId(container: XmPushActionContainer): String {
        return when {
            isBusinessMessage(container) -> ReportConstants.AWAKE_EVENT_CHAIN_INTERFACE_ID
            isNormalNotificationMessage(container) -> ReportConstants.NOTIFICATION_EVENT_CHAIN_INTERFACE_ID
            isPassThoughMessage(container) -> ReportConstants.THROUGH_EVENT_CHAIN_INTERFACE_ID
            isRegisterMessage(container) -> ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID
            else -> ""
        }
    }

    @JvmStatic
    fun getLocalNotifyType(context: Context, packageName: String): Int {
        return context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).getInt(packageName, Int.MAX_VALUE)
    }

    @JvmStatic
    fun getTargetPackage(container: XmPushActionContainer): String {
        val metaInfo = container.metaInfo
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == container.packageName && metaInfo?.extra != null) {
            val targetPackage = metaInfo.extra[MIUI_PACKAGE_NAME]
            if (!TextUtils.isEmpty(targetPackage)) {
                return targetPackage!!
            }
        }
        return container.packageName
    }

    @JvmStatic
    fun hasLocalNotifyType(context: Context, packageName: String): Boolean {
        return context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).contains(packageName)
    }

    @JvmStatic
    fun isApplicationForeground(context: Context, packageName: String): Boolean {
        val runningProcesses = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses ?: return false
        return runningProcesses.any { process ->
            process.importance == 100 && Arrays.asList(*process.pkgList).contains(packageName)
        }
    }

    @JvmStatic
    fun isBusinessMessage(container: XmPushActionContainer): Boolean {
        val metaInfo = container.metaInfo
        return isIdVaild(metaInfo) && metaInfo.isIgnoreRegInfo
    }

    @JvmStatic
    fun isNPBMessage(container: XmPushActionContainer): Boolean {
        return isBusinessMessage(container) || isNormalNotificationMessage(container) || isPassThoughMessage(container)
    }

    @JvmStatic
    fun isNormalNotificationMessage(container: XmPushActionContainer): Boolean {
        val metaInfo = container.metaInfo
        return isIdVaild(metaInfo) && metaInfo.passThrough == 0 && !isBusinessMessage(container)
    }

    @JvmStatic
    fun isNotifyForeground(extra: Map<String, String>?): Boolean {
        return extra?.get(EXTRA_PARAM_NOTIFY_FOREGROUND)?.let { it == "1" } ?: true
    }

    @JvmStatic
    fun isPassThoughMessage(container: XmPushActionContainer): Boolean {
        val metaInfo = container.metaInfo
        return isIdVaild(metaInfo) && metaInfo.passThrough == 1 && !isBusinessMessage(container)
    }

    @JvmStatic
    fun isRegisterMessage(container: XmPushActionContainer): Boolean {
        return container.action == ActionType.Registration
    }

    @JvmStatic
    fun notifyPushMessage(context: Context, pushAction: IPushServiceAction?, container: XmPushActionContainer, payload: ByteArray): NotifyPushMessageInfo {
        if (shouldUseLegacyPublishChain(context, container)) {
            return MIPushNotificationPublishSupport.notifyPushMessage(context, container, payload, notifyContainerCache)
        }
        return NotifyPushMessageInfo().apply {
            targetPkgName = getTargetPackage(container)
            try {
                val handler = pushAction?.notificationHandler
                if (handler != null) {
                    MyLog.w("NotificationHelper: delegating to IPushNotificationHandler")
                    handler.handleNotification(getTargetPackage(container), payload)
                } else {
                    MyLog.e("NotificationHelper: No notification handler available in IPushServiceAction")
                }
            } catch (t: Throwable) {
                MyLog.e(t)
            }
        }
    }

    @JvmStatic
    fun notifyPushMessage(
        context: Context,
        container: XmPushActionContainer,
        payload: ByteArray,
    ): NotifyPushMessageInfo {
        return notifyPushMessage(context, null, container, payload)
    }

    @JvmStatic
    fun onNotificationRemoved(context: Context, statusBarNotification: StatusBarNotification) {
        MIPushTopNotificationSupport.onNotificationRemoved(context, statusBarNotification)
    }

    @JvmStatic
    fun setLocalNotifyType(context: Context, packageName: String, notifyType: Int) {
        context.getSharedPreferences(PREF_KEY_NOTIFY_TYPE, 0).edit().putInt(packageName, notifyType).commit()
    }

    @JvmStatic
    fun uploadClearMessageData(context: Context, items: LinkedList<out Any>) {
        MIPushNotificationCacheSupport.uploadClearMessageData(context, items)
    }

    private fun isIdVaild(metaInfo: PushMetaInfo?): Boolean {
        val id = metaInfo?.id
        return !TextUtils.isEmpty(id) &&
            id!!.length == 22 &&
            MESSAGE_TYPE_INDEX.indexOf(id[0]) >= 0
    }

    /**
     * Display notifications now route through [MyMIPushNotificationHelper] by default.
     * The legacy publish chain is intentionally retained in source for quick rollback, but it is
     * no longer active unless this guard is flipped during troubleshooting.
     */
    private fun shouldUseLegacyPublishChain(context: Context, container: XmPushActionContainer): Boolean {
        return false
    }
}
