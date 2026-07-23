package com.xiaomi.xmsf.services

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface ISubProcBridge : IInterface {
    @Throws(RemoteException::class)
    fun notifyOnlineConfigChanged()

    abstract class Stub : Binder(), ISubProcBridge {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_NOTIFY_ONLINE_CONFIG_CHANGED -> {
                    data.enforceInterface(DESCRIPTOR)
                    notifyOnlineConfigChanged()
                    reply?.writeNoException()
                    true
                }
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        companion object {
            const val DESCRIPTOR = "com.xiaomi.xmsf.services.ISubProcBridge"
            private const val TRANSACTION_NOTIFY_ONLINE_CONFIG_CHANGED = 1
            private const val INTERFACE_TRANSACTION = 1598968902

            @JvmStatic
            fun asInterface(binder: IBinder?): ISubProcBridge? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is ISubProcBridge) local else Proxy(binder)
            }
        }

        private class Proxy(
            private val remote: IBinder,
        ) : ISubProcBridge {
            override fun asBinder(): IBinder = remote

            override fun notifyOnlineConfigChanged() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_NOTIFY_ONLINE_CONFIG_CHANGED, data, reply, 0)
                    reply.readException()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }
    }
}
