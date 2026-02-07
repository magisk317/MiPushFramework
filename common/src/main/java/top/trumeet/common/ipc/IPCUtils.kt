package top.trumeet.common.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

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
                            Log.w("IPCUtils", "Binder died!")
                            listener.onDisconnected()
                        }
                    }, 0)
                } catch (e: Exception) {
                    Log.e("IPCUtils", "Unable to link to death", e)
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
