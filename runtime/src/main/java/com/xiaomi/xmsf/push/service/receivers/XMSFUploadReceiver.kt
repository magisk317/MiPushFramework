package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.xmsf.push.service.StatService
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class XMSFUploadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: "unknown"
        StockSurfaceSupport.recordStatEvent(context, "xmsf_upload:$action")
        context.startService(
            Intent(context, StatService::class.java)
                .putExtra("event", "xmsf_upload:$action"),
        )
    }
}
