package io.github.magisk317.mipush.common.cache

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.collection.LruCache

/**
 * @author zts
 */

// 无构造依赖的纯函数式缓存：状态仅为内部 LruCache，无需经 Koin 注入，
// 故刻意保留为 Kotlin object 手动单例（原先曾走 DI，现统一 Koin 后无需注册）。
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
