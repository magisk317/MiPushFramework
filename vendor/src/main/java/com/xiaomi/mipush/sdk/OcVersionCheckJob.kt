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
 * Stock 7.4.67-C obfuscates the same implementation as `com.xiaomi.mipush.sdk.x`; it sends a
 * DailyCheckClientConfig notification containing current misc/plugin config versions. The comment
 * was updated after tracing stock pass-through `cloud_control_update` from `p9.l` to that class.
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
