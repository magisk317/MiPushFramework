package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.awake.AwakeDataHelper
import com.xiaomi.push.service.awake.module.AwakeManager
import com.xiaomi.push.service.awake.module.IProcessData
import com.xiaomi.tinyData.TinyDataManager
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils

class PushLayerProcessIml : IProcessData {
    override fun sendByTinyData(context: Context, map: HashMap<String, String>) {
        TinyDataManager.getInstance(context)?.upload(
            PushConstants.CATEGORY_AWAKE,
            PushConstants.WAKE_UP_APP,
            1L,
            AwakeDataHelper.getString(map),
        )
    }

    override fun sendDirectly(context: Context, map: HashMap<String, String>) {
        val notification = XmPushActionNotification().apply {
            appId = AwakeManager.getInstance(context).appId
            packageName = AwakeManager.getInstance(context).packageName
            type = NotificationType.AwakeAppResponse.value
            id = PacketHelper.generatePacketID()
            extra = map
        }
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                notification.packageName,
                notification.appId,
                notification,
                ActionType.Notification,
            ),
        )
        if (context !is XMPushService) {
            MyLog.w("MoleInfo : context is not correct in pushLayer ${notification.id}")
            return
        }
        MyLog.w("MoleInfo : send data directly in pushLayer ${notification.id}")
        context.sendMessage(context.packageName, payload, true)
    }

    override fun shouldDoLast(context: Context, map: HashMap<String, String>) {
        MyLog.w("MoleInfo：　${AwakeDataHelper.obfuscateLogContent(map)}")
    }
}
