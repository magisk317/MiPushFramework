package com.xiaomi.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.mipush.sdk.AppInfoHolder;
import com.xiaomi.mipush.sdk.COSPushHelper;
import com.xiaomi.mipush.sdk.FTOSPushHelper;
import com.xiaomi.mipush.sdk.HWPushHelper;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.mipush.sdk.OperatePushHelper;
import com.xiaomi.mipush.sdk.PushServiceClient;
import com.xiaomi.mipush.sdk.RetryType;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.push.service.ServiceClient;
import com.xiaomi.smack.util.TrafficUtils;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/receivers/NetworkStatusReceiver.class */
public class NetworkStatusReceiver extends BroadcastReceiver {
    private boolean isXmlRegister;
    private static int sCorePoolSize = 1;
    private static int sMaximumPoolSize = 1;
    private static int sKeepAliveTime = 2;
    private static BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(sCorePoolSize, sMaximumPoolSize, sKeepAliveTime, TimeUnit.SECONDS, queue);
    private static boolean isRegister = false;

    public NetworkStatusReceiver() {
        this.isXmlRegister = false;
        this.isXmlRegister = true;
    }

    public NetworkStatusReceiver(Object obj) {
        this.isXmlRegister = false;
        isRegister = true;
    }

    public static boolean isRegister() {
        return isRegister;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyNetworkChanged(Context context) {
        if (!PushServiceClient.getInstance(context).shouldUseMIUIPush() && AppInfoHolder.getInstance(context).appRegistered() && !AppInfoHolder.getInstance(context).invalidated()) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(context, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR));
                intent.setAction(PushServiceConstants.ACTION_NETWORK_STATUS_CHANGED);
                ServiceClient.getInstance(context).startServiceSafely(intent);
            } catch (Exception e) {
                MyLog.e(e);
            }
        }
        TrafficUtils.notifyNetworkChanage(context);
        if (Network.hasNetwork(context) && PushServiceClient.getInstance(context).isProvisioned()) {
            PushServiceClient.getInstance(context).processRegisterTask();
        }
        if (Network.hasNetwork(context)) {
            if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(context).getSyncStatus(RetryType.DISABLE_PUSH))) {
                MiPushClient.disablePush(context);
            }
            if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(context).getSyncStatus(RetryType.ENABLE_PUSH))) {
                MiPushClient.enablePush(context);
            }
            if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(context).getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN))) {
                MiPushClient.syncAssemblePushToken(context);
            }
            if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(context).getSyncStatus(RetryType.UPLOAD_FCM_TOKEN))) {
                MiPushClient.syncAssembleFCMPushToken(context);
            }
            if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(context).getSyncStatus(RetryType.UPLOAD_COS_TOKEN))) {
                MiPushClient.syncAssembleCOSPushToken(context);
            }
            if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(context).getSyncStatus(RetryType.UPLOAD_FTOS_TOKEN))) {
                MiPushClient.syncAssembleFTOSPushToken(context);
            }
            if (HWPushHelper.needConnect() && HWPushHelper.shouldTryConnect(context)) {
                HWPushHelper.setConnectTime(context);
                HWPushHelper.registerHuaWeiAssemblePush(context);
            }
            COSPushHelper.doInNetworkChange(context);
            FTOSPushHelper.doInNetworkChange(context);
        }
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(final Context context, Intent intent) {
        if (this.isXmlRegister) {
            return;
        }
        threadPoolExecutor.execute(new Runnable() { // from class: com.xiaomi.push.service.receivers.NetworkStatusReceiver.1
            @Override // java.lang.Runnable
            public void run() {
                NetworkStatusReceiver.this.notifyNetworkChanged(context);
            }
        });
    }
}
