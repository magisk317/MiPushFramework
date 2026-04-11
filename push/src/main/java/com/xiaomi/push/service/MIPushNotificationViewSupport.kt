package com.xiaomi.push.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.xiaomi.push.service.notification.BuilderCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer

object MIPushNotificationViewSupport {
    private const val NOTIFICATION_ICON = "mipush_notification"
    private const val NOTIFICATION_SMALL_ICON = "mipush_small_notification"

    @JvmStatic
    fun determineTitleAndDespByDIP(context: Context, pushMetaInfo: PushMetaInfo): Array<String> {
        var title = pushMetaInfo.title
        var description = pushMetaInfo.description
        val extra = pushMetaInfo.extra
        if (extra != null) {
            val widthDp =
                ((context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density) + 0.5f).toInt()
            when {
                widthDp <= 320 -> {
                    extra["title_short"]?.takeIf { it.isNotEmpty() }?.let { title = it }
                    extra["description_short"]?.takeIf { it.isNotEmpty() }?.let { description = it }
                }

                widthDp > 360 -> {
                    extra["title_long"]?.takeIf { it.isNotEmpty() }?.let { title = it }
                    extra["description_long"]?.takeIf { it.isNotEmpty() }?.let { description = it }
                }
            }
        }
        return arrayOf(title, description)
    }

    @JvmStatic
    fun getNotificationForCustomLayout(context: Context, container: XmPushActionContainer): RemoteViews? {
        return MIPushNotificationCustomLayoutSupport.getNotificationForCustomLayout(context, container)
    }

    @JvmStatic
    fun getNotificationForLargeIcons(
        context: Context,
        container: XmPushActionContainer,
        remoteViews: RemoteViews?,
        pendingIntent: PendingIntent,
        notificationId: Int,
    ): MIPushNotificationHelper.GetNotificationResult {
        val result = MIPushNotificationHelper.GetNotificationResult()
        val metaInfo = container.metaInfo
        val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
        val extra = metaInfo.extra
        val titleAndDescription = determineTitleAndDespByDIP(context, metaInfo)
        val builder = MIPushNotificationBuilderSupport.createBuilder(
            context,
            remoteViews,
            extra,
            titleAndDescription[1],
            targetPackage,
            notificationId,
        )
        MIPushNotificationBuilderSupport.applyBaseStyle(
            builder,
            context,
            container,
            extra,
            titleAndDescription,
            pendingIntent,
        )
        val largeIconId = getIconId(context, targetPackage, NOTIFICATION_ICON)
        val smallIconId = getIconId(context, targetPackage, NOTIFICATION_SMALL_ICON)
        MIPushNotificationBuilderSupport.applyIcons(context, builder, extra, targetPackage, largeIconId, smallIconId)
        var notifyType = metaInfo.notifyType
        if (MIPushNotificationHelper.hasLocalNotifyType(context, targetPackage)) {
            notifyType = MIPushNotificationHelper.getLocalNotifyType(context, targetPackage)
        }
        MIPushNotificationBuilderSupport.applyMetadata(context, builder, extra, notifyType, targetPackage)
        MIPushNotificationBuilderSupport.ensureChannel(context, builder, targetPackage, extra)
        result.notification = builder.build().apply {
            flags = flags or Notification.FLAG_AUTO_CANCEL
        }
        return result
    }

    @JvmStatic
    fun getIdForSmallIcon(context: Context, packageName: String): Int {
        val largeIconId = getIconId(context, packageName, NOTIFICATION_ICON)
        var resolvedIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON)
        if (largeIconId > 0) {
            resolvedIconId = largeIconId
        } else if (resolvedIconId <= 0) {
            resolvedIconId = context.applicationInfo.icon
        }
        if (resolvedIconId == 0 && Build.VERSION.SDK_INT >= 9) {
            resolvedIconId = context.applicationInfo.logo
        }
        return resolvedIconId
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @JvmStatic
    fun getBitmapFromId(context: Context, resId: Int): Bitmap? {
        return ContextCompat.getDrawable(context, resId)?.let(::drawableToBitmap)
    }

    private fun getIconId(context: Context, packageName: String, name: String): Int {
        return if (packageName == context.packageName) {
            context.resources.getIdentifier(name, "drawable", packageName)
        } else {
            0
        }
    }
}
