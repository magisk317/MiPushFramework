package com.xiaomi.xmsf.push.service.notificationcollection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class NotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        StockSurfaceSupport.recordNotificationEvent(this, "listener_connected", packageName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        StockSurfaceSupport.recordNotificationEvent(this, "posted", sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        StockSurfaceSupport.recordNotificationEvent(this, "removed", sbn.packageName)
    }

    companion object {
        @JvmStatic
        fun ensureStarted(context: Context) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    requestRebind(ComponentName(context, NotificationListener::class.java))
                }
                context.startService(Intent(context, NotificationListener::class.java))
            }
        }
    }
}
