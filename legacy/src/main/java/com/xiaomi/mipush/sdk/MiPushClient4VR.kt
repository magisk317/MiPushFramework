package com.xiaomi.mipush.sdk

import android.content.Context
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification

/*
 * Local legacy VR upload helper retained for compatibility.
 * No stock 7.4.67-C or 2026-04-13 current override same-path source was found in the dump.
 */
object MiPushClient4VR {
    private const val EXTRA_VR_DATA = "data"

    @JvmStatic
    fun uploadData(context: Context, data: String) {
        XmPushActionNotification().apply {
            type = NotificationType.VRUpload.value
            appId = AppInfoHolder.getInstance(context).appID
            packageName = context.packageName
            putToExtra(EXTRA_VR_DATA, data)
            id = PacketHelper.generatePacketID()
            PushServiceClient.getInstance(context).sendMessage(this, ActionType.Notification, null)
        }
    }
}
