package com.xiaomi.xmsf.push.service

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface IStatService : IInterface {

    @Throws(RemoteException::class)
    fun insertEvent(str: String?)

    @Throws(RemoteException::class)
    fun insertEventIntl(map: Map<*, *>?)

    abstract class Stub : Binder(), IStatService {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        @Throws(RemoteException::class)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_INSERT_EVENT -> {
                    data.enforceInterface(DESCRIPTOR)
                    insertEvent(data.readString())
                    true
                }

                TRANSACTION_INSERT_EVENT_INTL -> {
                    data.enforceInterface(DESCRIPTOR)
                    @Suppress("DEPRECATION")
                    insertEventIntl(data.readHashMap(javaClass.classLoader))
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
            const val DESCRIPTOR = "com.xiaomi.xmsf.push.service.IStatService"
            private const val TRANSACTION_INSERT_EVENT = 1
            private const val TRANSACTION_INSERT_EVENT_INTL = 2
            private const val INTERFACE_TRANSACTION = 1598968902

            @JvmStatic
            fun asInterface(binder: IBinder?): IStatService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is IStatService) local else Proxy(binder)
            }
        }

        private class Proxy(
            private val remote: IBinder,
        ) : IStatService {
            override fun asBinder(): IBinder = remote

            override fun insertEvent(str: String?) {
                transactOneWay(TRANSACTION_INSERT_EVENT) { parcel -> parcel.writeString(str) }
            }

            override fun insertEventIntl(map: Map<*, *>?) {
                transactOneWay(TRANSACTION_INSERT_EVENT_INTL) { parcel -> parcel.writeMap(map) }
            }

            private inline fun transactOneWay(code: Int, writeBody: (Parcel) -> Unit) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    writeBody(data)
                    remote.transact(code, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }
        }
    }
}
