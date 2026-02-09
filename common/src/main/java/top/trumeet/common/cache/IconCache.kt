package top.trumeet.common.cache

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.core.graphics.drawable.IconCompat
import top.trumeet.common.utils.ImgUtils

/**
 * Author: TimothyZhang023
 * Icon Cache
 */
import javax.inject.Inject
import javax.inject.Singleton
import com.magisk317.utils.Singleton as SingletonUtils

@Singleton
class IconCache @Inject constructor() {
    init {
        try {
            SingletonUtils.reset(this)
        } catch (_: Throwable) {}
    }

    private val bitmapLruCache = LruCache<String, Bitmap>(100)
    private val mIconMemoryCaches = LruCache<String, IconCompat>(100)
    private val appColorCache = LruCache<String, Int>(100)
    private val bitmapCache = LruCache<String, Bitmap>(100)

    fun getRawIconBitmapWithoutLoader(ctx: Context, pkg: String): Bitmap? {
        return bitmapLruCache["raw_$pkg"]
    }

    fun getBitmap(ctx: Context, key: String?, callback: Converter<String, Bitmap>): Bitmap? {
        if (key == null) return null
        return object : AbstractCacheAspect<Bitmap>(bitmapCache) {
            override fun gen(): Bitmap? {
                return callback.convert(ctx, key)
            }
        }.get(key)
    }

    fun getRawIconBitmap(ctx: Context, pkg: String): Bitmap? {
        return object : AbstractCacheAspect<Bitmap>(bitmapLruCache) {
            override fun gen(): Bitmap? {
                val pm = ctx.packageManager
                var res: Bitmap? = null
                try {
                    val icon = pm.getApplicationInfo(pkg, 0).loadIcon(pm)
                    res = ImgUtils.drawableToBitmap(icon)
                } catch (ignored: Throwable) {
                }

                if (res == null) {
                    try {
                        val icon = ctx.packageManager.getApplicationIcon(pkg)
                        res = ImgUtils.drawableToBitmap(icon)
                    } catch (ignored: Throwable) {
                    }
                }

                return res
            }
        }.get("raw_$pkg")
    }

    fun getIconCache(ctx: Context, pkg: String, callback: Converter<Bitmap, IconCompat>): IconCompat? {
        return object : AbstractCacheAspect<IconCompat>(mIconMemoryCaches) {
            override fun gen(): IconCompat? {
                val rawIconBitmap = getRawIconBitmap(ctx, pkg) ?: return null
                val whiteIconBitmap = WhiteIconProcess().convert(ctx, rawIconBitmap)
                return callback.convert(ctx, whiteIconBitmap)
            }
        }.get("white_$pkg")
    }

    fun getAppColor(ctx: Context, pkg: String, callback: Converter<Bitmap, Int>): Int {
        return object : AbstractCacheAspect<Int>(appColorCache) {
            override fun gen(): Int? {
                val rawIconBitmap = getRawIconBitmap(ctx, pkg) ?: return -1
                return callback.convert(ctx, rawIconBitmap)
            }
        }.get(pkg) ?: -1
    }

    class WhiteIconProcess : Converter<Bitmap, Bitmap> {
        override fun convert(ctx: Context, b: Bitmap): Bitmap {
            val dip2px = dip2px(ctx, 64f)
            return ImgUtils.scaleImage(ImgUtils.convertToTransparentAndWhite(b), dip2px, dip2px) ?: b
        }
    }

    interface Converter<T, R> {
        fun convert(ctx: Context, b: T): R
    }

    companion object {
        @Volatile
        private var instance: IconCache? = null

        @JvmStatic
        fun getInstance(): IconCache {
            return instance ?: synchronized(this) {
                instance ?: IconCache().also { instance = it }
            }
        }

        @JvmStatic
        fun dip2px(context: Context, dipValue: Float): Int {
            val scale = context.resources.displayMetrics.density
            return (dipValue * scale + 0.5f).toInt()
        }
    }
}
