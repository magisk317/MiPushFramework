package com.xiaomi.push.service.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.reflect.JavaCalls

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/ha/b.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/notification/BuilderCompat.java
 * Stock class name is obfuscated as ha.b; this file keeps the deobfuscated BuilderCompat wrapper surface.
 */
open class BuilderCompat(private val mContext: Context) {
    private val builder: Notification.Builder = createBuilder(mContext)

    fun addExtras(bundle: Bundle): BuilderCompat {
        if (Build.VERSION.SDK_INT >= 20) {
            builder.addExtras(bundle)
        }
        return this
    }

    open fun addAction(i: Int, charSequence: CharSequence?, pendingIntent: PendingIntent?): BuilderCompat {
        JavaCalls.callMethod(builder, "addAction", i, charSequence, pendingIntent)
        return this
    }

    open fun addAction(action: Notification.Action?): BuilderCompat {
        if (Build.VERSION.SDK_INT >= 20) {
            builder.addAction(action)
        }
        return this
    }

    protected open fun applyCustomizations() {}

    fun build(): Notification {
        applyCustomizations()
        return builder.build()
    }

    protected fun getContext(): Context = mContext

    fun getExtras(): Bundle = builder.extras

    fun getPlatformBuilder(): Notification.Builder = builder

    fun getResourceIdentifier(resources: Resources, str: String?, str2: String?, str3: String?): Int {
        if (TextUtils.isEmpty(str)) {
            return 0
        }
        return resources.getIdentifier(str, str2, str3)
    }

    fun setCustomContentView(remoteViews: RemoteViews?): BuilderCompat {
        if (Build.VERSION.SDK_INT >= 24) {
            builder.setCustomContentView(remoteViews)
        } else {
            JavaCalls.callMethod(builder, "setContent", remoteViews)
        }
        return this
    }

    fun setContentIntent(pendingIntent: PendingIntent?): BuilderCompat {
        builder.setContentIntent(pendingIntent)
        return this
    }

    open fun setContentText(charSequence: CharSequence?): BuilderCompat {
        builder.setContentText(charSequence)
        return this
    }

    open fun setContentTitle(charSequence: CharSequence?): BuilderCompat {
        builder.setContentTitle(charSequence)
        return this
    }

    fun setAutoCancel(z: Boolean): BuilderCompat {
        builder.setAutoCancel(z)
        return this
    }

    fun setChannelId(str: String?): BuilderCompat {
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setChannelId(str)
        }
        return this
    }

    fun setDefaults(i: Int): BuilderCompat {
        JavaCalls.callMethod(builder, "setDefaults", i)
        return this
    }

    fun setGroup(str: String?): BuilderCompat {
        builder.setGroup(str)
        return this
    }

    fun setGroupAlertBehavior(i: Int): BuilderCompat {
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setGroupAlertBehavior(i)
        }
        return this
    }

    fun setGroupSummary(z: Boolean): BuilderCompat {
        builder.setGroupSummary(z)
        return this
    }

    open fun setLargeIcon(bitmap: Bitmap?): BuilderCompat {
        builder.setLargeIcon(bitmap)
        return this
    }

    fun setPriority(i: Int): BuilderCompat {
        JavaCalls.callMethod(builder, "setPriority", i)
        return this
    }

    fun setShowWhen(z: Boolean): BuilderCompat {
        builder.setShowWhen(z)
        return this
    }

    fun setSmallIcon(i: Int): BuilderCompat {
        builder.setSmallIcon(i)
        return this
    }

    fun setSmallIcon(icon: Icon?): BuilderCompat {
        if (Build.VERSION.SDK_INT >= 23) {
            builder.setSmallIcon(icon)
        }
        return this
    }

    fun setSound(uri: Uri?): BuilderCompat {
        JavaCalls.callMethod(builder, "setSound", uri)
        return this
    }

    fun setStyle(style: Notification.Style?): BuilderCompat {
        builder.setStyle(style)
        return this
    }

    fun setTicker(charSequence: CharSequence?): BuilderCompat {
        builder.setTicker(charSequence)
        return this
    }

    fun setTimeoutAfter(j: Long): BuilderCompat {
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setTimeoutAfter(j)
        }
        return this
    }

    fun setWhen(j: Long): BuilderCompat {
        builder.setWhen(j)
        return this
    }

    fun createBigPictureStyle(): Notification.BigPictureStyle {
        return try {
            Notification.BigPictureStyle::class.java
                .getConstructor(Notification.Builder::class.java)
                .newInstance(builder)
        } catch (e: ReflectiveOperationException) {
            throw IllegalStateException("Failed to create BigPictureStyle", e)
        }
    }

    companion object {
        private fun createBuilder(context: Context): Notification.Builder {
            return if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, "default")
            } else {
                try {
                    Notification.Builder::class.java
                        .getDeclaredConstructor(Context::class.java)
                        .newInstance(context)
                } catch (e: ReflectiveOperationException) {
                    throw IllegalStateException("Failed to create Notification.Builder", e)
                }
            }
        }
    }
}
