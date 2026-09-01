package com.xiaomi.xmsf.services

import android.app.Service
import android.os.IBinder
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter
import io.github.magisk317.xposed.logging.MagiskOtel

class ServiceBoxService : Service() {
    private val binder = object : ISubProcBridge.Stub() {
        override fun notifyOnlineConfigChanged() {
            StockSurfaceSupport.recordStatEvent(this@ServiceBoxService, "service_box:online_config_changed")
            refreshOnlineConfig()
        }
    }

    override fun onCreate() {
        super.onCreate()
        refreshOnlineConfig()
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

    override fun onBind(intent: android.content.Intent?): IBinder = binder

    private fun refreshOnlineConfig() {
        // Stock 7.5.29 no longer binds MainProcBridgeService. Read the same OnlineConfig keys
        // locally in the service process and keep the product-owned KeepAlive state synchronized.
        KeepAliveRuntimeAdapter.refreshOnlineConfig(this)
    }
}
