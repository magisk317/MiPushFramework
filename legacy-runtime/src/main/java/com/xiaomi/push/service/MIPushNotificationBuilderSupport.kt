package com.xiaomi.push.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.notification.BannerBuilder
import com.xiaomi.push.service.notification.BuilderCompat
import com.xiaomi.push.service.notification.ColorfulBuilder
import com.xiaomi.xmpush.thrift.XmPushActionContainer

internal object MIPushNotificationBuilderSupport {
    private const val STYLE_TYPE = "notification_style_type"
    private const val STYLE_BIG_PICTURE = "2"
    private const val STYLE_BIG_TEXT = "1"
    private const val STYLE_COLORFUL = "3"
    private const val STYLE_BANNER = "4"
    private const val STYLE_BIG_PICTURE_URI = "notification_bigPic_uri"
    private const val BANNER_IMAGE_URI = "notification_banner_image_uri"
    private const val COLORFUL_BUTTON_TEXT = "notification_colorful_button_text"
    private const val COLORFUL_BUTTON_NOTIFY_EFFECT = "notification_colorful_button_notify_effect"
    private const val COLORFUL_BUTTON_INTENT_URI = "notification_colorful_button_intent_uri"
    private const val COLORFUL_BUTTON_INTENT_CLASS = "notification_colorful_button_intent_class"
    private const val COLORFUL_BUTTON_WEB_URI = "notification_colorful_button_web_uri"
    private const val COLORFUL_BUTTON_BG_COLOR = "notification_colorful_button_bg_color"
    private const val COLORFUL_BG_COLOR = "notification_colorful_bg_color"
    private const val COLORFUL_BG_IMAGE_URI = "notification_colorful_bg_image_uri"
    private const val NOTIFICATION_SMALL_ICON_URI = "notification_small_icon_uri"
    private const val NOTIFICATION_LARGE_ICON_URI = "notification_large_icon_uri"
    private const val NOTIFICATION_CHANNEL_NAME = "channel_name"
    private const val NOTIFICATION_CHANNEL_IMPORTANCE = "channel_importance"
    private const val NOTIFICATION_CHANNEL_DESCRIPTION = "channel_description"
    private const val NOTIFICATION_CHANNEL_ID = "channel_id"
    private const val NOTIFICATION_PRIORITY = "notification_priority"
    private const val NOTIFICATION_TIMEOUT = "timeout"

    @JvmStatic
    fun createBuilder(
        context: Context,
        remoteViews: RemoteViews?,
        extra: Map<String, String>?,
        contentText: String,
        packageName: String,
        notificationId: Int,
    ): BuilderCompat {
        if (remoteViews != null) {
            return BuilderCompat(context).setCustomContentView(remoteViews)
        }
        if (extra == null || !extra.containsKey(STYLE_TYPE)) {
            return BuilderCompat(context)
        }
        return createBuilderWithStyle(context, extra, contentText, packageName, notificationId)
    }

    @JvmStatic
    fun applyBaseStyle(
        builder: BuilderCompat,
        context: Context,
        container: XmPushActionContainer,
        extra: Map<String, String>?,
        titleAndDesc: Array<String>,
        clickedPendingIntent: PendingIntent,
    ) {
        builder.setContentTitle(titleAndDesc[0])
        builder.setContentText(titleAndDesc[1])
        builder.setWhen(System.currentTimeMillis())
        val showWhen = extra?.get(MIPushNotificationHelper.NOTIFICATION_SHOW_WHEN)
        if (TextUtils.isEmpty(showWhen)) {
            if (Build.VERSION.SDK_INT >= 24) {
                builder.setShowWhen(true)
            }
        } else {
            builder.setShowWhen(showWhen.toBoolean())
        }
        builder.setContentIntent(clickedPendingIntent)
        MIPushNotificationActionSupport.setNotificationStyleAction(builder, context, container.packageName, extra)
    }

