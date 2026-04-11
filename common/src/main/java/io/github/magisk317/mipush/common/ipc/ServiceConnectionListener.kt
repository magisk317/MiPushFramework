package io.github.magisk317.mipush.common.ipc

import android.os.IBinder

/**
 * Created by Trumeet on 2017/12/22.
 */
interface ServiceConnectionListener {
    fun onReady(service: IBinder)
    fun onDisconnected()
}
