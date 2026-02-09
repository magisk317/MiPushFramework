package top.trumeet.common.cache

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.collection.LruCache

/**
 * @author zts
 */
import javax.inject.Inject
import javax.inject.Singleton
import com.magisk317.utils.Singleton as SingletonUtils

@Singleton
class ApplicationNameCache @Inject constructor() {
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

    companion object {
        @Volatile
        private var instance: ApplicationNameCache? = null

        @JvmStatic
        fun getInstance(): ApplicationNameCache {
            return instance ?: synchronized(this) {
                instance ?: ApplicationNameCache().also { instance = it }
            }
        }
    }
}