    @JvmStatic
    fun applyIcons(
        context: Context,
        builder: BuilderCompat,
        extra: Map<String, String>?,
        packageName: String,
        largeIconId: Int,
        smallIconId: Int,
    ) {
        if (largeIconId > 0 && smallIconId > 0) {
            builder.setLargeIcon(MIPushNotificationViewSupport.getBitmapFromId(context, largeIconId))
            builder.setSmallIcon(smallIconId)
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                val onlineSmallIcon = extra?.get(NOTIFICATION_SMALL_ICON_URI)?.let {
                    MIPushOnlineResourceSupport.getOnlinePictureResource(context, it, true)
                }
                if (onlineSmallIcon != null) {
                    builder.setSmallIcon(Icon.createWithBitmap(onlineSmallIcon))
                } else {
                    builder.setSmallIcon(Icon.createWithResource(packageName, NotificationUtils.getIdForSmallIconFromTargetPkg(context, packageName)))
                }
            } catch (_: Throwable) {
                builder.setSmallIcon(MIPushNotificationViewSupport.getIdForSmallIcon(context, packageName))
            }
        } else {
            builder.setSmallIcon(MIPushNotificationViewSupport.getIdForSmallIcon(context, packageName))
        }
        extra?.get(NOTIFICATION_LARGE_ICON_URI)?.let {
            MIPushOnlineResourceSupport.getOnlinePictureResource(context, it, false)
        }?.let(builder::setLargeIcon)
    }

    @JvmStatic
    fun applyMetadata(
        context: Context,
        builder: BuilderCompat,
        extra: Map<String, String>?,
        notifyType: Int,
        packageName: String,
    ) {
        extra?.get(MIPushNotificationHelper.NOTIFICATION_TICKER)?.takeIf { it.isNotEmpty() }?.let(builder::setTicker)
        if (Build.VERSION.SDK_INT >= 16) {
            builder.setPriority(getPriority(extra))
        }
        builder.setDefaults(notifyType)
        if (extra != null && (notifyType and 1) != 0) {
            val soundUri = extra[MIPushNotificationHelper.NOTIFICATION_SOUND_URI]
            if (!TextUtils.isEmpty(soundUri) && soundUri!!.startsWith(MIPushNotificationHelper.ANDROID_RESOURCE + packageName)) {
                builder.setDefaults(notifyType xor 1)
                builder.setSound(Uri.parse(soundUri))
            }
        }
        builder.setAutoCancel(true)
    }

    @JvmStatic
    fun ensureChannel(context: Context, builder: BuilderCompat, packageName: String, extra: Map<String, String>?) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val manager = NotificationManagerHelper.from(context, packageName)
        val sourceChannelId = extra?.get(NOTIFICATION_CHANNEL_ID).takeUnless { it.isNullOrEmpty() }
            ?: NotificationManagerHelper.DEFAULT_ID
        val channelId = manager.getMipushChannelId(sourceChannelId)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, getChannelName(context, packageName, extra), getChannelImportance(extra))
            setChannelDescription(channel, extra)
            manager.createNotificationChannel(channel)
        }
        builder.setChannelId(channelId)
        val timeout = getTimeout(extra)
        if (timeout > 0) {
            builder.setTimeoutAfter(timeout * 1000L)
        }
    }

    private fun createBuilderWithStyle(
        context: Context,
        extra: Map<String, String>,
        contentText: String,
        packageName: String,
        notificationId: Int,
    ): BuilderCompat {
        val styleType = extra[STYLE_TYPE]
        if (Build.VERSION.SDK_INT >= 16 && STYLE_BIG_PICTURE == styleType) {
            val builder = BuilderCompat(context)
            val bitmap = extra[STYLE_BIG_PICTURE_URI]?.takeIf { it.isNotEmpty() }
                ?.let { MIPushOnlineResourceSupport.getOnlinePictureResource(context, it, false) }
            if (bitmap == null) {
                MyLog.w("can not get big picture.")
                return builder
            }
            val style = builder.createBigPictureStyle()
            style.bigPicture(bitmap)
            style.setSummaryText(contentText)
            style.bigLargeIcon(null as Bitmap?)
            builder.setStyle(style)
            return builder
        }
        if (Build.VERSION.SDK_INT >= 16 && STYLE_BIG_TEXT == styleType) {
            return BuilderCompat(context).setStyle(Notification.BigTextStyle().bigText(contentText))
        }
        if (STYLE_BANNER == styleType && MIUIUtils.isXMSF(context)) {
            return BannerBuilder(context, packageName).apply {
                extra[BANNER_IMAGE_URI]?.takeIf { it.isNotEmpty() }
                    ?.let { setBanner(MIPushOnlineResourceSupport.getOnlinePictureResource(context, it, false)) }
                setPushExtra(extra)
            }
        }
        if (STYLE_COLORFUL == styleType && MIUIUtils.isXMSF(context)) {
            return ColorfulBuilder(context, notificationId, packageName).apply {
                val pendingIntent = if (!TextUtils.isEmpty(extra[COLORFUL_BUTTON_TEXT])) {
                    MIPushNotificationActionSupport.getStylePendingIntent(
                        context,
                        packageName,
                        extra,
                        COLORFUL_BUTTON_NOTIFY_EFFECT,
                        COLORFUL_BUTTON_INTENT_URI,
                        COLORFUL_BUTTON_INTENT_CLASS,
                        COLORFUL_BUTTON_WEB_URI,
                    )
                } else {
                    null
                }
                if (pendingIntent != null) {
                    addAction(extra[COLORFUL_BUTTON_TEXT], pendingIntent).setActionBackground(extra[COLORFUL_BUTTON_BG_COLOR])
                }
                if (!TextUtils.isEmpty(extra[COLORFUL_BG_COLOR])) {
                    setBackground(extra[COLORFUL_BG_COLOR])
                } else if (!TextUtils.isEmpty(extra[COLORFUL_BG_IMAGE_URI])) {
                    setBackground(MIPushOnlineResourceSupport.getOnlinePictureResource(context, extra[COLORFUL_BG_IMAGE_URI]!!, false))
                }
                setPushExtra(extra)
            }
        }
        return BuilderCompat(context)
    }

    private fun getChannelImportance(extra: Map<String, String>?): Int {
        var importance = 3
        val raw = extra?.get(NOTIFICATION_CHANNEL_IMPORTANCE)
        if (!TextUtils.isEmpty(raw)) {
            try {
                MyLog.v("importance=$raw")
                importance = raw?.toInt() ?: 3
            } catch (e: Exception) {
                MyLog.e("parsing channel importance error: $e")
            }
        }
        return importance
    }

    private fun getChannelName(context: Context, packageName: String, extra: Map<String, String>?): String {
        return extra?.get(NOTIFICATION_CHANNEL_NAME).takeUnless { it.isNullOrEmpty() }
            ?: AppInfoUtils.getAppLabel(context, packageName)
    }

    private fun getPriority(extra: Map<String, String>?): Int {
        var priority = 0
        val raw = extra?.get(NOTIFICATION_PRIORITY)
        if (!TextUtils.isEmpty(raw)) {
            try {
                MyLog.v("priority=$raw")
                priority = raw?.toInt() ?: 0
            } catch (e: Exception) {
                MyLog.e("parsing notification priority error: $e")
            }
        }
        return priority
    }

    private fun getTimeout(extra: Map<String, String>?): Int {
        return extra?.get(NOTIFICATION_TIMEOUT)?.toIntOrNull() ?: 0
    }

    private fun setChannelDescription(channel: NotificationChannel, extra: Map<String, String>?) {
        extra?.get(NOTIFICATION_CHANNEL_DESCRIPTION)?.takeIf { it.isNotEmpty() }?.let {
            JavaCalls.callMethod(channel, "setDescription", it)
        }
    }
}
