package io.github.magisk317.mipush.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.xiaomi.push.service.MyNotificationIconHelper
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.platform.support.XMPushUtils

/** Shell-local payload large-icon download and presentation adaptation. */
internal object NotificationLargeIconSupport {
    private const val KIB = 1024

    fun getLargeIcon(context: Context, metaInfo: PushMetaInfo, iconUri: String?): Bitmap? {
        var largeIcon = if (iconUri == null) null else getBitmapFromUri(context, iconUri, 200 * KIB)
        if (largeIcon != null) {
            largeIcon = roundLargeIconIfConfigured(metaInfo, largeIcon)
        }
        return largeIcon
    }

    fun roundLargeIconIfConfigured(metaInfo: PushMetaInfo, largeIcon: Bitmap): Bitmap {
        var result = largeIcon
        val custom = XMPushUtils.getConfiguration(metaInfo)
        if (custom.roundLargeIcon(false)) {
            result = ImgUtils.trimImgToCircle(result, Color.TRANSPARENT)
        }
        return result
    }

    fun getBitmapFromUri(context: Context, iconUri: String?, maxDownloadBytes: Int): Bitmap? {
        var bitmap: Bitmap? = null
        if (iconUri != null) {
            val safeUri = if (iconUri.startsWith("http://")) {
                iconUri.replaceFirst("http://", "https://")
            } else {
                iconUri
            }
            if (safeUri.startsWith("http")) {
                val result = MyNotificationIconHelper.getIconFromUrl(context, safeUri, maxDownloadBytes)
                bitmap = result.bitmap
            } else {
                bitmap = MyNotificationIconHelper.getIconFromUri(context, safeUri)
            }
        }
        return bitmap
    }
}
