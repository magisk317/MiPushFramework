package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.TinyDataHelper;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MiTinyDataClient.class */
public class MiTinyDataClient {
    public static final String PENDING_REASON_APPID = "com.xiaomi.xmpushsdk.tinydataPending.appId";
    public static final String PENDING_REASON_CHANNEL = "com.xiaomi.xmpushsdk.tinydataPending.channel";
    public static final String PENDING_REASON_INIT = "com.xiaomi.xmpushsdk.tinydataPending.init";

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MiTinyDataClient$MiTinyDataClientImp.class */
    public static class MiTinyDataClientImp {
        private static final int MAX_PENDING_SIZE = 100;
        private static final int MAX_SIZE = 30720;
        private static final long MIN_SEND_TIMESPAN = 1000;
        private static final int MIN_TINYDATA_XMSF_VERSION = 108;
        private static volatile MiTinyDataClientImp sInstance;
        private String mChannel;
        private Context mContext;
        private Boolean mPushServiceAcceptTinyData;
        private SmoothSender mSmoothSender = new SmoothSender();
        private final ArrayList<ClientUploadDataItem> mPendingList = new ArrayList<>();

        /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MiTinyDataClient$MiTinyDataClientImp$SmoothSender.class */
        public class SmoothSender {
            private ScheduledFuture<?> mFuture;
            private ScheduledThreadPoolExecutor mExecutor = new ScheduledThreadPoolExecutor(1);
            public final ArrayList<ClientUploadDataItem> mList = new ArrayList<>();
            private final Runnable repeatTask = new Runnable() { // from class: com.xiaomi.mipush.sdk.MiTinyDataClient.MiTinyDataClientImp.SmoothSender.2
                @Override // java.lang.Runnable
                public void run() {
                    if (SmoothSender.this.mList.size() != 0) {
                        SmoothSender.this.doSend();
                    } else if (SmoothSender.this.mFuture != null) {
                        SmoothSender.this.mFuture.cancel(false);
                        SmoothSender.this.mFuture = null;
                    }
                }
            };

            public SmoothSender() {
            }

            /* JADX INFO: Access modifiers changed from: private */
            public void awake() {
                if (this.mFuture == null) {
                    this.mFuture = this.mExecutor.scheduleAtFixedRate(this.repeatTask, MiTinyDataClientImp.MIN_SEND_TIMESPAN, MiTinyDataClientImp.MIN_SEND_TIMESPAN, TimeUnit.MILLISECONDS);
                }
            }

            /* JADX INFO: Access modifiers changed from: private */
            public void doSend() {
                ClientUploadDataItem clientUploadDataItemRemove = this.mList.remove(0);
                for (XmPushActionNotification xmPushActionNotification : TinyDataHelper.pack(Arrays.asList(clientUploadDataItemRemove), MiTinyDataClientImp.this.mContext.getPackageName(), AppInfoHolder.getInstance(MiTinyDataClientImp.this.mContext).getAppID(), 30720)) {
                    MyLog.v("MiTinyDataClient Send item by PushServiceClient.sendMessage(XmActionNotification)." + clientUploadDataItemRemove.getId());
                    PushServiceClient.getInstance(MiTinyDataClientImp.this.mContext).sendMessage(xmPushActionNotification, ActionType.Notification, true, null);
                }
            }

            public void add(final ClientUploadDataItem clientUploadDataItem) {
                this.mExecutor.execute(new Runnable() { // from class: com.xiaomi.mipush.sdk.MiTinyDataClient.MiTinyDataClientImp.SmoothSender.1
                    @Override // java.lang.Runnable
                    public void run() {
                        SmoothSender.this.mList.add(clientUploadDataItem);
                        SmoothSender.this.awake();
                    }
                });
            }
        }

        private void addToPendingList(ClientUploadDataItem clientUploadDataItem) {
            synchronized (this.mPendingList) {
                if (!this.mPendingList.contains(clientUploadDataItem)) {
                    this.mPendingList.add(clientUploadDataItem);
                    if (this.mPendingList.size() > 100) {
                        this.mPendingList.remove(0);
                    }
                }
            }
        }

