package com.xiaomi.mipush.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.channel.commonutils.string.MD5;
import com.xiaomi.push.clientreport.PerfMessageHelper;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.PushProvision;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.xmsf.runtime.PushRuntime;
import com.xiaomi.xmsf.runtime.PushRuntimeRegistrationTaskStore;
import com.xiaomi.xmsf.runtime.RegistrationIntentDispatcher;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.BootModeType;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushActionRegistration;
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.apache.thrift.TBase;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushServiceClient.class */
public class PushServiceClient {
    private static final int MAX_PENDING_MESSAGES_SIZE = 50;
    private static final int MIN_MIUI_PUSH_BIND_SERVICE_VERSION = 108;
    private static final int MIN_MIUI_PUSH_VERSION = 105;
    private static final int MIN_MIUI_PUSH_VERSION_JAR = 106;
    private static final int REQUEST_CACHE_SIZE = 10;
    private static final boolean isForHybridFrame = false;
    private static PushServiceClient sInstance;
    private Handler handler;
    private Messenger mClientMessenger;
    private Context mContext;
    private boolean mIsMiuiPushServiceEnabled;
    private static boolean isBind = false;
    private static final ArrayList<BufferedRequest> sPendingRequest = new ArrayList<>();
    private List<Message> pendingMessages = new ArrayList();
    private boolean isConnectingService = false;
    private Intent registerTask = null;
    private Integer mDeviceProvisioned = null;
    private String mSession = null;

    /* JADX INFO: renamed from: com.xiaomi.mipush.sdk.PushServiceClient$4, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushServiceClient$4.class */
    static /* synthetic */ class AnonymousClass4 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$mipush$sdk$RetryType;

