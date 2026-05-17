package io.github.magisk317.mipush.notification

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
import io.github.magisk317.mipush.notification.NotificationManagerEx
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.common.utils.NotificationUtils.getChannelIdByPkg
import io.github.magisk317.mipush.common.utils.NotificationUtils.getGroupIdByPkg

object NotificationChannelManager {
    @JvmStatic
    fun getNotificationManagerEx(): NotificationManagerEx = NotificationManagerEx

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

        val channel = NotificationChannel(
            getChannelId(metaInfo, packageName),
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = channelDescription
        if (sound != null) {
            val attr = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            channel.setSound(Uri.parse(sound), attr)
        }
        return channel
    }

    @JvmStatic
    fun getChannelId(metaInfo: PushMetaInfo, packageName: String): String {
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        return getChannelIdByPkg(packageName) + "_" + configuration.channelId("")
    }

    @JvmStatic
    fun isNotificationChannelEnabled(channel: NotificationChannel?): Boolean {
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @JvmStatic
    fun isNotificationChannelEnabled(packageName: String, channelId: String?): Boolean {
        if (!TextUtils.isEmpty(channelId)) {
            return try {
                val channel = NotificationManagerEx.getNotificationChannel(packageName, channelId)
                isNotificationChannelEnabled(channel)
            } catch (_: Exception) {
                // 查询失败时降级为"已启用"，避免阻塞列表/渲染链路
                true
            }
        }
        return false
    }

    @JvmStatic
    fun registerChannelIfNeeded(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String
    ): NotificationChannel? {
        val appName = Global.applicationNameCache().getAppName(context, packageName) ?: return null
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
        if (notificationChannel != null) {
            notificationChannel.group = notificationChannelGroup.id
            val existing = getNotificationManagerEx().getNotificationChannel(packageName, notificationChannel.id)
            if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                getNotificationManagerEx().deleteNotificationChannel(packageName, notificationChannel.id)
            }
            getNotificationManagerEx().createNotificationChannels(
                packageName,
                listOf(notificationChannel)
            )
        }
        return notificationChannel
    }
}
