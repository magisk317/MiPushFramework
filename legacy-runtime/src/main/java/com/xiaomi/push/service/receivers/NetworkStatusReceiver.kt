package com.xiaomi.push.service.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.mipush.sdk.AppInfoHolder
import com.xiaomi.mipush.sdk.COSPushHelper
import com.xiaomi.mipush.sdk.FTOSPushHelper
import com.xiaomi.mipush.sdk.HWPushHelper
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.mipush.sdk.OperatePushHelper
import com.xiaomi.mipush.sdk.PushServiceClient
import com.xiaomi.mipush.sdk.RetryType
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.ServiceClient
import com.xiaomi.smack.util.TrafficUtils
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/push/service/receivers/NetworkStatusReceiver.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/receivers/NetworkStatusReceiver.java
 */
class NetworkStatusReceiver @JvmOverloads constructor(dummy: Any? = null) : BroadcastReceiver() {
    private var isXmlRegister: Boolean = dummy == null

    init {
        if (dummy != null) {
            isRegister = true
        }
    }

    private fun notifyNetworkChanged(context: Context) {
        if (!PushServiceClient.getInstance(context).shouldUseMIUIPush() &&
            AppInfoHolder.getInstance(context).appRegistered() &&
            !AppInfoHolder.getInstance(context).invalidated()
        ) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(context, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR)
                    action = PushServiceConstants.ACTION_NETWORK_STATUS_CHANGED
                }
                ServiceClient.getInstance(context).startServiceSafely(intent)
            } catch (e: Exception) {
                MyLog.e(e)
            }
        }

        TrafficUtils.notifyNetworkChanage(context)

        if (Network.hasNetwork(context) && PushServiceClient.getInstance(context).isProvisioned()) {
            PushServiceClient.getInstance(context).processRegisterTask()
        }

        if (Network.hasNetwork(context)) {
            val operateHelper = OperatePushHelper.getInstance(context)
            if (OperatePushHelper.SYNCING == operateHelper.getSyncStatus(RetryType.DISABLE_PUSH)) {
                MiPushClient.disablePush(context)
            }
            if (OperatePushHelper.SYNCING == operateHelper.getSyncStatus(RetryType.ENABLE_PUSH)) {
                MiPushClient.enablePush(context)
            }
            if (OperatePushHelper.SYNCING == operateHelper.getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN)) {
                MiPushClient.syncAssemblePushToken(context)
            }
            if (OperatePushHelper.SYNCING == operateHelper.getSyncStatus(RetryType.UPLOAD_FCM_TOKEN)) {
                MiPushClient.syncAssembleFCMPushToken(context)
            }
            if (OperatePushHelper.SYNCING == operateHelper.getSyncStatus(RetryType.UPLOAD_COS_TOKEN)) {
                MiPushClient.syncAssembleCOSPushToken(context)
            }
            if (OperatePushHelper.SYNCING == operateHelper.getSyncStatus(RetryType.UPLOAD_FTOS_TOKEN)) {
                MiPushClient.syncAssembleFTOSPushToken(context)
            }
            if (HWPushHelper.needConnect() && HWPushHelper.shouldTryConnect(context)) {
                HWPushHelper.setConnectTime(context)
                HWPushHelper.registerHuaWeiAssemblePush(context)
            }
            COSPushHelper.doInNetworkChange(context)
            FTOSPushHelper.doInNetworkChange(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (isXmlRegister) {
            return
        }
        threadPoolExecutor.execute {
            notifyNetworkChanged(context)
        }
    }

    companion object {
        private const val sCorePoolSize = 1
        private const val sMaximumPoolSize = 1
        private const val sKeepAliveTime = 2L
        private val queue: BlockingQueue<Runnable> = LinkedBlockingQueue()
        private val threadPoolExecutor = ThreadPoolExecutor(
            sCorePoolSize,
            sMaximumPoolSize,
            sKeepAliveTime,
            TimeUnit.SECONDS,
            queue
        )
        private var isRegister = false

        @JvmStatic
        fun isRegister(): Boolean = isRegister
    }
}
