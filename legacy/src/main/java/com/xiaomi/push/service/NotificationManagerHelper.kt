package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import com.xiaomi.channel.commonutils.logger.MyLog
import java.util.WeakHashMap

class NotificationManagerHelper private constructor(
    private val targetPackage: String,
) {
    fun cancel(notificationId: Int) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.cancel(targetPackage, notificationId)
                outLog("cancel succ:$notificationId")
            } else {
                getNm().cancel(notificationId)
            }
        } catch (e: Exception) {
            outLog("cancel error$e")
        }
    }

    fun createNotificationChannel(notificationChannel: NotificationChannel) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.createNotificationChannel(targetPackage, notificationChannel)
            } else {
                getNm().createNotificationChannel(notificationChannel)
            }
        } catch (e: Exception) {
            outLog("createNotificationChannel error$e")
        }
    }

    fun createNotificationChannelGroup(notificationChannelGroup: NotificationChannelGroup) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.createNotificationChannelGroup(targetPackage, notificationChannelGroup)
            } else {
                getNm().createNotificationChannelGroup(notificationChannelGroup)
            }
        } catch (e: Exception) {
            outLog("createNotificationChannelGroup error$e")
        }
    }

    fun deleteNotificationChannel(channelId: String) {
        getNm().deleteNotificationChannel(channelId)
    }

    fun deleteNotificationChannelGroup(groupId: String) {
        getNm().deleteNotificationChannelGroup(groupId)
    }

    fun getActiveNotifications(): List<StatusBarNotification>? {
        return try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.getActiveNotifications(targetPackage)
            } else {
                NotificationManagerPlatformSupport.filterLocalActiveNotifications(targetPackage, getNm().activeNotifications)
            }
        } catch (e: Exception) {
            outLog("getActiveNotifications error $e")
            null
        }
    }

    fun getGroupSummaryChannelId(defaultChannelId: String, fallbackChannelId: String): String {
        return if (isSupportFwk()) defaultChannelId else fallbackChannelId
    }

    fun getMipushChannelId(channelId: String): String {
        val format = if (isSupportFwk()) NEW_FORMAT_PREFIX else OLD_FORMAT_PREFIX
        return format.format(targetPackage, channelId)
    }

    fun getNotificationChannel(channelId: String): NotificationChannel? {
        return try {
            if (isSupportFwk()) {
                getNotificationChannels()?.firstOrNull { it.id == channelId }
            } else {
                getNm().getNotificationChannel(channelId)
            }
        } catch (e: Exception) {
            outLog("getNotificationChannel error$e")
            null
        }
    }

    fun getNotificationChannelGroup(groupId: String): NotificationChannelGroup? {
        return try {
            if (!isSupportFwk()) {
                if (Build.VERSION.SDK_INT < 28) {
                    getNm().notificationChannelGroups.firstOrNull { it.id == groupId }
                } else {
                    getNm().getNotificationChannelGroup(groupId)
                }
            } else {
                NotificationManagerPlatformSupport.getNotificationChannelGroup(groupId, targetPackage)
            }
        } catch (e: Exception) {
            outLog("getNotificationChannel error$e")
            null
        }
    }

    fun getNotificationChannelGroups(): List<NotificationChannelGroup>? {
        return try {
            if (isSupportFwk()) {
                null
            } else {
                getNm().notificationChannelGroups
            }
        } catch (e: Exception) {
            outLog("getNotificationChannelGroups error $e")
            null
        }
    }

    fun getNotificationChannels(): List<NotificationChannel>? {
        return try {
            val channels: List<NotificationChannel>?
            val format: String
            if (isSupportFwk()) {
                channels = NotificationManagerPlatformSupport.getNotificationChannels(targetPackage)
                format = NEW_FORMAT_PREFIX
            } else {
                channels = getNm().notificationChannels
                format = OLD_FORMAT_PREFIX
            }
            NotificationManagerPlatformSupport.filterMipushChannels(targetPackage, format, channels)
        } catch (e: Exception) {
            outLog("getNotificationChannels error$e")
            null
        }
    }

    fun notify(notificationId: Int, notification: Notification) {
        try {
            if (isSupportFwk()) {
                NotificationManagerPlatformSupport.notify(targetPackage, notificationId, notification)
            } else {
                getNm().notify(notificationId, notification)
            }
        } catch (_: Exception) {
        }
    }

    override fun toString(): String {
        return "NotificationManagerHelper{$targetPackage}"
    }

    companion object {
        const val DEFAULT_ID = "default"
        private const val NEW_FORMAT_PREFIX = "mipush|%s|%s"
        private const val OLD_FORMAT_PREFIX = "mipush_%s_%s"
        private val cache = WeakHashMap<String, NotificationManagerHelper>()

        @JvmStatic
        fun from(context: Context, packageName: String): NotificationManagerHelper {
            NotificationManagerPlatformSupport.init(context)
            return cache[packageName] ?: NotificationManagerHelper(packageName).also {
                cache[packageName] = it
            }
        }

        @JvmStatic
        fun outLog(message: String) {
            MyLog.w("NMHelper:$message")
        }

        @JvmStatic
        fun isRomSupportNotificationBelongToApp(context: Context): Boolean {
            return NotificationManagerPlatformSupport.isRomSupportNotificationBelongToApp(context)
        }

        private fun isSupportFwk(): Boolean {
            return NotificationManagerPlatformSupport.isSupportFwk()
        }

        private fun getNm(): NotificationManager {
            return NotificationManagerPlatformSupport.getNm()
        }
    }
}
