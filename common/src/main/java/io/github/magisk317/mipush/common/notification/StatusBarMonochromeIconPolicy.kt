package io.github.magisk317.mipush.common.notification

import android.content.Context
import android.graphics.drawable.Icon
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.magisk317.mipush.common.utils.ImgUtils
import java.util.Collections
import java.util.LinkedHashMap
import io.github.magisk317.mipush.common.ICON_PACK_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ICON_PACK_PREF_COLUMN_BITMAP
import io.github.magisk317.mipush.common.ICON_PACK_PREF_COLUMN_PACKAGE
import io.github.magisk317.mipush.common.ICON_PACK_PREF_COLUMN_USER
import io.github.magisk317.mipush.common.ICON_PACK_PREF_PATH_ICON

/** Status-bar-only monochrome icon helpers.
 *
 * The original notification smallIcon must stay untouched: SystemUI reuses it when binding
 * expanded notification rows, while the status bar has its own getSmallIcon/icon tint path.
 */
object StatusBarMonochromeIconPolicy {
    private const val MAX_CACHE = 48

    private val whiteIconCache: MutableMap<String, Icon> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Icon>(MAX_CACHE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Icon>?): Boolean {
                return size > MAX_CACHE
            }
        },
    )

    @JvmStatic
    fun clearCacheForTest() {
        whiteIconCache.clear()
    }

    /**
     * White-alpha package silhouette for status-bar use. Safe from SystemUI hot path when the
     * posted smallIcon is unusable (RESOURCE resId=0 AUTOGROUP summaries, or MiPush-managed
     * launcher BITMAPs that would tint into white blocks).
     */
    @JvmStatic
    fun whiteIconForPackageOrNull(context: Context, packageName: String): Icon? {
        if (packageName.isBlank()) return null
        return whiteIconForPackage(context, packageName)
    }

    @JvmStatic
    fun iconPackIconForPackageOrNull(context: Context, packageName: String, userId: Int): Icon? {
        if (packageName.isBlank() || userId < 0) return null
        return runCatching {
            val uri = Uri.Builder()
                .scheme("content")
                .authority(ICON_PACK_PREF_AUTHORITY)
                .appendPath(ICON_PACK_PREF_PATH_ICON)
                .appendQueryParameter(ICON_PACK_PREF_COLUMN_PACKAGE, packageName)
                .appendQueryParameter(ICON_PACK_PREF_COLUMN_USER, userId.toString())
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(ICON_PACK_PREF_COLUMN_BITMAP)
                if (index < 0) return@use null
                val bytes = cursor.getBlob(index)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(Icon::createWithBitmap)
            }
        }.getOrNull()
    }

    private fun whiteIconForPackage(context: Context, packageName: String): Icon? {
        whiteIconCache[packageName]?.let { return it }
        val created = runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val raw = ImgUtils.drawableToBitmap(drawable)
            val white = ImgUtils.convertToTransparentAndWhite(raw)
            Icon.createWithBitmap(white)
        }.getOrNull() ?: return null
        whiteIconCache[packageName] = created
        return created
    }

}