        private boolean checkSupportTinyData(Context context) {
            if (!PushServiceClient.getInstance(context).shouldUseMIUIPush()) {
                return true;
            }
            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4);
                if (packageInfo == null) {
                    return false;
                }
                return packageInfo.versionCode >= 108;
            } catch (Exception e) {
                return false;
            }
        }

        public static MiTinyDataClientImp getInstance() {
            if (sInstance == null) {
                synchronized (MiTinyDataClientImp.class) {
                    try {
                        if (sInstance == null) {
                            sInstance = new MiTinyDataClientImp();
                        }
                    } catch (Throwable th) {
                        throw th;
                    }
                }
            }
            return sInstance;
        }

        private boolean missingAppId(Context context) {
            return AppInfoHolder.getInstance(context).getAppID() == null && !checkSupportTinyData(this.mContext);
        }

        private boolean upload(ClientUploadDataItem clientUploadDataItem) {
            if (TinyDataHelper.verify(clientUploadDataItem, false)) {
                return false;
            }
            if (!this.mPushServiceAcceptTinyData.booleanValue()) {
                this.mSmoothSender.add(clientUploadDataItem);
                return true;
            }
            MyLog.v("MiTinyDataClient Send item by PushServiceClient.sendTinyData(ClientUploadDataItem)." + clientUploadDataItem.getId());
            PushServiceClient.getInstance(this.mContext).sendTinyData(clientUploadDataItem);
            return true;
        }

        public boolean alreadyInit() {
            return this.mContext != null;
        }

        public void init(Context context) {
            if (context == null) {
                MyLog.w("context is null, MiTinyDataClientImp.init() failed.");
                return;
            }
            this.mContext = context;
            this.mPushServiceAcceptTinyData = Boolean.valueOf(checkSupportTinyData(context));
            processPendingList(MiTinyDataClient.PENDING_REASON_INIT);
        }

        public void processPendingList(String str) {
            MyLog.v("MiTinyDataClient.processPendingList(" + str + com.xiaomi.push.mpcd.Constants.SEPARATOR_RIGHT_PARENTESIS);
            ArrayList arrayList = new ArrayList();
            synchronized (this.mPendingList) {
                arrayList.addAll(this.mPendingList);
                this.mPendingList.clear();
            }
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                processUploadRequest((ClientUploadDataItem) it.next());
            }
        }

        public boolean processUploadRequest(ClientUploadDataItem clientUploadDataItem) {
            synchronized (this) {
                boolean z = false;
                if (clientUploadDataItem == null) {
                    return false;
                }
                if (TinyDataHelper.verify(clientUploadDataItem, true)) {
                    return false;
                }
                boolean z2 = TextUtils.isEmpty(clientUploadDataItem.getChannel()) && TextUtils.isEmpty(this.mChannel);
                boolean z3 = !alreadyInit();
                Context context = this.mContext;
                if (context == null || missingAppId(context)) {
                    z = true;
                }
                if (!z3 && !z2 && !z) {
                    MyLog.v("MiTinyDataClient Send item immediately." + clientUploadDataItem.getId());
                    if (TextUtils.isEmpty(clientUploadDataItem.getId())) {
                        clientUploadDataItem.setId(PacketHelper.generatePacketID());
                    }
                    if (TextUtils.isEmpty(clientUploadDataItem.getChannel())) {
                        clientUploadDataItem.setChannel(this.mChannel);
                    }
                    if (TextUtils.isEmpty(clientUploadDataItem.getSourcePackage())) {
                        clientUploadDataItem.setSourcePackage(this.mContext.getPackageName());
                    }
                    if (clientUploadDataItem.getTimestamp() <= 0) {
                        clientUploadDataItem.setTimestamp(System.currentTimeMillis());
                    }
                    return upload(clientUploadDataItem);
                }
                if (z2) {
                    MyLog.v("MiTinyDataClient Pending " + clientUploadDataItem.getName() + " reason is " + MiTinyDataClient.PENDING_REASON_CHANNEL);
                } else if (z3) {
                    MyLog.v("MiTinyDataClient Pending " + clientUploadDataItem.getName() + " reason is " + MiTinyDataClient.PENDING_REASON_INIT);
                } else if (z) {
                    MyLog.v("MiTinyDataClient Pending " + clientUploadDataItem.getName() + " reason is " + MiTinyDataClient.PENDING_REASON_APPID);
                }
                addToPendingList(clientUploadDataItem);
                return true;
            }
        }

        public void setChannel(String str) {
            synchronized (this) {
                if (TextUtils.isEmpty(str)) {
                    MyLog.w("channel is null, MiTinyDataClientImp.setChannel(String) failed.");
                } else {
                    this.mChannel = str;
                    processPendingList(MiTinyDataClient.PENDING_REASON_CHANNEL);
                }
            }
        }
    }

    public static void init(Context context, String str) {
        if (context == null) {
            MyLog.w("context is null, MiTinyDataClient.init(Context, String) failed.");
            return;
        }
        MiTinyDataClientImp.getInstance().init(context);
        if (TextUtils.isEmpty(str)) {
            MyLog.w("channel is null or empty, MiTinyDataClient.init(Context, String) failed.");
        } else {
            MiTinyDataClientImp.getInstance().setChannel(str);
        }
    }

    public static boolean upload(Context context, ClientUploadDataItem clientUploadDataItem) {
        MyLog.v("MiTinyDataClient.upload " + clientUploadDataItem.getId());
        if (!MiTinyDataClientImp.getInstance().alreadyInit()) {
            MiTinyDataClientImp.getInstance().init(context);
        }
        return MiTinyDataClientImp.getInstance().processUploadRequest(clientUploadDataItem);
    }

    public static boolean upload(Context context, String str, String str2, long j, String str3) {
        ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
        clientUploadDataItem.setCategory(str);
        clientUploadDataItem.setName(str2);
        clientUploadDataItem.setCounter(j);
        clientUploadDataItem.setData(str3);
        clientUploadDataItem.setFromSdk(true);
        clientUploadDataItem.setChannel("push_sdk_channel");
        return upload(context, clientUploadDataItem);
    }

    public static boolean upload(String str, String str2, long j, String str3) {
        ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
        clientUploadDataItem.setCategory(str);
        clientUploadDataItem.setName(str2);
        clientUploadDataItem.setCounter(j);
        clientUploadDataItem.setData(str3);
        return MiTinyDataClientImp.getInstance().processUploadRequest(clientUploadDataItem);
    }
}
