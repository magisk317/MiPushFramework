package io.github.magisk317.mipush.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.magisk317.mipush.notification.policy.CustomConfiguration

/** Shell-local Focus notification payload construction. */
internal object NotificationFocusPayloadSupport {
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val PIC_ICON = "miui.focus.pic_mipush_icon"

internal fun collectFocusPicUris(configuration: CustomConfiguration): Map<String, String> {
    return configuration.keys()
        .filter { it.startsWith("miui.focus.pic_") }
        .mapNotNull { key ->
            val uri = configuration.get(key, null)
            if (uri.isNullOrBlank()) null else key to uri
        }
        .toMap()
}

internal fun buildFocusBundle(
    configuration: CustomConfiguration,
    bitmapLoader: (String) -> Bitmap?
): Bundle? {
    val focusParam = configuration.focusParam(null) ?: return null
    val focusBundle = Bundle()
    focusBundle.putString(FOCUS_PARAM, focusParam)
    val picsBundle = Bundle()
    for ((key, url) in collectFocusPicUris(configuration)) {
        focusBundle.putString(key, url)
        val bitmap = bitmapLoader(url)
        if (bitmap != null) {
            picsBundle.putParcelable(key, Icon.createWithBitmap(bitmap))
        }
    }
    if (!picsBundle.isEmpty) {
        focusBundle.putBundle(FOCUS_PICS, picsBundle)
    }
    return focusBundle
}

internal fun buildConfiguredFocusBundle(
    context: Context,
    packageName: String,
    largeIcon: Bitmap?,
    notificationIcon: Icon?,
    configuration: CustomConfiguration,
    bitmapLoader: (String) -> Bitmap?,
): Bundle? {
    val icon = MiPushIslandPayloadBuilder.resolveNotificationIcon(
        context,
        packageName,
        notificationIcon,
        largeIcon,
    )
    val configured = buildFocusBundle(configuration, bitmapLoader)
    if (configured != null) {
        val pics = configured.getBundle(FOCUS_PICS) ?: Bundle().also {
            configured.putBundle(FOCUS_PICS, it)
        }
        configured.putString(PIC_ICON, PIC_ICON)
        if (!pics.containsKey(PIC_ICON)) {
            pics.putParcelable(PIC_ICON, icon)
        }
        return configured
    }
    return null
}

}
