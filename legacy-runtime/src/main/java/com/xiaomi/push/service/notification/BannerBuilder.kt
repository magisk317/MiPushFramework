package com.xiaomi.push.service.notification

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.widget.RemoteViews
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.MIPushNotificationHelper

class BannerBuilder(context: Context, str: String) : CustomNotificationBuilder(context, 0, str) {
    private var mBannerBitmap: Bitmap? = null
    private var mImageTextColor = INVALID_COLOR

    override fun applyCustomizations() {
        if (!isSupportCustom() || mBannerBitmap == null) {
            buildStandardNotification()
            return
        }
        super.applyCustomizations()
        val resources = getContext().resources
        val packageName = getContext().packageName
        val bgId = getResourceIdentifier(resources, BANNER_NOTIFICATION_BACKGROUND_NAME, "id", packageName)

        if (MIUIUtils.getMiuiVersionCode(getContext()) >= 10) {
            getRemoteViews()?.setImageViewBitmap(bgId, bitmapWithRound(mBannerBitmap!!, 30.0f))
        } else {
            getRemoteViews()?.setImageViewBitmap(bgId, mBannerBitmap)
        }

        showDefaultAppIcon(getResourceIdentifier(resources, BANNER_NOTIFICATION_ICON_NAME, "id", packageName))
        val titleId = getResourceIdentifier(resources, "title", "id", packageName)
        getRemoteViews()?.setTextViewText(titleId, mTitle)

        if (mPushExtra != null && mImageTextColor == INVALID_COLOR) {
            setImageTextColor(mPushExtra!![MIPushNotificationHelper.NOTIFICATION_IMAGE_TEXT_COLOR])
        }

        val textColor = mImageTextColor
        getRemoteViews()?.setTextColor(titleId, if (textColor == INVALID_COLOR || !isDarkColor(textColor)) -1 else -16777216)
        setCustomContentView(getRemoteViews())

        val bundle = Bundle().apply {
            putBoolean("miui.customHeight", true)
        }
        addExtras(bundle)
    }

    override fun checkSupportCustom(): Boolean {
        if (!MIUIUtils.isXMSF(getContext())) return false
        val resources = getContext().resources
        val packageName = getContext().packageName
        val bgId = getResourceIdentifier(resources, BANNER_NOTIFICATION_BACKGROUND_NAME, "id", packageName)
        val iconId = getResourceIdentifier(resources, BANNER_NOTIFICATION_ICON_NAME, "id", packageName)
        val titleId = getResourceIdentifier(resources, "title", "id", packageName)
        return bgId != 0 && iconId != 0 && titleId != 0 && MIUIUtils.getMiuiVersionCode(getContext()) >= 9
    }

    override fun getCopyLayout(): String? = null
    override fun getOriginalLayout(): String = BANNER_NOTIFICATION_LAYOUT_NAME

    fun setBanner(bitmap: Bitmap?): BannerBuilder {
        if (isSupportCustom() && bitmap != null) {
            if (bitmap.width != BANNER_IMAGE_WIDTH ||
                BANNER_IMAGE_MIN_HEIGHT > bitmap.height ||
                bitmap.height > BANNER_IMAGE_MAX_HEIGHT
            ) {
                MyLog.w("colorful notification banner image resolution error, must belong to [984*184, 984*1678]")
            } else {
                mBannerBitmap = bitmap
            }
        }
        return this
    }

    fun setImageTextColor(str: String?): BannerBuilder {
        if (isSupportCustom() && !TextUtils.isEmpty(str)) {
            try {
                mImageTextColor = Color.parseColor(str)
            } catch (e: Exception) {
                MyLog.w("parse banner notification image text color error")
            }
        }
        return this
    }

    override fun setLargeIcon(bitmap: Bitmap?): CustomNotificationBuilder = this

    companion object {
        private const val BANNER_IMAGE_MAX_HEIGHT = 1678
        private const val BANNER_IMAGE_MIN_HEIGHT = 184
        private const val BANNER_IMAGE_WIDTH = 984
        private const val BANNER_NOTIFICATION_BACKGROUND_NAME = "bg"
        private const val BANNER_NOTIFICATION_ICON_NAME = "icon"
        private const val BANNER_NOTIFICATION_LAYOUT_NAME = "notification_banner"
        private const val BANNER_NOTIFICATION_TITLE_NAME = "title"
    }
}
