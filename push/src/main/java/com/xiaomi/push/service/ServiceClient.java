package com.xiaomi.push.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.BuildSettings;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.smack.packet.IQ;
import com.xiaomi.smack.packet.Presence;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.http.NameValuePair;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/ServiceClient.class */
public class ServiceClient {
    private static final int MAX_PENDING_MESSAGES_SIZE = 50;
    private static final int MIN_MIUI_PUSH_VERSION = 104;
    private static final int MIN_MIUI_PUSH_VERSION_JAR = 106;
    private static ServiceClient sInstance;
    private static String sSession = null;
    private Messenger mClientMessenger;
    private Context mContext;
    private boolean mIsMiuiPushServiceEnabled;
    private List<Message> pendingMessages = new ArrayList();
    private boolean isConnectingService = false;
    private Messenger mMessenger = new Messenger(new Handler(Looper.getMainLooper()) { // from class: com.xiaomi.push.service.ServiceClient.1
        @Override // android.os.Handler
        public void handleMessage(Message message) {
            super.handleMessage(message);
        }
    });

    private ServiceClient(Context context) {
        this.mIsMiuiPushServiceEnabled = false;
        this.mContext = context.getApplicationContext();
        if (serviceInstalled()) {
            MyLog.v("use miui push service");
            this.mIsMiuiPushServiceEnabled = true;
        }
    }

