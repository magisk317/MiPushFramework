package com.xiaomi.channel.commonutils.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import com.xiaomi.mipush.sdk.stat.db.MessageInfoContract;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/GoogleAdvertisingClient.class */
final class GoogleAdvertisingClient {
    private static final String ACTION_START_SERVICE = "com.google.android.gms.ads.identifier.service.START";
    private static final int TIMEOUT = 30000;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/GoogleAdvertisingClient$AdInfo.class */
    static final class AdInfo {
        private final String advertisingId;
        private final boolean limitAdTrackingEnabled;

        AdInfo(String str, boolean z) {
            this.advertisingId = str;
            this.limitAdTrackingEnabled = z;
        }

        public String getId() {
            return this.advertisingId;
        }

        public boolean isLimitAdTrackingEnabled() {
            return this.limitAdTrackingEnabled;
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/GoogleAdvertisingClient$AdvertisingConnection.class */
    private static final class AdvertisingConnection implements ServiceConnection {
        private final LinkedBlockingQueue<IBinder> queue;
        boolean retrieved;

        private AdvertisingConnection() {
            this.retrieved = false;
            this.queue = new LinkedBlockingQueue<>(1);
        }

        public IBinder getBinder() throws InterruptedException {
            if (this.retrieved) {
                throw new IllegalStateException();
            }
            this.retrieved = true;
            return this.queue.poll(MessageInfoContract.TIMEOUT, TimeUnit.MILLISECONDS);
        }

        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                this.queue.put(iBinder);
            } catch (InterruptedException e) {
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/android/GoogleAdvertisingClient$AdvertisingInterface.class */
    private static final class AdvertisingInterface implements IInterface {
        static final String DESCRIPTOR = "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService";
        static final int TRANSACTION_getId = 1;
        static final int TRANSACTION_isLimitAdTrackingEnabled = 2;
        private IBinder binder;

        public AdvertisingInterface(IBinder iBinder) {
            this.binder = iBinder;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this.binder;
        }

        public String getId() throws RemoteException {
            Parcel parcelObtain = Parcel.obtain();
            Parcel parcelObtain2 = Parcel.obtain();
            try {
                parcelObtain.writeInterfaceToken(DESCRIPTOR);
                this.binder.transact(1, parcelObtain, parcelObtain2, 0);
                parcelObtain2.readException();
                return parcelObtain2.readString();
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }

        public boolean isLimitAdTrackingEnabled(boolean z) throws RemoteException {
            Parcel parcelObtain = Parcel.obtain();
            Parcel parcelObtain2 = Parcel.obtain();
            try {
                parcelObtain.writeInterfaceToken(DESCRIPTOR);
                parcelObtain.writeInt(z ? 1 : 0);
                this.binder.transact(2, parcelObtain, parcelObtain2, 0);
                parcelObtain2.readException();
                boolean z2 = parcelObtain2.readInt() != 0;
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return z2;
            } catch (Throwable th) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                throw th;
            }
        }
    }

    GoogleAdvertisingClient() {
    }

    public static AdInfo getAdvertisingIdInfo(Context context) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Cannot be called from the main thread");
        }
        try {
            context.getPackageManager().getPackageInfo("com.android.vending", 0);
            AdvertisingConnection advertisingConnection = new AdvertisingConnection();
            Intent intent = new Intent(ACTION_START_SERVICE);
            intent.setPackage("com.google.android.gms");
            if (context.bindService(intent, advertisingConnection, 1)) {
                try {
                    try {
                        IBinder binder = advertisingConnection.getBinder();
                        if (binder != null) {
                            AdInfo adInfo = new AdInfo(new AdvertisingInterface(binder).getId(), false);
                            context.unbindService(advertisingConnection);
                            return adInfo;
                        }
                        context.unbindService(advertisingConnection);
                    } catch (Exception e) {
                        throw e;
                    }
                } catch (Throwable th) {
                    context.unbindService(advertisingConnection);
                    throw th;
                }
                context.unbindService(advertisingConnection);
                throw new IOException("Google advertising binder unavailable");
            }
            throw new IOException("Google Play connection failed");
        } catch (Exception e2) {
            throw e2;
        }
    }
}
