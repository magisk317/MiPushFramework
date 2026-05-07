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
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/AppLayerProcessDataIml.java
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
