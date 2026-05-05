package com.xiaomi.channel.commonutils.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/android/GoogleAdvertisingClient.java
 */
internal object GoogleAdvertisingClient {
    private const val ACTION_START_SERVICE = "com.google.android.gms.ads.identifier.service.START"
    private const val TIMEOUT = 30000L

    class AdInfo internal constructor(private val advertisingId: String?, private val limitAdTrackingEnabled: Boolean) {
        fun getId(): String? = advertisingId

        fun isLimitAdTrackingEnabled(): Boolean = limitAdTrackingEnabled
    }

    private class AdvertisingConnection : ServiceConnection {
        private val queue = LinkedBlockingQueue<IBinder>(1)
        var retrieved = false

        @Throws(InterruptedException::class)
        fun getBinder(): IBinder? {
            if (retrieved) {
                throw IllegalStateException()
            }
            retrieved = true
            return queue.poll(TIMEOUT, TimeUnit.MILLISECONDS)
        }

        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            try {
                queue.put(iBinder)
            } catch (e: InterruptedException) {
                // ignore
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
        }
    }

    private class AdvertisingInterface(private val binder: IBinder) : IInterface {
        override fun asBinder(): IBinder = binder

        @Throws(RemoteException::class)
        fun getId(): String? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                binder.transact(TRANSACTION_GET_ID, data, reply, 0)
                reply.readException()
                reply.readString()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        @Throws(RemoteException::class)
        fun isLimitAdTrackingEnabled(enabled: Boolean): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(if (enabled) 1 else 0)
                binder.transact(TRANSACTION_IS_LIMIT_AD_TRACKING_ENABLED, data, reply, 0)
                reply.readException()
                reply.readInt() != 0
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        companion object {
            private const val DESCRIPTOR = "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService"
            private const val TRANSACTION_GET_ID = 1
            private const val TRANSACTION_IS_LIMIT_AD_TRACKING_ENABLED = 2
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getAdvertisingIdInfo(context: Context): AdInfo {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("Cannot be called from the main thread")
        }
        context.packageManager.getPackageInfo("com.android.vending", 0)
        val advertisingConnection = AdvertisingConnection()
        val intent = Intent(ACTION_START_SERVICE).apply { setPackage("com.google.android.gms") }
        if (context.bindService(intent, advertisingConnection, Context.BIND_AUTO_CREATE)) {
            try {
                val binder = advertisingConnection.getBinder()
                if (binder != null) {
                    return AdInfo(AdvertisingInterface(binder).getId(), false)
                }
            } finally {
                context.unbindService(advertisingConnection)
            }
            throw IOException("Google advertising binder unavailable")
        }
        throw IOException("Google Play connection failed")
    }
}
