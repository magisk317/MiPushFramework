package com.xiaomi.channel.commonutils.msa;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import com.xiaomi.channel.commonutils.logger.MyLog;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/HuaweiIdManager.class */
class HuaweiIdManager implements IdManager {
    private static final int OAID_SUPPORT_VERSION = 20602000;
    private static final String SERVICE_ACTION = "com.uodis.opendevice.OPENIDS_SERVICE";
    private static final String SERVICE_PACKAGE_NAME = "com.huawei.hwid";
    private static final String SP_NAME_AAID = "aaid";
    private static final int STATE_BINDING = 1;
    private static final int STATE_END = 2;
    private static final int STATE_NONE = 0;
    private static final int TIME_WAIT_LOCK = 3000;
    private static boolean sIsSupport;
    private Context mContext;
    private ServiceConnection mServiceConnection;
    private volatile int mState = 0;
    private volatile String mOaid = null;
    private volatile boolean mIsOaidLimited = false;
    private volatile String mAaid = null;
    private final Object mLockObj = new Object();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/HuaweiIdManager$IdentifierServiceConnection.class */
    private class IdentifierServiceConnection implements ServiceConnection {
        private IdentifierServiceConnection() {
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                HuaweiIdManager.this.mOaid = ServiceRemote.getOaid(iBinder);
                HuaweiIdManager.this.mIsOaidLimited = ServiceRemote.isOaidTrackLimited(iBinder);
                HuaweiIdManager.this.unbindService();
                HuaweiIdManager.this.mState = 2;
                synchronized (HuaweiIdManager.this.mLockObj) {
                    try {
                        HuaweiIdManager.this.mLockObj.notifyAll();
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e2) {
                HuaweiIdManager.this.unbindService();
                HuaweiIdManager.this.mState = 2;
                synchronized (HuaweiIdManager.this.mLockObj) {
                    try {
                        HuaweiIdManager.this.mLockObj.notifyAll();
                    } catch (Exception e3) {
                    }
                }
            } catch (Throwable th) {
                HuaweiIdManager.this.unbindService();
                HuaweiIdManager.this.mState = 2;
                synchronized (HuaweiIdManager.this.mLockObj) {
                    try {
                        HuaweiIdManager.this.mLockObj.notifyAll();
                    } catch (Exception e4) {
                    }
                    throw th;
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/HuaweiIdManager$ServiceRemote.class */
    private static class ServiceRemote {
        static final String DESCRIPTOR = "com.uodis.opendevice.aidl.OpenDeviceIdentifierService";
        static final int TRANSACTION_getOaid = 1;
        static final int TRANSACTION_isOaidTrackLimited = 2;

        private ServiceRemote() {
        }

        static String getOaid(IBinder iBinder) throws RemoteException {
            Parcel parcelObtain = Parcel.obtain();
            Parcel parcelObtain2 = Parcel.obtain();
            try {
                parcelObtain.writeInterfaceToken(DESCRIPTOR);
                iBinder.transact(1, parcelObtain, parcelObtain2, 0);
                parcelObtain2.readException();
                return parcelObtain2.readString();
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }

        static boolean isOaidTrackLimited(IBinder iBinder) throws RemoteException {
            Parcel parcelObtain = Parcel.obtain();
            Parcel parcelObtain2 = Parcel.obtain();
            try {
                parcelObtain.writeInterfaceToken(DESCRIPTOR);
                boolean z = false;
                iBinder.transact(2, parcelObtain, parcelObtain2, 0);
                parcelObtain2.readException();
                if (parcelObtain2.readInt() != 0) {
                    z = true;
                }
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return z;
            } catch (Throwable th) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                throw th;
            }
        }
    }

    public HuaweiIdManager(Context context) {
        this.mContext = context;
        bindService();
    }

    private void bindService() {
        boolean zBindService;
        this.mServiceConnection = new IdentifierServiceConnection();
        Intent intent = new Intent(SERVICE_ACTION);
        intent.setPackage("com.huawei.hwid");
        int i = 1;
        try {
            zBindService = this.mContext.bindService(intent, this.mServiceConnection, 1);
        } catch (Exception e) {
            zBindService = false;
        }
        if (!zBindService) {
            i = 2;
        }
        this.mState = i;
    }

    private static String getAaidFromSp(Context context) {
        String string = null;
        try {
            string = null;
            if (Build.VERSION.SDK_INT >= 24) {
                String string2 = context.createDeviceProtectedStorageContext().getSharedPreferences("aaid", 0).getString("aaid", null);
                string = string2;
                if (string2 != null) {
                    return string2;
                }
            }
            string = context.getSharedPreferences("aaid", 0).getString("aaid", null);
        } catch (Exception e) {
        }
        if (string == null) {
            string = "";
        }
        return string;
    }

    public static boolean isHuaweiPhone(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.huawei.hwid", 128);
            boolean z = (packageInfo.applicationInfo.flags & 1) != 0;
            sIsSupport = packageInfo.versionCode >= OAID_SUPPORT_VERSION;
            return z;
        } catch (Exception e) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unbindService() {
        ServiceConnection serviceConnection = this.mServiceConnection;
        if (serviceConnection != null) {
            try {
                this.mContext.unbindService(serviceConnection);
            } catch (Exception e) {
            }
        }
    }

    private void waitIfNeed(String str) {
        if (this.mState != 1 || Looper.myLooper() == Looper.getMainLooper()) {
            return;
        }
        synchronized (this.mLockObj) {
            try {
                MyLog.w("huawei's " + str + " wait...");
                this.mLockObj.wait(3000L);
            } catch (Exception e) {
            }
        }
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getAAID() {
        if (this.mAaid == null) {
            synchronized (this) {
                if (this.mAaid == null) {
                    this.mAaid = getAaidFromSp(this.mContext);
                }
            }
        }
        return this.mAaid;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getOAID() {
        waitIfNeed("getOAID");
        return this.mOaid;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getUDID() {
        return null;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public String getVAID() {
        return null;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isAllowOAID() {
        waitIfNeed("isAllowOAID");
        return !this.mIsOaidLimited;
    }

    @Override // com.xiaomi.channel.commonutils.msa.IdManager
    public boolean isSupported() {
        return sIsSupport;
    }
}
