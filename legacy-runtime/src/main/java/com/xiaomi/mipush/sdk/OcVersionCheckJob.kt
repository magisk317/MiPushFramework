package com.xiaomi.mipush.sdk

import android.content.Context
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.OnlineConfigHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ConfigListType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionCheckClientInfo
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/OcVersionCheckJob.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class OcVersionCheckJob(private val context: Context) : ScheduledJobManager.Job() {
    override fun getJobId(): String = "2"

    override fun run() {
        val onlineConfig = OnlineConfig.getInstance(context)
        val clientInfo = XmPushActionCheckClientInfo().apply {
            setMiscConfigVersion(OnlineConfigHelper.getVersion(onlineConfig, ConfigListType.MISC_CONFIG))
            setPluginConfigVersion(OnlineConfigHelper.getVersion(onlineConfig, ConfigListType.PLUGIN_CONFIG))
        }
        val notification = XmPushActionNotification("-1", false).apply {
            setType(NotificationType.DailyCheckClientConfig.value)
            setBinaryExtra(XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientInfo))
        }
        PushServiceClient.getInstance(context).sendMessage(notification, ActionType.Notification, null)
    }
}
