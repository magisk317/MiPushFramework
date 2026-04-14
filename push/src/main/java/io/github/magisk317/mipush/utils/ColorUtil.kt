package io.github.magisk317.mipush.utils

import android.app.Notification
import android.graphics.Bitmap
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.palette.graphics.Palette

object ColorUtil {
    @JvmStatic
    fun getIconColor(bitmap: Bitmap): Int {
        return Palette.from(bitmap).generate().getVibrantColor(Notification.COLOR_DEFAULT)
    }

    @JvmStatic
    fun createColorSubtext(appName: CharSequence, color: Int): Spannable {
        val amended: Spannable = SpannableStringBuilder(appName)
        amended.setSpan(ForegroundColorSpan(color), 0, amended.length, 0)
        return amended
    }
}
