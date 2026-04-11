package com.xiaomi.push.service;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.network.Fallback;
import com.xiaomi.network.HostFilter;
import com.xiaomi.network.HostManager;
import com.xiaomi.push.protobuf.ChannelConfig;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.ServiceConfig;
import com.xiaomi.push.thrift.ChannelStatsType;
import com.xiaomi.slim.Blob;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.util.StringUtils;
import com.xiaomi.stats.StatsHandler;
import com.xiaomi.stats.StatsHelper;
import com.xiaomi.xmsf.runtime.PushConnectionState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushHostManagerFactory.class */
public class PushHostManagerFactory extends ServiceConfig.Listener implements HostManager.HostManagerFactory {
    private static final long MIN_BUCKET_FETCH_DURATION = 3600000;
    private static final String V2 = "bucket_v2";
    private static final String VERSION = "2.2";
    private long lastFetchTime;
    private XMPushService pushService;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushHostManagerFactory$GslbHttpGet.class */
    static class GslbHttpGet implements HostManager.HttpGet {
        private static final String KEY_DROID_VERSION = "osver";
        private static final String KEY_MI = "mi";
        private static final String KEY_OS = "os";
        private static final String KEY_SDK_VERSION = "sdkver";

        GslbHttpGet() {
        }

        @Override // com.xiaomi.network.HostManager.HttpGet
        public String doGet(String str) throws IOException {
            PushGslbRequest gslbRequest = PushHostRuntime.buildGslbRequest(str, 41, Build.VERSION.SDK_INT, Build.MODEL, Build.VERSION.INCREMENTAL, SystemUtils.getMIUIType());
            MyLog.v("fetch bucket from : " + gslbRequest.getRequestUrl());
            URL url = new URL(gslbRequest.getRequestUrl());
            PushRuntime.observeChannelEvent(null, "gslb_fetch_request", "PushHostManagerFactory.GslbHttpGet");
            try {
                long jCurrentTimeMillis = System.currentTimeMillis();
                String strDownloadXml = Network.downloadXml(SystemUtils.getContext(), url);
                StatsHelper.statsGslb(gslbRequest.getStatsHostPort(), (int) (System.currentTimeMillis() - jCurrentTimeMillis), null);
                return strDownloadXml;
            } catch (IOException e) {
                PushRuntime.observeChannelEvent(null, "gslb_fetch_failed", "PushHostManagerFactory.GslbHttpGet");
                StatsHelper.statsGslb(gslbRequest.getStatsHostPort(), -1, e);
                throw e;
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushHostManagerFactory$PushHostManager.class */
    static class PushHostManager extends HostManager {
        protected PushHostManager(Context context, HostFilter hostFilter, HostManager.HttpGet httpGet, String str) {
            super(context, hostFilter, httpGet, str);
        }

        @Override // com.xiaomi.network.HostManager
        protected String getRemoteFallbackJSON(ArrayList<String> arrayList, String str, String str2, boolean z) throws IOException {
            try {
                if (StatsHandler.getInstance().isAllowStats()) {
                    str2 = ServiceConfig.getDeviceUUID();
                }
                return super.getRemoteFallbackJSON(arrayList, str, str2, z);
            } catch (IOException e) {
                StatsHelper.stats(0, ChannelStatsType.GSLB_ERR.getValue(), 1, null, Network.hasNetwork(sAppContext) ? 1 : 0);
                throw e;
            }
        }
    }

    PushHostManagerFactory(XMPushService xMPushService) {
        this.pushService = xMPushService;
    }

    public static void init(XMPushService xMPushService) {
        PushHostManagerFactory pushHostManagerFactory = new PushHostManagerFactory(xMPushService);
        ServiceConfig.getInstance().addListener(pushHostManagerFactory);
        synchronized (HostManager.class) {
            try {
                HostManager.setHostManagerFactory(pushHostManagerFactory);
                HostManager.init(xMPushService, null, new GslbHttpGet(), Blob.CLIENT_PING_ID, "push", VERSION);
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    @Override // com.xiaomi.network.HostManager.HostManagerFactory
    public HostManager createHostManager(Context context, HostFilter hostFilter, HostManager.HttpGet httpGet, String str) {
        return new PushHostManager(context, hostFilter, httpGet, str);
    }

    @Override // com.xiaomi.push.service.ServiceConfig.Listener
    public void onConfigChange(ChannelConfig.PushServiceConfig pushServiceConfig) {
    }

    @Override // com.xiaomi.push.service.ServiceConfig.Listener
    public void onConfigMsgReceive(ChannelMessage.PushServiceConfigMsg pushServiceConfigMsg) {
        Fallback fallbacksByHost;
        long currentTimeMillis = System.currentTimeMillis();
        PushBucketFetchPlan bucketFetchPlan = PushHostRuntime.decideBucketFetch(pushServiceConfigMsg.hasFetchBucket() && pushServiceConfigMsg.getFetchBucket(), this.lastFetchTime, currentTimeMillis, MIN_BUCKET_FETCH_DURATION);
        PushRuntime.observeChannelEvent(null, bucketFetchPlan.getEventAction(), "PushHostManagerFactory.onConfigMsgReceive");
        if (bucketFetchPlan.getShouldRefresh()) {
            MyLog.w("fetch bucket :" + pushServiceConfigMsg.getFetchBucket());
            this.lastFetchTime = currentTimeMillis;
            HostManager hostManager = HostManager.getInstance();
            hostManager.clear();
            try {
                hostManager.refreshFallbacks();
            } catch (Throwable th) {
                MyLog.e(th);
            }
            Connection currentConnection = this.pushService.getCurrentConnection();
            if (currentConnection == null) {
                PushBucketReconnectPlan bucketReconnectPlan = PushHostRuntime.decideBucketReconnect(false, null, new ArrayList<>());
                PushRuntime.observeChannelEvent(null, bucketReconnectPlan.getEventAction(), "PushHostManagerFactory.onConfigMsgReceive");
                return;
            }
            fallbacksByHost = hostManager.getFallbacksByHost(currentConnection.getConfiguration().getHost());
            ArrayList<String> hosts = fallbacksByHost == null ? new ArrayList<>() : fallbacksByHost.getHosts();
            PushBucketReconnectPlan bucketReconnectPlan = PushHostRuntime.decideBucketReconnect(true, currentConnection.getHost(), hosts);
            PushRuntime.observeChannelEvent(null, bucketReconnectPlan.getEventAction(), "PushHostManagerFactory.onConfigMsgReceive");
            if (!bucketReconnectPlan.getShouldReconnect()) {
                return;
            }
            MyLog.w("bucket changed, force reconnect");
            PushRuntime.observeConnectionState(PushConnectionState.Disconnected, "PushHostManagerFactory.onConfigMsgReceive", currentConnection.getHost(), bucketReconnectPlan.getConnectionStateReason());
            this.pushService.disconnect(0, null);
            this.pushService.scheduleConnect(false);
        }
    }
}
