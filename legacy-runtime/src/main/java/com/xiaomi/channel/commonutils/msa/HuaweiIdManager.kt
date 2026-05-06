package com.xiaomi.channel.commonutils.msa

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import com.xiaomi.channel.commonutils.logger.MyLog
import java.lang.Object

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/msa/HuaweiIdManager.java
 */
class HuaweiIdManager(private val mContext: Context) : IdManager {
    private var mServiceConnection: ServiceConnection? = null
    @Volatile
    private var mState = STATE_NONE
    @Volatile
    private var mOaid: String? = null
    @Volatile
    private var mIsOaidLimited = false
    @Volatile
    private var mAaid: String? = null
    private val mLockObj = Object()

    private inner class IdentifierServiceConnection : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            try {
                mOaid = ServiceRemote.getOaid(iBinder)
                mIsOaidLimited = ServiceRemote.isOaidTrackLimited(iBinder)
            } catch (e: Exception) {
                // Match legacy behavior: end the bind wait even if remote calls fail.
            } finally {
                unbindService()
                mState = STATE_END
                synchronized(mLockObj) {
                    try {
                        mLockObj.notifyAll()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
        }
    }

    private object ServiceRemote {
        private const val DESCRIPTOR = "com.uodis.opendevice.aidl.OpenDeviceIdentifierService"
        private const val TRANSACTION_GET_OAID = 1
        private const val TRANSACTION_IS_OAID_TRACK_LIMITED = 2

        @Throws(RemoteException::class)
        fun getOaid(iBinder: IBinder): String? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                iBinder.transact(TRANSACTION_GET_OAID, data, reply, 0)
                reply.readException()
                reply.readString()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        @Throws(RemoteException::class)
        fun isOaidTrackLimited(iBinder: IBinder): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                iBinder.transact(TRANSACTION_IS_OAID_TRACK_LIMITED, data, reply, 0)
                reply.readException()
                reply.readInt() != 0
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val serviceConnection = IdentifierServiceConnection()
        mServiceConnection = serviceConnection
        val intent = Intent(SERVICE_ACTION).apply { setPackage(SERVICE_PACKAGE_NAME) }
        val bound = try {
            mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            false
        }
        mState = if (bound) STATE_BINDING else STATE_END
    }

    private fun unbindService() {
        val serviceConnection = mServiceConnection
        if (serviceConnection != null) {
            try {
                mContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun waitIfNeed(str: String) {
        if (mState != STATE_BINDING || Looper.myLooper() == Looper.getMainLooper()) {
            return
        }
        synchronized(mLockObj) {
            try {
                MyLog.w("huawei's $str wait...")
                mLockObj.wait(TIME_WAIT_LOCK.toLong())
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun getAAID(): String? {
        if (mAaid == null) {
            synchronized(this) {
                if (mAaid == null) {
                    mAaid = getAaidFromSp(mContext)
                }
            }
        }
        return mAaid
    }

    override fun getOAID(): String? {
        waitIfNeed("getOAID")
        return mOaid
    }

    override fun getUDID(): String? = null

    override fun getVAID(): String? = null

    override fun isAllowOAID(): Boolean {
        waitIfNeed("isAllowOAID")
        return !mIsOaidLimited
    }

    override fun isSupported(): Boolean = sIsSupport

    companion object {
        private const val OAID_SUPPORT_VERSION = 20602000L
        private const val SERVICE_ACTION = "com.uodis.opendevice.OPENIDS_SERVICE"
        private const val SERVICE_PACKAGE_NAME = "com.huawei.hwid"
        private const val SP_NAME_AAID = "aaid"
        private const val STATE_BINDING = 1
        private const val STATE_END = 2
        private const val STATE_NONE = 0
        private const val TIME_WAIT_LOCK = 3000
        private var sIsSupport = false

        private fun getAaidFromSp(context: Context): String {
            var value: String? = null
            try {
                if (Build.VERSION.SDK_INT >= 24) {
                    val deviceProtected = context.createDeviceProtectedStorageContext()
                        .getSharedPreferences(SP_NAME_AAID, 0)
                        .getString(SP_NAME_AAID, null)
                    value = deviceProtected
                    if (deviceProtected != null) {
                        return deviceProtected
                    }
                }
                value = context.getSharedPreferences(SP_NAME_AAID, 0).getString(SP_NAME_AAID, null)
            } catch (e: Exception) {
                // ignore
            }
            return value ?: ""
        }

        @JvmStatic
        fun isHuaweiPhone(context: Context): Boolean {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(SERVICE_PACKAGE_NAME, 128)
                val isSystem = packageInfo.applicationInfo != null && (packageInfo.applicationInfo!!.flags and 1) != 0
                sIsSupport = packageInfo.longVersionCode >= OAID_SUPPORT_VERSION
                isSystem
            } catch (e: Exception) {
                false
            }
        }
    }
}
