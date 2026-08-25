package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.common.R as CommonR
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.platform.support.Global
import com.xiaomi.xmsf.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ForegroundHelper(private val service: Service) {
    companion object {
        const val CHANNEL_STATUS = "status"
        const val NOTIFICATION_ALIVE_ID = 1
    }

    fun startForeground() {
        createNotificationGroupForPushStatus()
        // Always satisfy startForegroundService contract first, then apply keep-alive policy.
        showForegroundNotificationToKeepAlive()
        MagiskOtel.event(
            name = "push.keepalive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "foreground",
                "reason" to "started",
            ),
            statusOk = true,
        )
        // Check keep-alive preference asynchronously to avoid blocking the main thread.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                if (!Global.configCenter().isStartForegroundServiceAsync()) {
                    stopForegroundNotification()
                }
            } catch (t: Throwable) {
                Logger.withTag("ForegroundHelper").e(t) { "Failed to check foreground service preference" }
                MagiskOtel.event(
                    name = "push.keepalive",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to "0",
                        "process" to "xmsf",
                        "stage" to "foreground",
                        "reason" to "pref_check_failed",
                        "error_class" to t.javaClass.simpleName,
                    ),
                    statusOk = false,
                )
            }
        }
    }

    fun stopForegroundNotification() {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        MagiskOtel.event(
            name = "push.keepalive",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "foreground",
                "reason" to "stopped",
            ),
            statusOk = true,
        )
    }

    internal fun showForegroundNotificationToKeepAlive() {
        val notification: Notification = NotificationCompat.Builder(service, CHANNEL_STATUS)
            .setContentTitle(service.getString(R.string.notification_alive))
            .setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                NOTIFICATION_ALIVE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            service.startForeground(NOTIFICATION_ALIVE_ID, notification)
        }
    }

    internal fun createNotificationGroupForPushStatus() {
        val manager = NotificationManagerCompat.from(service.applicationContext)
        val groupId = "status_group"
        val group = NotificationChannelGroupCompat.Builder(groupId)
            .setName(CHANNEL_STATUS)
        manager.createNotificationChannelGroup(group.build())
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_STATUS, NotificationManager.IMPORTANCE_MIN
        )
            .setName(service.getString(R.string.notification_category_alive)).setGroup(groupId)
        manager.createNotificationChannel(channel.build())
    }
}
