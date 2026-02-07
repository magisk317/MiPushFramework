package top.trumeet.common.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

/**
 * Created by Trumeet on 2017/12/29.
 */
object ServiceRunningChecker {
    @JvmStatic
    fun isServiceRunning(context: Context, service: Class<*>): Boolean {
        return try {
            val bind = context.bindService(Intent(context, service), emptyConn, 0)
            if (bind) {
                context.unbindService(emptyConn)
            }
            bind
        } catch (e: SecurityException) {
            false
        }
    }

    private val emptyConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {}
        override fun onServiceDisconnected(name: ComponentName) {}
    }
}
