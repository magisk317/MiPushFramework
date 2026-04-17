package com.xiaomi.push.service.notification

import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.MIPushNotificationHelper

class ColorfulBuilder(
    context: Context,
    i: Int,
    str: String
) : CustomNotificationBuilder(context, i, str) {
    private var mBackgroundBitmap: Bitmap? = null
    private var mBackgroundColor = INVALID_COLOR
    private var mButtonBgColor = INVALID_COLOR
    private var mButtonClickPendingIntent: PendingIntent? = null
    private var mButtonText: CharSequence? = null
    private var mImageTextColor = INVALID_COLOR

    private fun adjustRemoteViewPaddingAndTextColor(
        remoteViews: RemoteViews,
        containerId: Int,
        titleId: Int,
        contentId: Int,
        isLightText: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= 16) {
            val padding = dp2px(6.0f)
            remoteViews.setViewPadding(containerId, padding, 0, padding, 0)
        }
        if (isLightText) {
            remoteViews.setTextColor(titleId, -1)
            remoteViews.setTextColor(contentId, -1)
        } else {
            remoteViews.setTextColor(titleId, -16777216)
            remoteViews.setTextColor(contentId, -16777216)
        }
    }

    private fun createRoundCornerBg(i: Int, i2: Int, i3: Int, f: Float): ShapeDrawable {
        return ShapeDrawable().apply {
            shape = RoundRectShape(floatArrayOf(f, f, f, f, f, f, f, f), null, null)
            paint.color = i
            paint.style = Paint.Style.FILL
            intrinsicWidth = i2
            intrinsicHeight = i3
        }
    }

    fun addAction(charSequence: CharSequence?, pendingIntent: PendingIntent?): ColorfulBuilder {
        if (isSupportCustom()) {
            mButtonText = charSequence
            mButtonClickPendingIntent = pendingIntent
        }
        return this
    }

    override fun applyCustomizations() {
        if (!isSupportCustom()) {
            buildStandardNotification()
            return
        }
        super.applyCustomizations()
        val resources = getContext().resources
        val packageName = getContext().packageName
        val iconId = getResourceIdentifier(resources, COLORFUL_NOTIFICATION_ICON_NAME, "id", packageName)

        if (mIconBitmap == null) {
            showDefaultAppIcon(iconId)
        } else {
            getRemoteViews()?.setImageViewBitmap(iconId, mIconBitmap)
        }

        val titleId = getResourceIdentifier(resources, "title", "id", packageName)
        val contentId = getResourceIdentifier(resources, "content", "id", packageName)
        getRemoteViews()?.setTextViewText(titleId, mTitle)
        getRemoteViews()?.setTextViewText(contentId, mContent)

        if (!TextUtils.isEmpty(mButtonText)) {
            val buttonContainerId = getResourceIdentifier(resources, COLORFUL_NOTIFICATION_BUTTON_CONTAINER_NAME, "id", packageName)
            val buttonId = getResourceIdentifier(resources, COLORFUL_NOTIFICATION_BUTTON_NAME, "id", packageName)
            val buttonBgId = getResourceIdentifier(resources, COLORFUL_NOTIFICATION_BUTTON_BG_NAME, "id", packageName)
            getRemoteViews()?.setViewVisibility(buttonContainerId, 0)
            getRemoteViews()?.setTextViewText(buttonId, mButtonText)
            getRemoteViews()?.setOnClickPendingIntent(buttonContainerId, mButtonClickPendingIntent)

            if (mButtonBgColor != INVALID_COLOR) {
                val width = dp2px(70.0f)
                val height = dp2px(29.0f)
                getRemoteViews()?.setImageViewBitmap(
                    buttonBgId,
                    MIPushNotificationHelper.drawableToBitmap(createRoundCornerBg(mButtonBgColor, width, height, height / 2.0f))
                )
                getRemoteViews()?.setTextColor(buttonId, if (isDarkColor(mButtonBgColor)) -1 else -16777216)
            }
        }

        val bgId = getResourceIdentifier(resources, COLORFUL_NOTIFICATION_BACKGROUND_NAME, "id", packageName)
        val containerId = getResourceIdentifier(resources, COLORFUL_NOTIFICATION_CONTAINER_NAME, "id", packageName)

        if (mBackgroundColor != INVALID_COLOR) {
            val cornerRadius = if (MIUIUtils.getMiuiVersionCode(getContext()) >= 10) 30.0f else 0.0f
            getRemoteViews()?.setImageViewBitmap(
                bgId,
                MIPushNotificationHelper.drawableToBitmap(createRoundCornerBg(mBackgroundColor, BACKGROUND_IMAGE_WIDTH, BACKGROUND_IMAGE_HEIGHT, cornerRadius))
            )
            adjustRemoteViewPaddingAndTextColor(
                getRemoteViews()!!, containerId, titleId, contentId, isDarkColor(mBackgroundColor)
            )
        } else if (mBackgroundBitmap != null) {
            if (MIUIUtils.getMiuiVersionCode(getContext()) >= 10) {
                getRemoteViews()?.setImageViewBitmap(bgId, bitmapWithRound(mBackgroundBitmap!!, 30.0f))
            } else {
                getRemoteViews()?.setImageViewBitmap(bgId, mBackgroundBitmap)
            }
            if (mPushExtra != null && mImageTextColor == INVALID_COLOR) {
                setImageTextColor(mPushExtra!![MIPushNotificationHelper.NOTIFICATION_IMAGE_TEXT_COLOR])
            }
            val textColor = mImageTextColor
            adjustRemoteViewPaddingAndTextColor(
                getRemoteViews()!!, containerId, titleId, contentId,
                textColor == INVALID_COLOR || !isDarkColor(textColor)
            )
        } else if (Build.VERSION.SDK_INT >= 24) {
            getRemoteViews()?.setViewVisibility(iconId, 8)
            getRemoteViews()?.setViewVisibility(bgId, 8)
            try {
                val clazz = SystemUtils.loadClass(getContext(), "android.app.Notification\$DecoratedCustomViewStyle")
                JavaCalls.callMethod(
                    this, "setStyle",
                    clazz.getConstructor().newInstance()
                )
            } catch (e: Exception) {
                MyLog.w("load class DecoratedCustomViewStyle failed")
            }
        }

        val bundle = Bundle().apply {
            putBoolean("miui.customHeight", true)
        }
        addExtras(bundle)
        setCustomContentView(getRemoteViews())
    }

    override fun checkSupportCustom(): Boolean {
        if (!MIUIUtils.isXMSF(getContext())) return false
        val resources = getContext().resources
        val packageName = getContext().packageName
        val iconId = getResourceIdentifier(resources, COLORFUL_NOTIFICATION_ICON_NAME, "id", packageName)
        val titleId = getResourceIdentifier(resources, "title", "id", packageName)
        val contentId = getResourceIdentifier(resources, "content", "id", packageName)
        return iconId != 0 && titleId != 0 && contentId != 0
    }

    override fun getCopyLayout(): String? = COLORFUL_NOTIFICATION_LAYOUT_COPY_NAME
    override fun getOriginalLayout(): String = COLORFUL_NOTIFICATION_LAYOUT_NAME

    fun setActionBackground(i: Int): ColorfulBuilder {
        if (isSupportCustom()) {
            mButtonBgColor = i
        }
        return this
    }

    fun setActionBackground(str: String?): ColorfulBuilder {
        if (isSupportCustom() && !TextUtils.isEmpty(str)) {
            try {
                mButtonBgColor = Color.parseColor(str)
            } catch (e: Exception) {
                MyLog.w("parse colorful notification button bg color error")
            }
        }
        return this
    }

    fun setBackground(i: Int): ColorfulBuilder {
        if (isSupportCustom()) {
            mBackgroundColor = i
        }
        return this
    }

    fun setBackground(bitmap: Bitmap?): ColorfulBuilder {
        if (isSupportCustom() && bitmap != null) {
            if (bitmap.width != BACKGROUND_IMAGE_WIDTH ||
                bitmap.height < BACKGROUND_IMAGE_MIN_HEIGHT ||
                bitmap.height > BACKGROUND_IMAGE_MAX_HEIGHT
            ) {
                MyLog.w("colorful notification bg image resolution error, must [984*177, 984*207]")
            } else {
                mBackgroundBitmap = bitmap
            }
        }
        return this
    }

    fun setBackground(str: String?): ColorfulBuilder {
        if (isSupportCustom() && !TextUtils.isEmpty(str)) {
            try {
                mBackgroundColor = Color.parseColor(str)
            } catch (e: Exception) {
                MyLog.w("parse colorful notification bg color error")
            }
        }
        return this
    }

    fun setImageTextColor(str: String?): ColorfulBuilder {
        if (isSupportCustom() && !TextUtils.isEmpty(str)) {
            try {
                mImageTextColor = Color.parseColor(str)
            } catch (e: Exception) {
                MyLog.w("parse colorful notification image text color error")
            }
        }
        return this
    }

    companion object {
        private const val BACKGROUND_IMAGE_HEIGHT = 192
        private const val BACKGROUND_IMAGE_MAX_HEIGHT = 207
        private const val BACKGROUND_IMAGE_MIN_HEIGHT = 177
        private const val BACKGROUND_IMAGE_WIDTH = 984
        private const val COLORFUL_NOTIFICATION_BACKGROUND_NAME = "bg"
        private const val COLORFUL_NOTIFICATION_BUTTON_BG_NAME = "buttonBg"
        private const val COLORFUL_NOTIFICATION_BUTTON_CONTAINER_NAME = "buttonContainer"
        private const val COLORFUL_NOTIFICATION_BUTTON_NAME = "button"
        private const val COLORFUL_NOTIFICATION_CONTAINER_NAME = "container"
        private const val COLORFUL_NOTIFICATION_CONTENT_NAME = "content"
        private const val COLORFUL_NOTIFICATION_ICON_NAME = "icon"
        private const val COLORFUL_NOTIFICATION_LAYOUT_COPY_NAME = "notification_colorful_copy"
        private const val COLORFUL_NOTIFICATION_LAYOUT_NAME = "notification_colorful"
        private const val COLORFUL_NOTIFICATION_TITLE_NAME = "title"
    }
}
