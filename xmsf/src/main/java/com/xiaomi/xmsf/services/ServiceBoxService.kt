package com.xiaomi.xmsf.services

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class ServiceBoxService : Service() {
    private var mainProcBridge: IMainProcBridge? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mainProcBridge = IMainProcBridge.Stub.asInterface(service)
            StockSurfaceSupport.recordStatEvent(this@ServiceBoxService, "service_box:main_proc_connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainProcBridge = null
            StockSurfaceSupport.recordStatEvent(this@ServiceBoxService, "service_box:main_proc_disconnected")
        }
    }
    private val binder = object : ISubProcBridge.Stub() {
        override fun notifyOnlineConfigChanged() {
            StockSurfaceSupport.recordStatEvent(this@ServiceBoxService, "service_box:online_config_changed")
            val keepAliveEnabled = mainProcBridge?.getOnlineBooleanConfig(1005, false) ?: false
            StockSurfaceSupport.updateKeepAliveConfig(
                this@ServiceBoxService,
                """{"source":"service_box","onlineConfigChanged":true,"keepAliveEnabled":$keepAliveEnabled}""",
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        bindService(Intent(this, MainProcBridgeService::class.java), connection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        runCatching { unbindService(connection) }
        mainProcBridge = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