        static {
            int[] iArr = new int[RetryType.values().length];
            $SwitchMap$com$xiaomi$mipush$sdk$RetryType = iArr;
            try {
                iArr[RetryType.DISABLE_PUSH.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$RetryType[RetryType.ENABLE_PUSH.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$RetryType[RetryType.UPLOAD_HUAWEI_TOKEN.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$RetryType[RetryType.UPLOAD_FCM_TOKEN.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$RetryType[RetryType.UPLOAD_COS_TOKEN.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$xiaomi$mipush$sdk$RetryType[RetryType.UPLOAD_FTOS_TOKEN.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushServiceClient$BufferedRequest.class */
    static class BufferedRequest<T extends TBase<T, ?>> {
        ActionType actionType;
        boolean encrypt;
        T message;

        BufferedRequest() {
        }
    }

    private PushServiceClient(Context context) {
        this.mIsMiuiPushServiceEnabled = false;
        this.handler = null;
        this.mContext = context.getApplicationContext();
        this.mIsMiuiPushServiceEnabled = serviceInstalled();
        isBind = useBind();
        this.handler = new Handler(Looper.getMainLooper()) { // from class: com.xiaomi.mipush.sdk.PushServiceClient.1
            @Override // android.os.Handler
            public void dispatchMessage(Message message) {
                switch (message.what) {
                    case 19:
                        String str = (String) message.obj;
                        int i = message.arg1;
                        synchronized (OperatePushHelper.class) {
                            if (OperatePushHelper.getInstance(PushServiceClient.this.mContext).isMessageOperating(str)) {
                                if (OperatePushHelper.getInstance(PushServiceClient.this.mContext).getRetryCount(str) < 10) {
                                    if (RetryType.DISABLE_PUSH.ordinal() == i && OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(PushServiceClient.this.mContext).getSyncStatus(RetryType.DISABLE_PUSH))) {
                                        PushServiceClient.this.retryPolicy(str, RetryType.DISABLE_PUSH, true, null);
                                    } else if (RetryType.ENABLE_PUSH.ordinal() == i && OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(PushServiceClient.this.mContext).getSyncStatus(RetryType.ENABLE_PUSH))) {
                                        PushServiceClient.this.retryPolicy(str, RetryType.ENABLE_PUSH, true, null);
                                    } else if (RetryType.UPLOAD_HUAWEI_TOKEN.ordinal() == i && OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(PushServiceClient.this.mContext).getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN))) {
                                        PushServiceClient.this.retryPolicy(str, RetryType.UPLOAD_HUAWEI_TOKEN, false, PushServiceClient.this.getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_HUAWEI));
                                    } else if (RetryType.UPLOAD_FCM_TOKEN.ordinal() == i && OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(PushServiceClient.this.mContext).getSyncStatus(RetryType.UPLOAD_FCM_TOKEN))) {
                                        PushServiceClient.this.retryPolicy(str, RetryType.UPLOAD_FCM_TOKEN, false, PushServiceClient.this.getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_FCM));
                                    } else if (RetryType.UPLOAD_COS_TOKEN.ordinal() == i && OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(PushServiceClient.this.mContext).getSyncStatus(RetryType.UPLOAD_COS_TOKEN))) {
                                        PushServiceClient.this.retryPolicy(str, RetryType.UPLOAD_COS_TOKEN, false, PushServiceClient.this.getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_COS));
                                    } else if (RetryType.UPLOAD_FTOS_TOKEN.ordinal() == i && OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(PushServiceClient.this.mContext).getSyncStatus(RetryType.UPLOAD_FTOS_TOKEN))) {
                                        PushServiceClient.this.retryPolicy(str, RetryType.UPLOAD_FTOS_TOKEN, false, PushServiceClient.this.getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_FTOS));
                                    }
                                    OperatePushHelper.getInstance(PushServiceClient.this.mContext).increaseRetryCount(str);
                                } else {
                                    OperatePushHelper.getInstance(PushServiceClient.this.mContext).removeOperateMessage(str);
                                }
                            }
                        }
                        return;
                    default:
                        return;
                }
            }
        };
        Intent intentCreateGlobalServiceIntent = createGlobalServiceIntent();
        if (intentCreateGlobalServiceIntent != null) {
            startServiceSafely(intentCreateGlobalServiceIntent);
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
                this.mContext.bindService(intent, new ServiceConnection() { // from class: com.xiaomi.mipush.sdk.PushServiceClient.3
                    @Override // android.content.ServiceConnection
                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                        synchronized (PushServiceClient.this) {
                            PushServiceClient.this.mClientMessenger = new Messenger(iBinder);
                            PushServiceClient.this.isConnectingService = false;
                            Iterator it = PushServiceClient.this.pendingMessages.iterator();
                            while (it.hasNext()) {
                                try {
                                    PushServiceClient.this.mClientMessenger.send((Message) it.next());
                                } catch (RemoteException e) {
                                    MyLog.e(e);
                                }
                            }
                            PushServiceClient.this.pendingMessages.clear();
                        }
                    }

                    @Override // android.content.ServiceConnection
                    public void onServiceDisconnected(ComponentName componentName) {
                        PushServiceClient.this.mClientMessenger = null;
                        PushServiceClient.this.isConnectingService = false;
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

    private void callService(Intent intent) {
        int intValue = OnlineConfig.getInstance(this.mContext).getIntValue(ConfigKey.ServiceBootMode.getValue(), BootModeType.START.getValue());
        int serviceBootMode = getServiceBootMode();
        boolean z = intValue == BootModeType.BIND.getValue() && isBind;
        int value = (z ? BootModeType.BIND : BootModeType.START).getValue();
        if (value != serviceBootMode) {
            sendServiceBootMode(value);
        }
        if (z) {
            bindServiceSafely(intent);
        } else {
            startServiceSafely(intent);
        }
    }

    private Intent createGlobalServiceIntent() {
        if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.mContext.getPackageName())) {
            return createGlobalServiceIntentForApp();
        }
        MyLog.v("pushChannel xmsf create own channel");
        return createMyPushChannelIntent();
    }

    private Intent createGlobalServiceIntentForApp() {
        if (shouldUseMIUIPush()) {
            MyLog.v("pushChannel app start miui china channel");
            return createMIUIPushChannelIntent();
        }
        MyLog.v("pushChannel app start  own channel");
        return createMyPushChannelIntent();
    }

    private Intent createMIUIPushChannelIntent() {
        Intent intent = new Intent();
        String packageName = this.mContext.getPackageName();
        intent.setPackage(PushConstants.PUSH_SERVICE_PACKAGE_NAME);
        intent.setClassName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, getPushServiceName());
        intent.putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName);
        disableMyPushService();
        return intent;
    }

    private Intent createMyPushChannelIntent() {
        Intent intent = new Intent();
        String packageName = this.mContext.getPackageName();
        enableMyPushService();
        intent.setComponent(new ComponentName(this.mContext, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR));
        intent.putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName);
        return intent;
    }

    private Intent createServiceIntent() {
        return (!shouldUseMIUIPush() || PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.mContext.getPackageName())) ? createMyPushChannelIntent() : createMIUIPushChannelIntent();
    }

    private void disableMyPushService() {
        try {
            PackageManager packageManager = this.mContext.getPackageManager();
            ComponentName componentName = new ComponentName(this.mContext, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR);
            if (packageManager.getComponentEnabledSetting(componentName) == 2) {
                return;
            }
            packageManager.setComponentEnabledSetting(componentName, 2, 1);
        } catch (Throwable th) {
        }
    }

    private void enableMyPushService() {
        try {
            PackageManager packageManager = this.mContext.getPackageManager();
            ComponentName componentName = new ComponentName(this.mContext, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR);
            if (packageManager.getComponentEnabledSetting(componentName) == 1) {
                return;
            }
            packageManager.setComponentEnabledSetting(componentName, 1, 1);
        } catch (Throwable th) {
        }
    }

    public static PushServiceClient getInstance(Context context) {
        PushServiceClient pushServiceClient;
        synchronized (PushServiceClient.class) {
            try {
                if (sInstance == null) {
                    sInstance = new PushServiceClient(context);
                }
                pushServiceClient = sInstance;
            } catch (Throwable th) {
                throw th;
            }
        }
        return pushServiceClient;
    }

    private String getPushServiceName() {
        try {
            return this.mContext.getPackageManager().getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4).versionCode >= 106 ? PushConstants.PUSH_SERVICE_CLASS_NAME_JAR : PushConstants.PUSH_SERVICE_CLASS_NAME;
        } catch (Exception e) {
            return PushConstants.PUSH_SERVICE_CLASS_NAME;
        }
    }

    private int getServiceBootMode() {
        int i;
        synchronized (this) {
            i = this.mContext.getSharedPreferences("mipush_extra", 0).getInt(Constants.EXTRA_KEY_BOOT_SERVICE_MODE, -1);
        }
        return i;
    }

    private boolean isAutoTry() {
        String packageName = this.mContext.getPackageName();
        boolean z = true;
        if (packageName.contains("miui") || packageName.contains("xiaomi")) {
            return true;
        }
        if ((this.mContext.getApplicationInfo().flags & 1) == 0) {
            z = false;
        }
        return z;
    }

    private Message parseToMessage(Intent intent) {
        Message messageObtain = Message.obtain();
        messageObtain.what = 17;
        messageObtain.obj = intent;
        return messageObtain;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void retryPolicy(String str, RetryType retryType, boolean z, HashMap<String, String> map) {
        XmPushActionNotification xmPushActionNotification;
        if (AppInfoHolder.getInstance(this.mContext).checkAppInfo() && Network.hasNetwork(this.mContext)) {
            XmPushActionNotification xmPushActionNotification2 = new XmPushActionNotification();
            xmPushActionNotification2.setRequireAck(true);
            Intent intentCreateServiceIntent = createServiceIntent();
            if (TextUtils.isEmpty(str)) {
                String strGeneratePacketID = PacketHelper.generatePacketID();
                xmPushActionNotification2.setId(strGeneratePacketID);
                XmPushActionNotification xmPushActionNotification3 = z ? new XmPushActionNotification(strGeneratePacketID, true) : null;
                synchronized (OperatePushHelper.class) {
                    try {
                        OperatePushHelper.getInstance(this.mContext).resetOperateMessage(strGeneratePacketID);
                    } catch (Throwable th) {
                        throw th;
                    }
                }
                xmPushActionNotification = xmPushActionNotification3;
                str = strGeneratePacketID;
            } else {
                xmPushActionNotification2.setId(str);
                xmPushActionNotification = z ? new XmPushActionNotification(str, true) : null;
            }
            switch (AnonymousClass4.$SwitchMap$com$xiaomi$mipush$sdk$RetryType[retryType.ordinal()]) {
                case 1:
                    xmPushActionNotification2.setType(NotificationType.DisablePushMessage.value);
                    xmPushActionNotification.setType(NotificationType.DisablePushMessage.value);
                    if (map != null) {
                        xmPushActionNotification2.setExtra(map);
                        xmPushActionNotification.setExtra(map);
                    }
                    intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE);
                    break;
                case 2:
                    xmPushActionNotification2.setType(NotificationType.EnablePushMessage.value);
                    xmPushActionNotification.setType(NotificationType.EnablePushMessage.value);
                    if (map != null) {
                        xmPushActionNotification2.setExtra(map);
                        xmPushActionNotification.setExtra(map);
                    }
                    intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE);
                    break;
                case 3:
                case 4:
                case 5:
                case 6:
                    xmPushActionNotification2.setType(NotificationType.ThirdPartyRegUpdate.value);
                    if (map != null) {
                        xmPushActionNotification2.setExtra(map);
                    }
                    break;
            }
            MyLog.persist("type:" + retryType + ", " + str);
            xmPushActionNotification2.setAppId(AppInfoHolder.getInstance(this.mContext).getAppID());
            xmPushActionNotification2.setPackageName(this.mContext.getPackageName());
            sendMessage(xmPushActionNotification2, ActionType.Notification, false, null);
            if (z) {
                xmPushActionNotification.setAppId(AppInfoHolder.getInstance(this.mContext).getAppID());
                xmPushActionNotification.setPackageName(this.mContext.getPackageName());
                byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(PushContainerHelper.generateRequestContainer(this.mContext, xmPushActionNotification, ActionType.Notification, false, this.mContext.getPackageName(), AppInfoHolder.getInstance(this.mContext).getAppID()));
                if (bArrConvertThriftObjectToBytes != null) {
                    PerfMessageHelper.collectPerfData(this.mContext.getPackageName(), this.mContext, xmPushActionNotification, ActionType.Notification, bArrConvertThriftObjectToBytes.length);
                    intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArrConvertThriftObjectToBytes);
                    intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true);
                    intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, AppInfoHolder.getInstance(this.mContext).getAppID());
                    intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_APP_TOKEN, AppInfoHolder.getInstance(this.mContext).getAppToken());
                    callService(intentCreateServiceIntent);
                }
            }
            Message messageObtain = Message.obtain();
            messageObtain.what = 19;
            int iOrdinal = retryType.ordinal();
            messageObtain.obj = str;
            messageObtain.arg1 = iOrdinal;
            this.handler.sendMessageDelayed(messageObtain, 5000L);
        }
    }

    private void saveServiceBootMode(int i) {
        synchronized (this) {
            this.mContext.getSharedPreferences("mipush_extra", 0).edit().putInt(Constants.EXTRA_KEY_BOOT_SERVICE_MODE, i).commit();
        }
    }

    private boolean serviceInstalled() {
        try {
            PackageInfo packageInfo = this.mContext.getPackageManager().getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4);
            if (packageInfo == null) {
                return false;
            }
            return packageInfo.versionCode >= 105;
        } catch (Throwable th) {
            return false;
        }
    }

    private void startServiceSafely(Intent intent) {
        try {
            if (MIUIUtils.isMIUI() || Build.VERSION.SDK_INT < 26) {
                this.mContext.startService(intent);
            } else {
                bindServiceSafely(intent);
            }
        } catch (Exception e) {
            MyLog.e(e);
        }
    }

    private boolean useBind() {
        if (!shouldUseMIUIPush()) {
            return true;
        }
        try {
            return this.mContext.getPackageManager().getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4).versionCode >= 108;
        } catch (Exception e) {
            return true;
        }
    }

    public <T extends TBase<T, ?>> void addPendRequest(T t, ActionType actionType, boolean z) {
        BufferedRequest bufferedRequest = new BufferedRequest();
        bufferedRequest.message = t;
        bufferedRequest.actionType = actionType;
        bufferedRequest.encrypt = z;
        ArrayList<BufferedRequest> arrayList = sPendingRequest;
        synchronized (arrayList) {
            arrayList.add(bufferedRequest);
            if (arrayList.size() > 10) {
                arrayList.remove(0);
            }
        }
    }

    public void awakePushService() {
        startServiceSafely(createServiceIntent());
    }

    public void clearLocalNotificationType() {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKAGE_NAME, this.mContext.getPackageName());
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_SIG, MD5.MD5_16(this.mContext.getPackageName()));
        callService(intentCreateServiceIntent);
    }

    public void clearNotification(int i) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKAGE_NAME, this.mContext.getPackageName());
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_NOTIFY_ID, i);
        callService(intentCreateServiceIntent);
    }

    public void clearNotification(String str, String str2) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKAGE_NAME, this.mContext.getPackageName());
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_NOTIFY_TITLE, str);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_NOTIFY_DESCRIPTION, str2);
        callService(intentCreateServiceIntent);
    }

    public final void closePush() {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_DISABLE_PUSH);
        callService(intentCreateServiceIntent);
    }

    public boolean isProvisioned() {
        boolean z = true;
        if (!shouldUseMIUIPush() || !isAutoTry()) {
            return true;
        }
        if (this.mDeviceProvisioned == null) {
            Integer numValueOf = Integer.valueOf(PushProvision.getInstance(this.mContext).getProvisioned());
            this.mDeviceProvisioned = numValueOf;
            if (numValueOf.intValue() == 0) {
                this.mContext.getContentResolver().registerContentObserver(PushProvision.getInstance(this.mContext).getProvisionedUri(), false, new ContentObserver(new Handler(Looper.getMainLooper())) { // from class: com.xiaomi.mipush.sdk.PushServiceClient.2
                    @Override // android.database.ContentObserver
                    public void onChange(boolean z2) {
                        PushServiceClient pushServiceClient = PushServiceClient.this;
                        pushServiceClient.mDeviceProvisioned = Integer.valueOf(PushProvision.getInstance(pushServiceClient.mContext).getProvisioned());
                        if (PushServiceClient.this.mDeviceProvisioned.intValue() != 0) {
                            PushServiceClient.this.mContext.getContentResolver().unregisterContentObserver(this);
                            if (Network.hasNetwork(PushServiceClient.this.mContext)) {
                                PushServiceClient.this.processRegisterTask();
                            }
                        }
                    }
                });
            }
        }
        if (this.mDeviceProvisioned.intValue() == 0) {
            z = false;
        }
        return z;
    }

    public void processPendRequest() {
        ArrayList<BufferedRequest> arrayList = sPendingRequest;
        synchronized (arrayList) {
            boolean z = Thread.currentThread() == Looper.getMainLooper().getThread();
            for (BufferedRequest bufferedRequest : arrayList) {
                sendMessage(bufferedRequest.message, bufferedRequest.actionType, bufferedRequest.encrypt, false, null, true);
                if (!z) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException e) {
                    }
                }
            }
            sPendingRequest.clear();
        }
    }

    public void processRegisterTask() {
        Intent intent = this.registerTask;
        this.registerTask = null;
        if (intent != null) {
            PushRuntimeRegistrationTaskStore.cache(this.mContext.getPackageName(), intent, "PushServiceClient.processRegisterTask", "legacy_cached_task", System.currentTimeMillis());
        }
        PushRuntimeRegistrationTaskStore.dispatchAll("PushServiceClient.processRegisterTask", new RegistrationIntentDispatcher() { // from class: com.xiaomi.mipush.sdk.PushServiceClient.5
            @Override // com.xiaomi.xmsf.runtime.RegistrationIntentDispatcher
            public boolean dispatch(String str, Intent intent2) {
                PushServiceClient.this.callService(intent2);
                return true;
            }
        });
    }

    public final void register(XmPushActionRegistration xmPushActionRegistration, boolean z) {
        PushClientReportManager.getInstance(this.mContext.getApplicationContext()).reportEvent(this.mContext.getPackageName(), ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, xmPushActionRegistration.getId(), ReportConstants.REGISTER_TYPE_CONSTRUCT_MSG, null);
        this.registerTask = null;
        AppInfoHolder.getInstance(this.mContext).appRegRequestId = xmPushActionRegistration.getId();
        PushRuntime.observeRegistrationRequest(this.mContext.getPackageName(), "PushServiceClient.register", z ? "env_changed" : "client_register", System.currentTimeMillis());
        Intent intentCreateServiceIntent = createServiceIntent();
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(PushContainerHelper.generateRequestContainer(this.mContext, xmPushActionRegistration, ActionType.Registration));
        if (bArrConvertThriftObjectToBytes == null) {
            MyLog.w("register fail, because msgBytes is null.");
            return;
        }
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_REGISTER_APP);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, AppInfoHolder.getInstance(this.mContext).getAppID());
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArrConvertThriftObjectToBytes);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_SESSION, this.mSession);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_ENV_CHANAGE, z);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_ENV_TYPE, AppInfoHolder.getInstance(this.mContext).getEnvType());
        if (Network.hasNetwork(this.mContext) && isProvisioned()) {
            PushRuntimeRegistrationTaskStore.clear(this.mContext.getPackageName());
            PushRuntime.requestConnection("PushServiceClient.register", "call_service");
            callService(intentCreateServiceIntent);
        } else {
            PushRuntimeRegistrationTaskStore.cache(
                this.mContext.getPackageName(),
                intentCreateServiceIntent,
                "PushServiceClient.register",
                Network.hasNetwork(this.mContext) ? "device_unprovisioned" : "network_unavailable",
                System.currentTimeMillis()
            );
        }
    }

    void send3rdPushHint(int i, String str) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_THIRDPARTY_HINT);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_THIRDPARTY_HINT_LEVEL, i);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_THIRDPARTY_HINT_DESC, str);
        startServiceSafely(intentCreateServiceIntent);
    }

    private HashMap<String, String> getAssemblePushExtraOrNull(AssemblePush assemblePush) {
        try {
            return AssemblePushHelper.getAssemblePushExtra(this.mContext, assemblePush);
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(e);
            return null;
        }
    }

    public final void sendAssemblePushTokenCommon(String str, RetryType retryType, AssemblePush assemblePush) {
        OperatePushHelper.getInstance(this.mContext).putSyncStatus(retryType, OperatePushHelper.SYNCING);
        retryPolicy(str, retryType, false, getAssemblePushExtraOrNull(assemblePush));
    }

    void sendDataCommon(Intent intent) {
        intent.fillIn(createServiceIntent(), 24);
        callService(intent);
    }

    public final <T extends TBase<T, ?>> void sendMessage(T t, ActionType actionType, PushMetaInfo pushMetaInfo) {
        sendMessage(t, actionType, !actionType.equals(ActionType.Registration), pushMetaInfo);
    }

    public final <T extends TBase<T, ?>> void sendMessage(T t, ActionType actionType, boolean z, PushMetaInfo pushMetaInfo) {
        sendMessage(t, actionType, z, true, pushMetaInfo, true);
    }

    public final <T extends TBase<T, ?>> void sendMessage(T t, ActionType actionType, boolean z, PushMetaInfo pushMetaInfo, boolean z2) {
        sendMessage(t, actionType, z, true, pushMetaInfo, z2);
    }

    public final <T extends TBase<T, ?>> void sendMessage(T t, ActionType actionType, boolean z, boolean z2, PushMetaInfo pushMetaInfo, boolean z3) {
        sendMessage(t, actionType, z, z2, pushMetaInfo, z3, this.mContext.getPackageName(), AppInfoHolder.getInstance(this.mContext).getAppID());
    }

    public final <T extends TBase<T, ?>> void sendMessage(T t, ActionType actionType, boolean z, boolean z2, PushMetaInfo pushMetaInfo, boolean z3, String str, String str2) {
        sendMessage(t, actionType, z, z2, pushMetaInfo, z3, str, str2, true);
    }

    public final <T extends TBase<T, ?>> void sendMessage(T t, ActionType actionType, boolean z, boolean z2, PushMetaInfo pushMetaInfo, boolean z3, String str, String str2, boolean z4) {
        if (!AppInfoHolder.getInstance(this.mContext).appRegistered()) {
            if (z2) {
                addPendRequest(t, actionType, z);
                return;
            } else {
                MyLog.w("drop the message before initialization.");
                return;
            }
        }
        XmPushActionContainer xmPushActionContainerGenerateRequestContainer = z4 ? PushContainerHelper.generateRequestContainer(this.mContext, t, actionType, z, str, str2) : PushContainerHelper.constructResponseContainer(this.mContext, t, actionType, z, str, str2);
        if (pushMetaInfo != null) {
            xmPushActionContainerGenerateRequestContainer.setMetaInfo(pushMetaInfo);
        }
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionContainerGenerateRequestContainer);
        if (bArrConvertThriftObjectToBytes == null) {
            MyLog.w("send message fail, because msgBytes is null.");
            return;
        }
        PerfMessageHelper.collectPerfData(this.mContext.getPackageName(), this.mContext, t, actionType, bArrConvertThriftObjectToBytes.length);
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_SEND_MESSAGE);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArrConvertThriftObjectToBytes);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, z3);
        callService(intentCreateServiceIntent);
    }

    public final void sendPushEnableDisableMessage(boolean z) {
        sendPushEnableDisableMessage(z, null);
    }

    public final void sendPushEnableDisableMessage(boolean z, String str) {
        if (z) {
            OperatePushHelper.getInstance(this.mContext).putSyncStatus(RetryType.DISABLE_PUSH, OperatePushHelper.SYNCING);
            OperatePushHelper.getInstance(this.mContext).putSyncStatus(RetryType.ENABLE_PUSH, "");
            retryPolicy(str, RetryType.DISABLE_PUSH, true, null);
        } else {
            OperatePushHelper.getInstance(this.mContext).putSyncStatus(RetryType.ENABLE_PUSH, OperatePushHelper.SYNCING);
            OperatePushHelper.getInstance(this.mContext).putSyncStatus(RetryType.DISABLE_PUSH, "");
            retryPolicy(str, RetryType.ENABLE_PUSH, true, null);
        }
    }

    public boolean sendServiceBootMode(int i) {
        if (!AppInfoHolder.getInstance(this.mContext).checkAppInfo()) {
            return false;
        }
        saveServiceBootMode(i);
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setId(PacketHelper.generatePacketID());
        xmPushActionNotification.setAppId(AppInfoHolder.getInstance(this.mContext).getAppID());
        xmPushActionNotification.setPackageName(this.mContext.getPackageName());
        xmPushActionNotification.setType(NotificationType.ClientABTest.value);
        xmPushActionNotification.extra = new HashMap();
        xmPushActionNotification.extra.put("boot_mode", i + "");
        getInstance(this.mContext).sendMessage(xmPushActionNotification, ActionType.Notification, false, null);
        return true;
    }

    public final void sendTinyData(ClientUploadDataItem clientUploadDataItem) {
        Intent intentCreateServiceIntent = createServiceIntent();
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadDataItem);
        if (bArrConvertThriftObjectToBytes == null) {
            MyLog.w("send TinyData failed, because tinyDataBytes is null.");
            return;
        }
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_SEND_TINYDATA);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArrConvertThriftObjectToBytes);
        startServiceSafely(intentCreateServiceIntent);
    }

    public void setLocalNotificationType(int i) {
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_PACKAGE_NAME, this.mContext.getPackageName());
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_NOTIFY_TYPE, i);
        intentCreateServiceIntent.putExtra(PushConstants.EXTRA_SIG, MD5.MD5_16(this.mContext.getPackageName() + i));
        callService(intentCreateServiceIntent);
    }

    public boolean shouldUseMIUIPush() {
        boolean z = true;
        if (!this.mIsMiuiPushServiceEnabled || 1 != AppInfoHolder.getInstance(this.mContext).getEnvType()) {
            z = false;
        }
        return z;
    }

    public final void unregister(XmPushActionUnRegistration xmPushActionUnRegistration) {
        byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(PushContainerHelper.generateRequestContainer(this.mContext, xmPushActionUnRegistration, ActionType.UnRegistration));
        if (bArrConvertThriftObjectToBytes == null) {
            MyLog.w("unregister fail, because msgBytes is null.");
            return;
        }
        Intent intentCreateServiceIntent = createServiceIntent();
        intentCreateServiceIntent.setAction(PushConstants.MIPUSH_ACTION_UNREGISTER_APP);
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, AppInfoHolder.getInstance(this.mContext).getAppID());
        intentCreateServiceIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArrConvertThriftObjectToBytes);
        callService(intentCreateServiceIntent);
    }
}
