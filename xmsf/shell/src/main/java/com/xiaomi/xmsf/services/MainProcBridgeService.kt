package com.xiaomi.xmsf.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class MainProcBridgeService : Service() {
    private val binder = object : IMainProcBridge.Stub() {
        override fun getOnlineBooleanConfig(key: Int, defaultValue: Boolean): Boolean {
            return StockSurfaceSupport.getOnlineBooleanConfig(this@MainProcBridgeService, key, defaultValue)
        }

        override fun getOnlineIntConfig(key: Int, defaultValue: Int): Int {
            return StockSurfaceSupport.getOnlineIntConfig(this@MainProcBridgeService, key, defaultValue)
        }

        override fun getOnlineStringConfig(key: Int, defaultValue: String?): String? {
            return StockSurfaceSupport.getOnlineStringConfig(this@MainProcBridgeService, key, defaultValue.orEmpty())
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
