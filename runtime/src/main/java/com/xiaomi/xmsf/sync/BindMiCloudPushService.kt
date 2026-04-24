package com.xiaomi.xmsf.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class BindMiCloudPushService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sid = intent?.getStringExtra("sid").orEmpty().ifBlank { "micloud" }
        val token = StockSurfaceSupport.serviceTokenBundle(this, sid)
        StockSurfaceSupport.recordStatEvent(
            this,
            "bind_micloud_push:${token.getString("source")}:${token.getBoolean("available")}",
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
