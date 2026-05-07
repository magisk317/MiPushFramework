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
        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_UPDATE_KEEPALIVE_STRATEGY -> {
                    data.enforceInterface(DESCRIPTOR)
                    updateKeepAliveStrategy(data.readString())
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
            private const val DESCRIPTOR = "com.xiaomi.xmsf.services.keepalive.strategy.IKeepAliveStrategy"
            private const val TRANSACTION_UPDATE_KEEPALIVE_STRATEGY = 1
            private const val INTERFACE_TRANSACTION = 1598968902
        }
    }
}
