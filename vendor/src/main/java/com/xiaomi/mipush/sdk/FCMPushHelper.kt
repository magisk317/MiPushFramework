package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import android.text.TextUtils

/*
 * Local compatibility reference:
 * this helper has no same-path source in com.xiaomi.xmsf 7.4.67-C stock or the
 * 2026-04-13 current override tree, and is retained to preserve the legacy
 * `com.xiaomi.mipush.sdk` FCM surface used by the app-facing compatibility layer.
 */
object FCMPushHelper {
    @JvmStatic
    fun clearToken(context: Context) {
        AssemblePushHelper.clearToken(context, AssemblePush.ASSEMBLE_PUSH_FCM)
    }

    @JvmStatic
    fun convertMessage(intent: Intent) {
        AssemblePushHelper.convertMessage(intent)
    }

    @JvmStatic
    fun isFCMSwitchOpen(context: Context): Boolean {
        return AssemblePushHelper.isOpenAssemblePushOnlineSwitch(context, AssemblePush.ASSEMBLE_PUSH_FCM) &&
            MiPushClient.getOpenFCMPush(context)
    }

    @JvmStatic
    fun notifyFCMNotificationCome(context: Context, map: Map<String, String>) {
        val pushMsg = map["pushMsg"]
        val receiver = AssemblePushHelper.getMiPushReceiver(context)
        if (TextUtils.isEmpty(pushMsg) || receiver == null) {
            return
        }
        receiver.onNotificationMessageArrived(context, AssemblePushHelper.parseMiPushMessage(pushMsg ?: ""))
    }

    @JvmStatic
    fun notifyFCMPassThoughMessageCome(context: Context, map: Map<String, String>) {
        val pushMsg = map["pushMsg"]
        val receiver = AssemblePushHelper.getMiPushReceiver(context)
        if (TextUtils.isEmpty(pushMsg) || receiver == null) {
            return
        }
        receiver.onReceivePassThroughMessage(context, AssemblePushHelper.parseMiPushMessage(pushMsg ?: ""))
    }

    @JvmStatic
    fun reportFCMMessageDelete() {
        MiTinyDataClient.upload(
            AssemblePushHelper.getSPErrorKey(AssemblePush.ASSEMBLE_PUSH_FCM) ?: "",
            "fcm",
            1L,
            "some fcm messages was deleted "
        )
    }

    @JvmStatic
    fun uploadToken(context: Context, token: String) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_FCM, token)
    }
}
