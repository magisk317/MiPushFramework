package com.magisk317

import android.os.Build
import com.magisk317.XMPushUtils
import com.xiaomi.push.service.MyMIPushNotificationHelper
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.ActionType

object SdkNotificationCompat {
    @JvmStatic
    fun shouldUseModernHelper(payload: ByteArray?): Boolean {
        if (payload == null || payload.isEmpty()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val container = XMPushUtils.packToContainer(payload) ?: return false
        return container.action == ActionType.SendMessage || container.action == ActionType.Notification
    }

    @JvmStatic
    fun notifyWithModernHelper(pushService: XMPushService, payload: ByteArray?) {
        if (payload == null || payload.isEmpty()) return
        MyMIPushNotificationHelper.notifyPushMessage(pushService, payload)
    }
}
