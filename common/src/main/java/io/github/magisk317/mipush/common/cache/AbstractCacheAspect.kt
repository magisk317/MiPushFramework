package io.github.magisk317.mipush.common.cache

import androidx.collection.LruCache

/**
 * @author zts
 */
internal abstract class AbstractCacheAspect<T : Any>(private val cache: LruCache<String, T>) {
    fun get(cacheKey: String): T? {
        var cached = cache[cacheKey]
        if (cached == null) {
            cached = gen()
            if (cached != null) {
                cache.put(cacheKey, cached)
            }
        }
        return cached
    }

    /**
     * @return from DataSource
     */
    abstract fun gen(): T?
}
