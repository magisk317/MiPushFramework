package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object MIPushNotificationPlatformSupport {
    private const val EXTRA_MESSAGE_ID = "message_id"
    private const val EXTRA_SHOW_AT_TAIL = "miui.showAtTail"
    private const val EXTRA_SCORE_INFO = "score_info"

    @Suppress("DEPRECATION")
    @JvmStatic
    fun buildLegacyNotification(
        context: Context,
        container: XmPushActionContainer,
        pushMetaInfo: PushMetaInfo,
        remoteViews: RemoteViews?,
        pendingIntent: PendingIntent,
    ): Notification {
        val notification = Notification(
            MIPushNotificationViewSupport.getIdForSmallIcon(context, MIPushNotificationHelper.getTargetPackage(container)),
            null,
            System.currentTimeMillis(),
        )
        val titleAndDescription = MIPushNotificationViewSupport.determineTitleAndDespByDIP(context, pushMetaInfo)
        try {
            notification.javaClass
                .getMethod(
                    "setLatestEventInfo",
                    Context::class.java,
                    CharSequence::class.java,
                    CharSequence::class.java,
                    PendingIntent::class.java,
                )
                .invoke(notification, context, titleAndDescription[0], titleAndDescription[1], pendingIntent)
        } catch (e: InvocationTargetException) {
            reportLegacyBuildError(context, container, pushMetaInfo, "7")
            MyLog.e("meet invocation target error. $e")
        } catch (e: IllegalAccessException) {
            reportLegacyBuildError(context, container, pushMetaInfo, "5")
            MyLog.e("meet illegal access error. $e")
        } catch (e: IllegalArgumentException) {
            reportLegacyBuildError(context, container, pushMetaInfo, "6")
            MyLog.e("meet illegal argument error. $e")
        } catch (e: NoSuchMethodException) {
            reportLegacyBuildError(context, container, pushMetaInfo, "4")
            MyLog.e("meet no such method error. $e")
        }

        val extra = pushMetaInfo.extra
        extra?.get(MIPushNotificationHelper.NOTIFICATION_TICKER)?.takeIf { it.isNotEmpty() }?.let {
            notification.tickerText = it
        }

        val now = System.currentTimeMillis()
        if (now - MIPushNotificationHelper.lastNotify > MIPushNotificationHelper.NOTIFY_INTERVAL) {
            MIPushNotificationHelper.lastNotify = now
            var notifyType = pushMetaInfo.notifyType
            val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
            if (MIPushNotificationHelper.hasLocalNotifyType(context, targetPackage)) {
                notifyType = MIPushNotificationHelper.getLocalNotifyType(context, targetPackage)
            }
            notification.defaults = notifyType
            val soundUri = extra?.get(MIPushNotificationHelper.NOTIFICATION_SOUND_URI)
            if (soundUri != null &&
                (notifyType and 1) != 0 &&
                soundUri.startsWith(MIPushNotificationHelper.ANDROID_RESOURCE + targetPackage)
            ) {
                notification.defaults = notifyType xor 1
                notification.sound = android.net.Uri.parse(soundUri)
            }
        }
        notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL
        if (remoteViews != null) {
            notification.contentView = remoteViews
        }
        return notification
    }

    @JvmStatic
    fun applyMiuiExtras(
        context: Context,
        container: XmPushActionContainer,
        pushMetaInfo: PushMetaInfo,
        notification: Notification,
    ) {
        if (MIUIUtils.isMIUI() && Build.VERSION.SDK_INT >= 19) {
            pushMetaInfo.id.takeIf { !TextUtils.isEmpty(it) }?.let {
                notification.extras.putString(EXTRA_MESSAGE_ID, it)
            }
            pushMetaInfo.internal?.get(EXTRA_SCORE_INFO)?.takeIf { !TextUtils.isEmpty(it) }?.let {
                notification.extras.putString(EXTRA_SCORE_INFO, it)
            }
            val messageType = when {
                MIPushNotificationHelper.isNormalNotificationMessage(container) -> 1000
                MIPushNotificationHelper.isBusinessMessage(container) -> 3000
                else -> -1
            }
            notification.extras.putString(ReportConstants.EVENT_MESSAGE_TYPE, messageType.toString())
            notification.extras.putString(
                MIPushNotificationHelper.NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING,
                MIPushNotificationHelper.getTargetPackage(container),
            )
        }

        pushMetaInfo.extra?.get("message_count")?.takeIf { MIUIUtils.isMIUI() }?.let { messageCount ->
            try {
                setMessageCount(notification, messageCount.toInt())
            } catch (e: NumberFormatException) {
                reportLegacyBuildError(context, container, pushMetaInfo, "8")
                MyLog.e("fail to set message count. $e")
            }
        }

        val showAtTail = pushMetaInfo.extra?.get(EXTRA_SHOW_AT_TAIL)
        if (Build.VERSION.SDK_INT >= 19 && MIUIUtils.isMIUI()) {
            notification.extras.putBoolean(EXTRA_SHOW_AT_TAIL, showAtTail == "true")
        }
        if (!MIUIUtils.isXMS() && MIUIUtils.isXMSF(context)) {
            setTargetPackage(notification, MIPushNotificationHelper.getTargetPackage(container))
        }
    }

    private fun reportLegacyBuildError(
        context: Context,
        container: XmPushActionContainer,
        pushMetaInfo: PushMetaInfo?,
        code: String,
    ) {
        if (pushMetaInfo != null) {
            PushClientReportManager.getInstance(context.applicationContext)
                .reportEvent4ERROR(
                    container.packageName,
                    MIPushNotificationHelper.getInterfaceId(container),
                    pushMetaInfo.id,
                    code,
                )
        }
    }

    private fun setMessageCount(notification: Notification, count: Int) {
        val extraNotification = JavaCalls.getField(notification, "extraNotification")
        if (extraNotification != null) {
            JavaCalls.callMethod(extraNotification, "setMessageCount", count)
        }
    }

    private fun setTargetPackage(notification: Notification, packageName: String): Notification {
        try {
            val extraNotificationField = Notification::class.java.getDeclaredField("extraNotification")
            extraNotificationField.isAccessible = true
            val extraNotification = extraNotificationField.get(notification)
            val setTargetPkg = extraNotification.javaClass.getDeclaredMethod("setTargetPkg", CharSequence::class.java)
            setTargetPkg.isAccessible = true
            setTargetPkg.invoke(extraNotification, packageName)
        } catch (e: Exception) {
            MyLog.e(e)
        }
        return notification
    }
}
