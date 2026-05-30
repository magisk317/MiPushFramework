package io.github.magisk317.mipush.common.cache

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.collection.LruCache

/**
 * @author zts
 */

// 单例对象，原先通过 DI 注入，只保留手动单例逻辑
object ApplicationNameCache {
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
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg
                } catch (_: Resources.NotFoundException) {
                    pkg
                }
            }
        }.get(pkg)
    }
}
