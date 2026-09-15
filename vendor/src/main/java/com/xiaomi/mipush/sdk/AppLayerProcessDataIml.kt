package com.xiaomi.mipush.sdk

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.awake.AwakeDataHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper
import com.xiaomi.push.service.awake.module.AwakeManager
import com.xiaomi.push.service.awake.module.IProcessData
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import java.util.HashMap

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class AppLayerProcessDataIml : IProcessData {
    override fun sendByTinyData(context: Context, map: HashMap<String, String>) {
        MiTinyDataClient.upload(PushConstants.CATEGORY_AWAKE, PushConstants.WAKE_UP_APP, 1L, AwakeDataHelper.getString(map))
        MyLog.w("MoleInfo：　send data in app layer")
    }

    override fun sendDirectly(context: Context, map: HashMap<String, String>) {
        val notification = XmPushActionNotification().apply {
            setAppId(AwakeManager.getInstance(context).appId)
            setPackageName(AwakeManager.getInstance(context).packageName)
            setType(NotificationType.AwakeAppResponse.value)
            setId(PacketHelper.generatePacketID())
            extra = map
        }
        PushServiceClient.getInstance(context).sendMessage(notification, ActionType.Notification, true, null, true)
        MyLog.w("MoleInfo：　send data in app layer")
    }

    override fun shouldDoLast(context: Context, map: HashMap<String, String>) {
        MyLog.w("MoleInfo：　" + AwakeDataHelper.obfuscateLogContent(map))
        val eventType = map[AwakeUploadHelper.KEY_EVENT_TYPE]
        val awakeInfo = map[AwakeUploadHelper.KEY_AWAKE_INFO]
        if (1007.toString() == eventType) {
            AwakeHelper.sendPingByWakeUpApp(context, awakeInfo)
        }
    }
}
