package com.xiaomi.xmsf.stock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.push.service.NotificationManagerHelper
import io.github.magisk317.mipush.platform.support.NotificationVendorAdapter

/** Stock ChannelProvider protocol and channel-id translation. */
internal object StockChannelSupport {
    private const val CODE_OK = 0
    private const val CODE_NOT_SUPPORTED = 1
    private const val CODE_UNEXPECTED = 3
    private const val CODE_ACCESS_DENIED = 4
    private const val CODE_NOT_FOUND = 5
    private const val CODE_UNKNOWN_METHOD = 6

    private const val STOCK_CHANNEL_CALLER = "com.miui.systemAdSolution"
    private const val PERMISSION_SOUND = 1
    private const val PERMISSION_VIBRATE = 2
    private const val PERMISSION_LIGHTS = 4
    private const val PERMISSION_BYPASS_DND = 8
    private const val PERMISSION_BADGE = 16
    private const val ALL_PERMISSIONS = PERMISSION_SOUND or PERMISSION_VIBRATE or PERMISSION_LIGHTS or
        PERMISSION_BYPASS_DND or PERMISSION_BADGE

    fun handle(
        context: Context,
        callingPackage: String?,
        method: String?,
        extras: Bundle?,
    ): Bundle {
        if (method == null || extras == null) return result(CODE_UNEXPECTED)
        if (callingPackage != STOCK_CHANNEL_CALLER) return result(CODE_ACCESS_DENIED)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return result(CODE_NOT_SUPPORTED)
        if (!NotificationVendorAdapter.isRomNotificationBelongToAppSupported(context)) {
            return result(CODE_NOT_SUPPORTED)
        }
        return try {
            when (method) {
                "createChannel" -> create(context, extras)
                "queryChannelState" -> query(context, extras)
                else -> result(CODE_UNKNOWN_METHOD)
            }
        } catch (_: Throwable) {
            result(CODE_UNEXPECTED)
        }
    }

    internal fun applyPermissions(channel: NotificationChannel, permissions: Int) {
        val mask = permissions and ALL_PERMISSIONS
        channel.enableVibration(mask and PERMISSION_VIBRATE != 0)
        channel.enableLights(mask and PERMISSION_LIGHTS != 0)
        channel.setBypassDnd(mask and PERMISSION_BYPASS_DND != 0)
        channel.setShowBadge(mask and PERMISSION_BADGE != 0)
        if (mask and PERMISSION_SOUND == 0) {
            channel.setSound(null, null)
        } else if (channel.sound == null) {
            channel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    internal fun computePermissions(channel: NotificationChannel): Int {
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return 0
        var permissions = 0
        if (channel.sound != null) permissions = permissions or PERMISSION_SOUND
        if (channel.shouldVibrate()) permissions = permissions or PERMISSION_VIBRATE
        if (channel.shouldShowLights()) permissions = permissions or PERMISSION_LIGHTS
        if (channel.canBypassDnd() && channel.importance >= NotificationManager.IMPORTANCE_HIGH) {
            permissions = permissions or PERMISSION_BYPASS_DND
        }
        if (channel.canShowBadge()) permissions = permissions or PERMISSION_BADGE
        return permissions
    }

    private fun create(context: Context, extras: Bundle): Bundle {
        val packageName = extras.getString("pkgName").orEmpty()
        val channelName = extras.getString("channelName").orEmpty()
        val channelDescription = extras.getString("channelDesc")
        val sourceChannelId = extras.getString("channelId").orEmpty()
        val importance = extras.getInt("channelImportance", NotificationManager.IMPORTANCE_DEFAULT)
        if (packageName.isBlank() || channelName.isBlank() || sourceChannelId.isBlank()) {
            return result(CODE_UNEXPECTED)
        }
        val manager = NotificationManagerHelper.from(context.applicationContext, packageName)
        val appChannelId = manager.getMipushChannelId(sourceChannelId)
        val permissions = extras.getString("channelPermissions")?.toIntOrNull() ?: 0
        val channel = NotificationChannel(appChannelId, channelName, importance).apply {
            description = channelDescription
        }
        applyPermissions(channel, permissions)
        manager.createNotificationChannel(channel)
        return result(CODE_OK).apply {
            putString("pkgName", packageName)
            putString("channelId", sourceChannelId)
        }
    }

    private fun query(context: Context, extras: Bundle): Bundle {
        val packageName = extras.getString("pkgName").orEmpty()
        val sourceChannelId = extras.getString("channelId").orEmpty()
        if (packageName.isBlank() || sourceChannelId.isBlank()) return result(CODE_UNEXPECTED)

        val manager = NotificationManagerHelper.from(context.applicationContext, packageName)
        val appChannelId = manager.getMipushChannelId(sourceChannelId)
        val channel = manager.getNotificationChannel(appChannelId) ?: return result(CODE_NOT_FOUND)
        val appNotificationOp = AppInfoUtils.getAppNotificationOp(context, packageName, true)
        val state = when (appNotificationOp) {
            AppInfoUtils.AppNotificationOp.UNKNOWN -> -1
            AppInfoUtils.AppNotificationOp.NOT_ALLOWED -> 0
            AppInfoUtils.AppNotificationOp.ALLOWED ->
                if (channel.importance == NotificationManager.IMPORTANCE_NONE) 0 else 1
        }
        val permissions = if (state == 1) computePermissions(channel) else 0
        return result(CODE_OK).apply {
            putInt("state", state)
            putString("channelPermissions", permissions.toString())
            putString("pkgName", packageName)
            putString("channelId", sourceChannelId)
            putString("appChannelId", appChannelId)
        }
    }

    private fun result(code: Int) = Bundle().apply { putInt(StockSurfaceSupport.KEY_CODE, code) }
}
