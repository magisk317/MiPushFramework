package com.xiaomi.xmsf.push.notification

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import io.github.magisk317.mipush.Global
import io.github.magisk317.mipush.XMPushUtils
import io.github.magisk317.mipush.notification.NotificationManagerEx
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.utils.NotificationUtils.getChannelIdByPkg
import io.github.magisk317.mipush.common.utils.NotificationUtils.getGroupIdByPkg

object NotificationChannelManager {
    @JvmStatic
    fun getNotificationManagerEx(): NotificationManagerEx = NotificationManagerEx

    @TargetApi(26)
    private fun createGroupWithPackage(
        packageName: String,
        appName: CharSequence
    ): NotificationChannelGroup = NotificationChannelGroup(getGroupIdByPkg(packageName), appName)

    private fun createChannelWithPackage(
        metaInfo: PushMetaInfo,
        packageName: String
    ): NotificationChannel? {
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val channelName = configuration.channelName("未分类")
        val channelDescription = configuration.channelDescription(null)
        val sound = configuration.soundUrl(null)

        var channel: NotificationChannel? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = NotificationChannel(
                getChannelId(metaInfo, packageName),
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = channelDescription
            if (sound != null) {
                val attr = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                channel.setSound(Uri.parse(sound), attr)
            }
        }
        return channel
    }

    @JvmStatic
    fun getChannelId(metaInfo: PushMetaInfo, packageName: String): String {
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        return getChannelIdByPkg(packageName) + "_" + configuration.channelId("")
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.O)
    fun isNotificationChannelEnabled(channel: NotificationChannel?): Boolean {
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @JvmStatic
    fun isNotificationChannelEnabled(packageName: String, channelId: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!TextUtils.isEmpty(channelId)) {
                val channel = NotificationManagerEx.getNotificationChannel(packageName, channelId)
                return isNotificationChannelEnabled(channel)
            }
            return false
        }
        return NotificationManagerEx.areNotificationsEnabled(packageName)
    }

    @JvmStatic
    fun registerChannelIfNeeded(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String
    ): NotificationChannel? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }
        val appName = Global.ApplicationNameCache().getAppName(context, packageName) ?: return null
        return createNotificationChannel(metaInfo, packageName, appName)
    }

    private fun createNotificationChannel(
        metaInfo: PushMetaInfo,
        packageName: String,
        appName: CharSequence
    ): NotificationChannel? {
        val notificationChannelGroup = createGroupWithPackage(packageName, appName)
        getNotificationManagerEx().createNotificationChannelGroups(
            packageName,
            listOf(notificationChannelGroup)
        )

        val notificationChannel = createChannelWithPackage(metaInfo, packageName)
        if (notificationChannel != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel.group = notificationChannelGroup.id
        }

        if (notificationChannel != null) {
            getNotificationManagerEx().createNotificationChannels(
                packageName,
                listOf(notificationChannel)
            )
        }
        return notificationChannel
    }
}
