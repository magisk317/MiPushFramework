package com.xiaomi.xmsf.services.keepalive.strategy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class KeepAliveConfigService : Service() {
    private val binder = object : IKeepAliveStrategy.Stub() {
        override fun updateKeepAliveStrategy(configJson: String?) {
            StockSurfaceSupport.updateKeepAliveConfig(this@KeepAliveConfigService, configJson)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("configJson")?.let {
            StockSurfaceSupport.updateKeepAliveConfig(this, it)
        }
        return START_NOT_STICKY
    }
}
