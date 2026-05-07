package com.xiaomi.xmsf.push.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class StatService : Service() {
    private val binder = object : IStatService.Stub() {
        override fun insertEvent(str: String?) {
            StockSurfaceSupport.recordStatEvent(this@StatService, str)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("event")?.let {
            StockSurfaceSupport.recordStatEvent(this, it)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
