package com.xiaomi.xmsf.push.service

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface IStatService : IInterface {

    @Throws(RemoteException::class)
    fun insertEvent(str: String?)

    abstract class Stub : Binder(), IStatService {

        override fun asBinder(): IBinder = this

        @Throws(RemoteException::class)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_INSERT_EVENT -> {
                    data.enforceInterface(DESCRIPTOR)
                    insertEvent(data.readString())
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
            private const val DESCRIPTOR = "com.xiaomi.xmsf.push.service.IStatService"
            private const val TRANSACTION_INSERT_EVENT = 1
            private const val INTERFACE_TRANSACTION = 1598968902
        }
    }
}
