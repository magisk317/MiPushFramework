package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import java.util.HashMap

object MIPushMessageRoutingSupport {
    @JvmStatic
    fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return try {
            !context.packageManager.queryBroadcastReceivers(intent, 32).isNullOrEmpty()
        } catch (_: Exception) {
            true
        }
    }

    @JvmStatic
    fun isMIUIOldAdsSDKMessage(container: XmPushActionContainer): Boolean {
        val extra = container.metaInfo?.extra ?: return false
        return extra["obslete_ads_message"] == "1"
    }

    @JvmStatic
    fun isMIUIPushMessage(container: XmPushActionContainer): Boolean {
        val extra = container.metaInfo?.extra ?: return false
        return PushConstants.PUSH_SERVICE_PACKAGE_NAME == container.packageName &&
            extra.containsKey(MIPushNotificationHelper.MIUI_PACKAGE_NAME)
    }

    @JvmStatic
    fun isMIUIPushSupported(context: Context, packageName: String): Boolean {
        val clickIntent = Intent("com.xiaomi.mipush.miui.CLICK_MESSAGE").setPackage(packageName)
        val receiveIntent = Intent("com.xiaomi.mipush.miui.RECEIVE_MESSAGE").setPackage(packageName)
        return try {
            val packageManager = context.packageManager
            val receivers = packageManager.queryBroadcastReceivers(receiveIntent, 32)
            val services = packageManager.queryIntentServices(clickIntent, 32)
            receivers.isNotEmpty() || services.isNotEmpty()
        } catch (e: Exception) {
            MyLog.e(e)
            false
        }
    }

    @JvmStatic
    fun predefinedNotification(container: XmPushActionContainer): Boolean {
        val extra = container.metaInfo?.extra
        return extra != null && extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)
    }

    @JvmStatic
    fun shouldSendBroadcast(
        pushAction: IPushServiceAction,
        packageName: String,
        container: XmPushActionContainer,
        pushMetaInfo: PushMetaInfo?,
    ): Boolean {
        return pushAction.runtimeObserver.shouldSendBroadcast(pushAction.context, packageName, container, pushMetaInfo)
    }
}
