package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.lang.ref.WeakReference

class AwakeAppPingJob(
    private val notification: XmPushActionNotification,
    private val pushServiceReference: WeakReference<XMPushService>,
    private val isCache: Boolean,
) : ScheduledJobManager.Job() {
    override fun getJobId(): String = "22"

    override fun run() {
        val pushService = pushServiceReference.get() ?: return
        notification.id = PacketHelper.generatePacketID()
        notification.requireAck = false
        MyLog.v("MoleInfo aw_ping : send aw_Ping msg ${notification.id}")
        try {
            val packageName = notification.packageName
            pushService.sendMessage(
                packageName,
                XmPushThriftSerializeUtils.convertThriftObjectToBytes(
                    MIPushHelper.generateRequestContainer(
                        packageName,
                        notification.appId,
                        notification,
                        ActionType.Notification,
                    ),
                ),
                isCache,
            )
        } catch (e: Exception) {
            MyLog.e("MoleInfo aw_ping : send help app ping error${e}")
        }
    }
}
