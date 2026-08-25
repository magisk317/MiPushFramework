package com.xiaomi.xmsf.services.keepalive.strategy

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface IKeepAliveStrategy : IInterface {
    @Throws(RemoteException::class)
    fun updateKeepAliveStrategy(configJson: String?)

    abstract class Stub : Binder(), IKeepAliveStrategy {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_UPDATE_KEEPALIVE_STRATEGY -> {
                    data.enforceInterface(DESCRIPTOR)
                    updateKeepAliveStrategy(data.readString())
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
            const val DESCRIPTOR = "com.xiaomi.xmsf.services.keepalive.strategy.IKeepAliveStrategy"
            private const val TRANSACTION_UPDATE_KEEPALIVE_STRATEGY = 1
            private const val INTERFACE_TRANSACTION = 1598968902

            @JvmStatic
            fun asInterface(binder: IBinder?): IKeepAliveStrategy? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is IKeepAliveStrategy) local else Proxy(binder)
            }
        }

        private class Proxy(
            private val remote: IBinder,
        ) : IKeepAliveStrategy {
            override fun asBinder(): IBinder = remote

            override fun updateKeepAliveStrategy(configJson: String?) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(configJson)
                    remote.transact(
                        TRANSACTION_UPDATE_KEEPALIVE_STRATEGY,
                        data,
                        null,
                        IBinder.FLAG_ONEWAY,
                    )
                } finally {
                    data.recycle()
                }
            }
        }
    }
}
