package com.nihility.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.nihility.Global
import com.xiaomi.xmsf.R

class ForegroundHelper(private val service: Service) {
    companion object {
        const val CHANNEL_STATUS = "status"
        const val NOTIFICATION_ALIVE_ID = 1
    }

    fun startForeground() {
        createNotificationGroupForPushStatus()
        if (Global.ConfigCenter().isStartForegroundService) {
            showForegroundNotificationToKeepAlive()
        } else {
            stopForegroundNotification()
        }
    }

    fun stopForegroundNotification() {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    internal fun showForegroundNotificationToKeepAlive() {
        val notification: Notification = NotificationCompat.Builder(service, CHANNEL_STATUS)
            .setContentTitle(service.getString(R.string.notification_alive))
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(true)
            .build()
        service.startForeground(NOTIFICATION_ALIVE_ID, notification)
    }

    internal fun createNotificationGroupForPushStatus() {
        val manager = NotificationManagerCompat.from(service.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}
