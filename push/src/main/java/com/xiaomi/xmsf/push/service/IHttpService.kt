package com.xiaomi.xmsf.push.service

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface IHttpService : IInterface {

    @Throws(RemoteException::class)
    fun doHttpPost(str: String?, map: Map<*, *>?): String?

    abstract class Stub : Binder(), IHttpService {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        @Throws(RemoteException::class)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_DO_HTTP_POST -> {
                    data.enforceInterface(DESCRIPTOR)
                    val response = doHttpPost(
                        data.readString(),
                        data.readHashMap(javaClass.classLoader)
                    )
                    reply?.writeNoException()
                    reply?.writeString(response)
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
            private const val DESCRIPTOR = "com.xiaomi.xmsf.push.service.IHttpService"
            private const val TRANSACTION_DO_HTTP_POST = 1
            private const val INTERFACE_TRANSACTION = 1598968902
        }
    }
}
