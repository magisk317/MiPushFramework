package com.xiaomi.xmsf.services

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter
import io.github.magisk317.xposed.logging.MagiskOtel

class ServiceBoxService : Service() {
    private var mainProcBridge: IMainProcBridge? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mainProcBridge = IMainProcBridge.Stub.asInterface(service)
            StockSurfaceSupport.recordStatEvent(this@ServiceBoxService, "service_box:main_proc_connected")
            refreshOnlineConfig()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainProcBridge = null
            StockSurfaceSupport.recordStatEvent(this@ServiceBoxService, "service_box:main_proc_disconnected")
        }
    }
    private val binder = object : ISubProcBridge.Stub() {
        override fun notifyOnlineConfigChanged() {
            StockSurfaceSupport.recordStatEvent(this@ServiceBoxService, "service_box:online_config_changed")
            refreshOnlineConfig()
        }
    }

    override fun onCreate() {
        super.onCreate()
        bindService(Intent(this, MainProcBridgeService::class.java), connection, BIND_AUTO_CREATE)
        MagiskOtel.event(
            name = "push.servicebox",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "create",
            ),
            statusOk = true,
        )
    }

    override fun onDestroy() {
        runCatching { unbindService(connection) }
        mainProcBridge = null
        KeepAliveRuntimeAdapter.shutdown()
        MagiskOtel.event(
            name = "push.servicebox",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "destroy",
            ),
            statusOk = true,
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun refreshOnlineConfig() {
        KeepAliveRuntimeAdapter.refreshOnlineConfig(this, mainProcBridge)
    }
}
