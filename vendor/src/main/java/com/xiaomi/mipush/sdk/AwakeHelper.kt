package com.xiaomi.mipush.sdk

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.awake.AwakeUploadHelper
import com.xiaomi.push.service.awake.module.AwakeManager
import com.xiaomi.push.service.awake.module.HelpType
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import java.util.HashMap

object AwakeHelper {
    const val PING = 9999

    @JvmStatic
    fun doAWork(context: Context?, intent: Intent?, uri: Uri?) {
        if (context == null) {
            return
        }
        PushServiceClient.getInstance(context).awakePushService()
        val appContext = context.applicationContext
        if (AwakeManager.getInstance(appContext).sendDataIml == null) {
            AwakeManager.getInstance(appContext).setPackageInfo(
                AppInfoHolder.getInstance(appContext).appID,
                context.packageName,
                OnlineConfig.getInstance(appContext).getIntValue(ConfigKey.AwakeInfoUploadWaySwitch.value, 0),
                AppLayerProcessDataIml()
            )
            OnlineConfig.getInstance(context).addOCUpdateCallbacks(
                object : OnlineConfig.OCUpdateCallback(102, "awake online config") {
                    override fun onCallback() {
                        AwakeManager.getInstance(context)
                            .setOnLineCmd(OnlineConfig.getInstance(context).getIntValue(ConfigKey.AwakeInfoUploadWaySwitch.value, 0))
                    }
                }
            )
        }
        if (context is Activity && intent != null) {
            AwakeManager.getInstance(appContext).sendResult(HelpType.ACTIVITY, context, intent, "")
            return
        }
        if (context !is Service || intent == null) {
            if (uri == null || TextUtils.isEmpty(uri.toString())) {
                return
            }
            AwakeManager.getInstance(appContext).sendResult(HelpType.PROVIDER, context, Intent(), uri.toString())
            return
        }
        if (PushConstants.ACTION_WAKEUP == intent.action) {
            AwakeManager.getInstance(appContext).sendResult(HelpType.SERVICE_COMPONENT, context, intent, "")
        } else {
            AwakeManager.getInstance(appContext).sendResult(HelpType.SERVICE_ACTION, context, intent, "")
        }
    }

    @JvmStatic
    fun doAwAppLogic(context: Context, appId: String?, onlineCmd: Int, awakeInfo: String?) {
        val notification = XmPushActionNotification().apply {
            setAppId(appId)
            extra = HashMap<String, String>()
            extra[PushConstants.EXTRA_AWAKE_APP_ONLINE_CMD] = onlineCmd.toString()
            extra[PushConstants.EXTRA_AWAKE_APP_AWAKE_INFO] = awakeInfo
            setId(PacketHelper.generatePacketID())
        }
        val bytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(notification)
        if (bytes == null) {
            MyLog.w("send message fail, because msgBytes is null.")
            return
        }
        val intent = Intent().apply {
            action = PushConstants.ACTION_AWAKE_APP_LOGIC
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bytes)
        }
        PushServiceClient.getInstance(context).sendDataCommon(intent)
    }

    @JvmStatic
    fun <T : TBase<T, *>> sendAwakeAppPingMessage(context: Context, thriftObject: T, pingSwitch: Boolean, pingFrequency: Int) {
        val bytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(thriftObject)
        if (bytes == null) {
            MyLog.w("send message fail, because msgBytes is null.")
            return
        }
        val intent = Intent().apply {
            action = PushConstants.ACTION_AWAKE_APP_PING
            putExtra(PushConstants.EXTRA_AWAKE_APP_PING_SWITCH, pingSwitch)
            putExtra(PushConstants.EXTRA_AWAKE_APP_PING_FREQUENCY, pingFrequency)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bytes)
            putExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true)
        }
        PushServiceClient.getInstance(context).sendDataCommon(intent)
    }

    @JvmStatic
    fun sendPingByWakeUpApp(context: Context, awakeInfo: String?) {
        MyLog.w("aw_ping : send aw_ping cmd and content to push service from 3rd app")
        val map = HashMap<String, String>().apply {
            put(AwakeUploadHelper.KEY_AWAKE_INFO, awakeInfo ?: "")
            put(AwakeUploadHelper.KEY_EVENT_TYPE, PING.toString())
            put(AwakeUploadHelper.KEY_DESCRIPTION, "ping message")
        }
        val notification = XmPushActionNotification().apply {
            setAppId(AppInfoHolder.getInstance(context).appID)
            setPackageName(context.packageName)
            setType(NotificationType.AwakeAppResponse.value)
            setId(PacketHelper.generatePacketID())
            extra = map
        }
        doSendPingByWakedUpApp(context, notification)
    }

    private fun doSendPingByWakedUpApp(context: Context, notification: XmPushActionNotification) {
        var pingSwitch = OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.AwakeAppPingSwitch.value, false)
        var pingFrequency = OnlineConfig.getInstance(context).getIntValue(ConfigKey.AwakeAppPingFrequency.value, 0)
        if (pingFrequency >= 0 && pingFrequency < 30) {
            MyLog.v("aw_ping: frquency need > 30s.")
            pingFrequency = 30
        }
        if (pingFrequency < 0) {
            pingSwitch = false
        }
        if (!MIUIUtils.isMIUI()) {
            sendAwakeAppPingMessage(context, notification, pingSwitch, pingFrequency)
        } else if (pingSwitch) {
            ScheduledJobManager.getInstance(context.applicationContext).addRepeatJob(
                object : ScheduledJobManager.Job() {
                    override fun getJobId(): String = "22"

                    override fun run() {
                        notification.setId(PacketHelper.generatePacketID())
                        PushServiceClient.getInstance(context.applicationContext)
                            .sendMessage(notification, ActionType.Notification, true, null, true)
                    }
                },
                pingFrequency
            )
        }
    }
}
