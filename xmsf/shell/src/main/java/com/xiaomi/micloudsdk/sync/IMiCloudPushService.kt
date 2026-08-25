package com.xiaomi.micloudsdk.sync

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

/** Stock MiCloud push worker contract. The [Intent] is an in/out AIDL value. */
interface IMiCloudPushService : IInterface {
    @Throws(RemoteException::class)
    fun startWork(intent: Intent?)

    abstract class Stub : Binder(), IMiCloudPushService {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_START_WORK -> {
                    data.enforceInterface(DESCRIPTOR)
                    val intent = if (data.readInt() != 0) {
                        Intent.CREATOR.createFromParcel(data)
                    } else {
                        null
                    }
                    startWork(intent)
                    reply?.writeNoException()
                    if (intent != null) {
                        reply?.writeInt(1)
                        reply?.let { intent.writeToParcel(it, 1) }
                    } else {
                        reply?.writeInt(0)
                    }
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
            const val DESCRIPTOR = "com.xiaomi.micloudsdk.sync.IMiCloudPushService"
            private const val TRANSACTION_START_WORK = 1
            private const val INTERFACE_TRANSACTION = 1598968902

            @JvmStatic
            fun asInterface(binder: IBinder?): IMiCloudPushService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                return if (local is IMiCloudPushService) local else Proxy(binder)
            }
        }

        private class Proxy(
            private val remote: IBinder,
        ) : IMiCloudPushService {
            override fun asBinder(): IBinder = remote

            override fun startWork(intent: Intent?) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    if (intent != null) {
                        data.writeInt(1)
                        intent.writeToParcel(data, 0)
                    } else {
                        data.writeInt(0)
                    }
                    remote.transact(TRANSACTION_START_WORK, data, reply, 0)
                    reply.readException()
                    if (reply.readInt() != 0 && intent != null) {
                        intent.readFromParcel(reply)
                    }
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }
    }
}
