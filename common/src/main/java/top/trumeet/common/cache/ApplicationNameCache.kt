package top.trumeet.common.cache

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.collection.LruCache

/**
 * @author zts
 */
import com.magisk317.utils.Singleton as SingletonUtils

// 单例对象，原先通过 Hilt/`javax.inject` 注入，只保留手动单例逻辑
object ApplicationNameCache {
    init {
        try {
            SingletonUtils.reset(this)
        } catch (_: Throwable) {}
    }

    private val cacheInstance = LruCache<String, CharSequence>(100)

    fun getAppName(ctx: Context, pkg: String): CharSequence? {
        return object : AbstractCacheAspect<CharSequence>(cacheInstance) {
            override fun gen(): CharSequence {
                return getAppNameInternal()
            }

            private fun getAppNameInternal(): CharSequence {
                val pm = ctx.packageManager
                return try {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm)
                } catch (e: Exception) {
                    when (e) {
                        is PackageManager.NameNotFoundException,
                        is Resources.NotFoundException -> pkg
                        else -> pkg
                    }
                }
            }
        }.get(pkg)
    }
}
