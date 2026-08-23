package io.github.magisk317.mipush.common.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import co.touchlab.kermit.Logger

/**
 * Created by Trumeet on 2017/12/22.
 */
object IPCUtils {
    @JvmStatic
    fun connectService(
        service: Intent,
        context: Context,
        flags: Int,
        listener: ServiceConnectionListener
    ): Disconnectable {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    binder.linkToDeath({
                        if (!binder.isBinderAlive && !binder.pingBinder()) {
                            Logger.withTag("IPCUtils").w { "Binder died!" }
                            listener.onDisconnected()
                        }
                    }, 0)
                } catch (e: RemoteException) {
                    Logger.withTag("IPCUtils").e(e) { "Unable to link to death" }
                }
                listener.onReady(binder)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                listener.onDisconnected()
            }
        }
        context.bindService(service, connection, flags)
        return object : Disconnectable {
            override fun disconnect() {
                context.unbindService(connection)
            }
        }
    }
}