    private void bindServiceSafely(Intent intent) {
        synchronized (this) {
            if (this.isConnectingService) {
                Message toMessage = parseToMessage(intent);
                if (this.pendingMessages.size() >= MAX_PENDING_MESSAGES_SIZE) {
                    this.pendingMessages.remove(0);
                }
                this.pendingMessages.add(toMessage);
                return;
            }
            if (this.mClientMessenger == null) {
                this.mContext.bindService(intent, new ServiceConnection() { // from class: com.xiaomi.push.service.ServiceClient.2
                    @Override // android.content.ServiceConnection
                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                        synchronized (ServiceClient.this) {
                            ServiceClient.this.mClientMessenger = new Messenger(iBinder);
                            ServiceClient.this.isConnectingService = false;
                            Iterator it = ServiceClient.this.pendingMessages.iterator();
                            while (it.hasNext()) {
                                try {
                                    ServiceClient.this.mClientMessenger.send((Message) it.next());
                                } catch (RemoteException e) {
                                    MyLog.e(e);
                                }
                            }
                            ServiceClient.this.pendingMessages.clear();
                        }
                    }

                    @Override // android.content.ServiceConnection
                    public void onServiceDisconnected(ComponentName componentName) {
                        ServiceClient.this.mClientMessenger = null;
                        ServiceClient.this.isConnectingService = false;
                    }
                }, 1);
                this.isConnectingService = true;
                this.pendingMessages.clear();
                this.pendingMessages.add(parseToMessage(intent));
            } else {
                try {
                    this.mClientMessenger.send(parseToMessage(intent));
                } catch (RemoteException e) {
                    this.mClientMessenger = null;
                    this.isConnectingService = false;
                }
            }
        }
    }

    private Intent createServiceIntent() {
        return ServiceClientIntentSupport.createServiceIntent(this.mContext, isMiuiPushServiceEnabled());
    }

    public static ServiceClient getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ServiceClient(context);
        }
        return sInstance;
    }

    public static String getSession() {
        return sSession;
    }

    @Deprecated
    private String joinAttributes(List<NameValuePair> list) {
        return joinAttributes(ServiceClientIntentSupport.translate(list));
    }

    private String joinAttributes(Map<String, String> map) {
        return ServiceClientIntentSupport.joinAttributes(map);
    }

    private Message parseToMessage(Intent intent) {
        Message messageObtain = Message.obtain();
        messageObtain.what = 17;
        messageObtain.obj = intent;
        return messageObtain;
    }

    @Deprecated
    private void putOpenParamsIntoIntent(Intent intent, String str, String str2, String str3, String str4, String str5, boolean z, List<NameValuePair> list, List<NameValuePair> list2) {
        putOpenParamsIntoIntent(intent, str, str2, str3, str4, str5, z, ServiceClientIntentSupport.translate(list), ServiceClientIntentSupport.translate(list2));
    }

    private void putOpenParamsIntoIntent(Intent intent, String str, String str2, String str3, String str4, String str5, boolean z, Map<String, String> map, Map<String, String> map2) {
        ServiceClientIntentSupport.putOpenParamsIntoIntent(intent, str, str2, str3, str4, str5, z, map, map2, sSession, this.mMessenger);
    }

    private boolean serviceInstalled() {
        if (BuildSettings.IsTestBuild) {
            return false;
        }
        try {
            PackageInfo packageInfo = this.mContext.getPackageManager().getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4);
            if (packageInfo == null) {
                return false;
            }
            return packageInfo.versionCode >= 104;
        } catch (Exception e) {
            return false;
        }
    }

    public static void setSession(String str) {
        sSession = str;
    }

    private Map<String, String> translate(List<NameValuePair> list) {
        return ServiceClientIntentSupport.translate(list);
    }

    public boolean batchSendMessage(com.xiaomi.smack.packet.Message[] messageArr, boolean z) {
        if (!Network.hasNetwork(this.mContext)) {
            return false;
        }
        Intent intentCreateServiceIntent = createServiceIntent();
        Bundle[] bundleArr = ServiceClientPacketSupport.buildMessageBundles(messageArr);
        if (bundleArr.length <= 0) {
            return false;
        }
        intentCreateServiceIntent.setAction(PushConstants.ACTION_BATCH_SEND_MESSAGE);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_SESSION, sSession);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKETS, bundleArr);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_ENCYPT, z);
        return startServiceSafely(intentCreateServiceIntent);
    }

    public void checkAlive() {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushServiceConstants.ACTION_CHECK_ALIVE);
        startServiceSafely(intentCreateServiceIntent);
    }

    public boolean closeChannel() {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.ACTION_CLOSE_CHANNEL);
        return startServiceSafely(intentCreateServiceIntent);
    }

    public boolean closeChannel(String str) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.ACTION_CLOSE_CHANNEL);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_CHANNEL_ID, str);
        return startServiceSafely(intentCreateServiceIntent);
    }

    public boolean closeChannel(String str, String str2) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.ACTION_CLOSE_CHANNEL);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_CHANNEL_ID, str);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_USER_ID, str2);
        return startServiceSafely(intentCreateServiceIntent);
    }

    @Deprecated
    public boolean forceReconnection(String str, String str2, String str3, String str4, String str5, boolean z, List<NameValuePair> list, List<NameValuePair> list2) {
        return forceReconnection(str, str2, str3, str4, str5, z, ServiceClientIntentSupport.translate(list), ServiceClientIntentSupport.translate(list2));
    }

    public boolean forceReconnection(String str, String str2, String str3, String str4, String str5, boolean z, Map<String, String> map, Map<String, String> map2) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.ACTION_FORCE_RECONNECT);
        putOpenParamsIntoIntent(intentCreateServiceIntent, str, str2, str3, str4, str5, z, map, map2);
        return startServiceSafely(intentCreateServiceIntent);
    }

    public boolean isMiuiPushServiceEnabled() {
        return this.mIsMiuiPushServiceEnabled;
    }

    public int openChannel(String str, String str2, String str3, String str4, String str5, Map<String, String> map, Map<String, String> map2, boolean z) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.ACTION_OPEN_CHANNEL);
        putOpenParamsIntoIntent(intentCreateServiceIntent, str, str2, str3, str4, str5, z, map, map2);
        startServiceSafely(intentCreateServiceIntent);
        return 0;
    }

    @Deprecated
    public int openChannel(String str, String str2, String str3, String str4, String str5, boolean z, List<NameValuePair> list, List<NameValuePair> list2) {
        return openChannel(str, str2, str3, str4, str5, ServiceClientIntentSupport.translate(list), ServiceClientIntentSupport.translate(list2), z);
    }

    @Deprecated
    public void resetConnection(String str, String str2, String str3, String str4, String str5, boolean z, List<NameValuePair> list, List<NameValuePair> list2) {
        resetConnection(str, str2, str3, str4, str5, z, ServiceClientIntentSupport.translate(list), ServiceClientIntentSupport.translate(list2));
    }

    public void resetConnection(String str, String str2, String str3, String str4, String str5, boolean z, Map<String, String> map, Map<String, String> map2) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.ACTION_RESET_CONNECTION);
        putOpenParamsIntoIntent(intentCreateServiceIntent, str, str2, str3, str4, str5, z, map, map2);
        startServiceSafely(intentCreateServiceIntent);
    }

    public boolean sendIQ(IQ iq) {
        if (!Network.hasNetwork(this.mContext)) {
            return false;
        }
        Intent intentCreateServiceIntent = createServiceIntent();
        Bundle bundle = ServiceClientPacketSupport.buildPacketBundle(iq);
        if (bundle == null) {
            return false;
        }
        intentCreateServiceIntent.setAction(PushConstants.ACTION_SEND_IQ);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_SESSION, sSession);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKET, bundle);
        return startServiceSafely(intentCreateServiceIntent);
    }

    public boolean sendMessage(com.xiaomi.smack.packet.Message message, boolean z) {
        if (!Network.hasNetwork(this.mContext)) {
            return false;
        }
        Intent intentCreateServiceIntent = createServiceIntent();
        Bundle bundle = ServiceClientPacketSupport.buildMessageBundle(message);
        if (bundle == null) {
            return false;
        }
        intentCreateServiceIntent.setAction(PushConstants.ACTION_SEND_MESSAGE);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_SESSION, sSession);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKET, bundle);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_ENCYPT, z);
        return startServiceSafely(intentCreateServiceIntent);
    }

    public boolean sendPresence(Presence presence) {
        if (!Network.hasNetwork(this.mContext)) {
            return false;
        }
        Intent intentCreateServiceIntent = createServiceIntent();
        Bundle bundle = ServiceClientPacketSupport.buildPacketBundle(presence);
        if (bundle == null) {
            return false;
        }
        intentCreateServiceIntent.setAction(PushConstants.ACTION_SEND_PRESENCE);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_SESSION, sSession);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKET, bundle);
        return startServiceSafely(intentCreateServiceIntent);
    }

    public boolean startServiceSafely(Intent intent) {
        try {
            if (MIUIUtils.isMIUI() || Build.VERSION.SDK_INT < 26) {
                this.mContext.startService(intent);
                return true;
            }
            bindServiceSafely(intent);
            return true;
        } catch (Exception e) {
            MyLog.e(e);
            return false;
        }
    }

    @Deprecated
    public void updateChannelInfo(String str, List<NameValuePair> list, List<NameValuePair> list2) {
        updateChannelInfo(str, translate(list), translate(list2));
    }

    public void updateChannelInfo(String str, Map<String, String> map, Map<String, String> map2) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.ACTION_UPDATE_CHANNEL_INFO);
        if (map != null) {
            String strJoinAttributes = joinAttributes(map);
            if (!TextUtils.isEmpty(strJoinAttributes)) {
                intentCreateServiceIntent.putExtra(PushConstants.EXTRA_CLIENT_ATTR, strJoinAttributes);
            }
        }
        if (map2 != null) {
            String strJoinAttributes2 = joinAttributes(map2);
            if (!TextUtils.isEmpty(strJoinAttributes2)) {
                intentCreateServiceIntent.putExtra(PushConstants.EXTRA_CLOUD_ATTR, strJoinAttributes2);
            }
        }
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_CHANNEL_ID, str);
        startServiceSafely(intentCreateServiceIntent);
    }
}
