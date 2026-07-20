package com.xiaomi.xmsf.stock

import android.content.Context
import android.content.Intent
import com.xiaomi.xmsf.push.service.notificationcollection.NotificationListener
import com.xiaomi.xmsf.services.ServiceBoxService

object StockSurfaceBootstrap {
    @JvmStatic
    fun bootstrap(context: Context) {
        NotificationListener.ensureStarted(context)
        runCatching { context.startService(Intent(context, ServiceBoxService::class.java)) }
    }
}
