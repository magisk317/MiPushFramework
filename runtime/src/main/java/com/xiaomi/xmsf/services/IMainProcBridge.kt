package com.xiaomi.xmsf.services

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface IMainProcBridge : IInterface {
    @Throws(RemoteException::class)
    fun getOnlineBooleanConfig(key: Int, defaultValue: Boolean): Boolean

    @Throws(RemoteException::class)
    fun getOnlineIntConfig(key: Int, defaultValue: Int): Int

    @Throws(RemoteException::class)
    fun getOnlineStringConfig(key: Int, defaultValue: String?): String?

    abstract class Stub : Binder(), IMainProcBridge {
        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_GET_BOOLEAN -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = getOnlineBooleanConfig(data.readInt(), data.readInt() != 0)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    true
                }
                TRANSACTION_GET_INT -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = getOnlineIntConfig(data.readInt(), data.readInt())
                    reply?.writeNoException()
                    reply?.writeInt(result)
                    true
                }
                TRANSACTION_GET_STRING -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = getOnlineStringConfig(data.readInt(), data.readString())
                    reply?.writeNoException()
                    reply?.writeString(result)
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
            private const val DESCRIPTOR = "com.xiaomi.xmsf.services.IMainProcBridge"
            private const val TRANSACTION_GET_BOOLEAN = 1
            private const val TRANSACTION_GET_INT = 2
            private const val TRANSACTION_GET_STRING = 3
            private const val INTERFACE_TRANSACTION = 1598968902

            @JvmStatic
            fun asInterface(binder: IBinder?): IMainProcBridge? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is IMainProcBridge) local else Proxy(binder)
            }
        }

        private class Proxy(
            private val remote: IBinder,
        ) : IMainProcBridge {
            override fun asBinder(): IBinder = remote

            override fun getOnlineBooleanConfig(key: Int, defaultValue: Boolean): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeInt(key)
                    data.writeInt(if (defaultValue) 1 else 0)
                    remote.transact(TRANSACTION_GET_BOOLEAN, data, reply, 0)
                    reply.readException()
                    reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun getOnlineIntConfig(key: Int, defaultValue: Int): Int {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeInt(key)
                    data.writeInt(defaultValue)
                    remote.transact(TRANSACTION_GET_INT, data, reply, 0)
                    reply.readException()
                    reply.readInt()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            override fun getOnlineStringConfig(key: Int, defaultValue: String?): String? {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeInt(key)
                    data.writeString(defaultValue)
                    remote.transact(TRANSACTION_GET_STRING, data, reply, 0)
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
