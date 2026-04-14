package com.xiaomi.mipush.sdk

import android.content.Context
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification

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
