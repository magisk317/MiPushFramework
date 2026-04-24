package com.xiaomi.push.service.notification
import io.github.magisk317.mipush.protocol.model.*

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.NotificationManagerHelper
import com.xiaomi.push.service.NotificationUtils

abstract class CustomNotificationBuilder(
    context: Context,
    private val mNotifyId: Int,
    private val mTargetPackageName: String
) : BuilderCompat(context) {
    protected var mContent: CharSequence? = null
    protected var mIconBitmap: Bitmap? = null
    protected var mPushExtra: Map<String, String>? = null
    protected var mTitle: CharSequence? = null

    private var mIsSupportCustom = false
    private var mRemoteViews: RemoteViews? = null
    private var mUseCopyLayout = false

    init {
        createRemoteViews()
    }

    private fun builderSetTitle(): Boolean {
        return mPushExtra?.get(MIPushNotificationHelper.NOTIFICATION_CUSTOM_BUILDER_SET_TITLE)
            ?.toBoolean() ?: false
    }

    private fun createRemoteViews() {
        val resourceId = getResourceIdentifier(
            getContext().resources,
            getLayoutName(),
            "layout",
            getContext().packageName
        )
        if (resourceId == 0) {
            MyLog.w("create RemoteViews failed, no such layout resource was found")
        } else {
            mRemoteViews = RemoteViews(getContext().packageName, resourceId)
            mIsSupportCustom = checkSupportCustom()
        }
    }

    private fun getAppIconBitmap(): Bitmap? {
        return MIPushNotificationHelper.drawableToBitmap(
            AppInfoUtils.getAppIconDrawable(getContext(), mTargetPackageName)
        )
    }

    private fun getLayoutName(): String {
        mUseCopyLayout = useCopyLayout()
        val layout = if (mUseCopyLayout) getCopyLayout() else getOriginalLayout()
        return layout.orEmpty()
    }

    private fun reapplyDisallowed(): Boolean {
        return !(TextUtils.isEmpty(getCopyLayout()) || TextUtils.isEmpty(mTargetPackageName))
    }

    private fun setContentTitleAndText() {
        if (Build.VERSION.SDK_INT >= 11) {
            super.setContentTitle(mTitle)
            super.setContentText(mContent)
        }
    }

    private fun shouldUseCopyLayout(): Boolean {
        if (Build.VERSION.SDK_INT < 20) {
            return false
        }
        val activeNotifications = NotificationManagerHelper.from(getContext(), mTargetPackageName)
            .getActiveNotifications()
        if (activeNotifications.isNullOrEmpty()) {
            return false
        }
        for (statusBarNotification in activeNotifications) {
            if (statusBarNotification.id == mNotifyId) {
                val notification = statusBarNotification.notification ?: return false
                return !notification.extras.getBoolean(CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN, true)
            }
        }
        return false
    }

    private fun useCopyLayout(): Boolean {
        return reapplyDisallowed() && shouldUseCopyLayout()
    }

    override fun addAction(i: Int, charSequence: CharSequence?, pendingIntent: PendingIntent?): CustomNotificationBuilder {
        return this
    }

    override fun addAction(action: Notification.Action?): CustomNotificationBuilder {
        return this
    }

    override fun applyCustomizations() {
        super.applyCustomizations()
        val bundle = Bundle()
        if (reapplyDisallowed()) {
            bundle.putBoolean(CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN, mUseCopyLayout)
        } else {
            bundle.putBoolean(CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN, false)
        }
        bundle.putBoolean("miui.customHeight", false)
        addExtras(bundle)
        if (builderSetTitle() || !NotificationUtils.isUserAggregate(getContext().contentResolver)) {
            setContentTitleAndText()
        }
    }

    protected fun bitmapWithRound(bitmap: Bitmap, f: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = true }
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawRoundRect(RectF(rect), f, f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
        return output
    }

    protected fun buildStandardNotification() {
        if (Build.VERSION.SDK_INT >= 11) {
            super.setContentTitle(mTitle)
            super.setContentText(mContent)
            mIconBitmap?.let { super.setLargeIcon(it) }
        }
    }

    protected fun dp2px(f: Float): Int {
        return ((f * getContext().resources.displayMetrics.density) + 0.5f).toInt()
    }

    protected abstract fun checkSupportCustom(): Boolean
    protected abstract fun getCopyLayout(): String?
    protected abstract fun getOriginalLayout(): String?

    protected fun getRemoteViews(): RemoteViews? = mRemoteViews
    protected fun getTargetPackageName(): String = mTargetPackageName
    protected fun isSupportCustom(): Boolean = mIsSupportCustom

    protected fun isDarkColor(i: Int): Boolean {
        return ((Color.red(i) * 0.299 + Color.green(i) * 0.587) + Color.blue(i) * 0.114) < 192.0
    }

    override fun setContentText(charSequence: CharSequence?): CustomNotificationBuilder {
        mContent = charSequence
        return this
    }

    override fun setContentTitle(charSequence: CharSequence?): CustomNotificationBuilder {
        mTitle = charSequence
        return this
    }

    override fun setLargeIcon(bitmap: Bitmap?): CustomNotificationBuilder {
        mIconBitmap = bitmap
        return this
    }

    fun setPushExtra(map: Map<String, String>?): CustomNotificationBuilder {
        mPushExtra = map
        return this
    }

    protected fun showDefaultAppIcon(i: Int) {
        val appIconBitmap = getAppIconBitmap()
        if (appIconBitmap != null) {
            getRemoteViews()?.setImageViewBitmap(i, appIconBitmap)
            return
        }
        val appIconId = AppInfoUtils.getAppIconId(getContext(), mTargetPackageName)
        if (appIconId != 0) {
            getRemoteViews()?.setImageViewResource(i, appIconId)
        }
    }

    companion object {
        private const val CUSTOM_NOTIFICATION_EXTRA_COPY_FLAG_BOOLEAN = "mipush.customCopyLayout"
        const val INVALID_COLOR = 16777216
    }
}
