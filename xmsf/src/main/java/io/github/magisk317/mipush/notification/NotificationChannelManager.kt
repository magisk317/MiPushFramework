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
import com.xiaomi.push.service.NotificationManagerHelper
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
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String
    ): NotificationChannel? {
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val channelName = configuration.channelName("未分类")
        val channelDescription = configuration.channelDescription(null)
        val sound = configuration.soundUrl(null)

        val channel = NotificationChannel(
            getChannelId(context, metaInfo, packageName),
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
    fun getChannelId(context: Context, metaInfo: PushMetaInfo, packageName: String): String {
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val sourceChannelId = configuration.channelId(null)
            ?.takeIf(String::isNotBlank)
            ?: NotificationManagerHelper.DEFAULT_ID
        // Stock 7.4.67-C g1.i/m namespaces channels as mipush|pkg|source when
        // belong-to-app is available and mipush_pkg_source otherwise. The old
        // ch_<package>_<source> name was invisible to stock g1.u ownership checks, so all
        // newly provisioned channels use the stock identity required by active clear.
        return NotificationManagerHelper.from(context.applicationContext, packageName)
            .getMipushChannelId(sourceChannelId)
    }

    internal fun getLegacyChannelId(metaInfo: PushMetaInfo, packageName: String): String {
        // This is lookup-only migration support for channels created before the stock
        // 7.4.67-C naming alignment. Reusing an existing ID preserves the user's channel
        // importance and sound; channel creation must continue through getChannelId().
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        return getChannelIdByPkg(packageName) + "_" + configuration.channelId("")
    }

    @JvmStatic
    fun getCandidateChannelIds(context: Context, metaInfo: PushMetaInfo, packageName: String): List<String> {
        val candidates = mutableListOf<String>()
        val custom = XMPushUtils.getConfiguration(metaInfo)
        custom.borrowChannelId(null)?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        val stockChannelId = getChannelId(context, metaInfo, packageName)
        candidates.add(stockChannelId)
        val legacyChannelId = getLegacyChannelId(metaInfo, packageName)
        candidates.add(legacyChannelId)
        custom.channelId(null)?.takeIf { it.isNotBlank() }?.let { rawId ->
            candidates.add(rawId)
            candidates.add("ch_${packageName}_${rawId}")
            candidates.add("mipush|${packageName}|${rawId}")
            candidates.add("mipush_${packageName}_${rawId}")
        }
        return candidates.distinct()
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
                // 查询失败时降级为"禁用"，避免绕过用户通道设置
                false
            }
        }
        return false
    }

    @JvmStatic
    fun isAnyChannelDisabled(context: Context, metaInfo: PushMetaInfo, packageName: String): Boolean {
        val candidates = getCandidateChannelIds(context, metaInfo, packageName)
        for (candId in candidates) {
            val channel = NotificationManagerEx.getNotificationChannel(packageName, candId)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return true
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
        return createNotificationChannel(context, metaInfo, packageName, appName)
    }

    private fun createNotificationChannel(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        appName: CharSequence
    ): NotificationChannel? {
        val notificationChannelGroup = createGroupWithPackage(packageName, appName)
        getNotificationManagerEx().createNotificationChannelGroups(
            packageName,
            listOf(notificationChannelGroup)
        )

        val notificationChannel = createChannelWithPackage(context, metaInfo, packageName)
        if (notificationChannel != null) {
            notificationChannel.group = notificationChannelGroup.id
            getNotificationManagerEx().createNotificationChannels(
                packageName,
                listOf(notificationChannel)
            )
        }
        return notificationChannel
    }
}
