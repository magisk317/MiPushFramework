package com.xiaomi.xmsf.push.service

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface IHttpService : IInterface {

    @Throws(RemoteException::class)
    fun doHttpPost(str: String?, map: Map<*, *>?): String?

    @Throws(RemoteException::class)
    fun doHttpPostIntl(str: String?, map: Map<*, *>?): String?

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
                    @Suppress("DEPRECATION")
                    val response = doHttpPost(
                        data.readString(),
                        data.readHashMap(javaClass.classLoader)
                    )
                    reply?.writeNoException()
                    reply?.writeString(response)
                    true
                }

                TRANSACTION_DO_HTTP_POST_INTL -> {
                    data.enforceInterface(DESCRIPTOR)
                    @Suppress("DEPRECATION")
                    val response = doHttpPostIntl(
                        data.readString(),
                        data.readHashMap(javaClass.classLoader),
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
            const val DESCRIPTOR = "com.xiaomi.xmsf.push.service.IHttpService"
            private const val TRANSACTION_DO_HTTP_POST = 1
            private const val TRANSACTION_DO_HTTP_POST_INTL = 2
            private const val INTERFACE_TRANSACTION = 1598968902

            @JvmStatic
            fun asInterface(binder: IBinder?): IHttpService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is IHttpService) local else Proxy(binder)
            }
        }

        private class Proxy(
            private val remote: IBinder,
        ) : IHttpService {
            override fun asBinder(): IBinder = remote

            override fun doHttpPost(str: String?, map: Map<*, *>?): String? = transactPost(
                TRANSACTION_DO_HTTP_POST,
                str,
                map,
            )

            override fun doHttpPostIntl(str: String?, map: Map<*, *>?): String? = transactPost(
                TRANSACTION_DO_HTTP_POST_INTL,
                str,
                map,
            )

            private fun transactPost(code: Int, str: String?, map: Map<*, *>?): String? {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(str)
                    data.writeMap(map)
                    remote.transact(code, data, reply, 0)
                    reply.readException()
                    reply.readString()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }
    }
}
