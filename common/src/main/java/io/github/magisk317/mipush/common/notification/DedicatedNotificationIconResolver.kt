package io.github.magisk317.mipush.common.notification

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.UserHandle
import io.github.magisk317.mipush.common.utils.ImgUtils
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Resolves app-owned resources that are explicitly named for notification/status-bar use.
 *
 * The hot path only reads this cache. Resource discovery runs from the silhouette worker so a
 * SystemUI descriptor bind never performs package/resource work synchronously.
 */
internal object DedicatedNotificationIconResolver {
    private const val MAX_DIMENSION = 2048
    private const val MAX_PIXELS = 2048L * 2048L
    private const val POSITIVE_TTL_MS = 10 * 60 * 1000L
    private const val NEGATIVE_TTL_MS = 15 * 1000L

    private val candidateNames = DedicatedNotificationIconPolicy.candidateNames

    internal data class Resolved(
        val packageName: String,
        val resourceName: String,
        val userId: Int,
        val versionCode: Long,
        val bitmap: Bitmap,
    )

    internal sealed interface CachedLookup {
        data class Hit(val value: Resolved) : CachedLookup
        data object Absent : CachedLookup
        data object NotReady : CachedLookup
    }

    private data class CacheEntry(
        val value: Resolved?,
        val storedAtMs: Long,
    )

    private const val CACHE_MAX = 128
    private val latest: MutableMap<String, CacheEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
                size > CACHE_MAX
        },
    )
    private val latestKeyByScope: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > CACHE_MAX
        },
    )

    internal fun clearForTest() {
        latest.clear()
        latestKeyByScope.clear()
    }

    internal fun lookupCached(packageName: String, userId: Int): CachedLookup {
        if (packageName.isBlank() || userId < 0) return CachedLookup.Absent
        val scopeKey = "$packageName#$userId"
        val cacheKey = latestKeyByScope[scopeKey] ?: return CachedLookup.NotReady
        val entry = latest[cacheKey] ?: return CachedLookup.NotReady
        val ageMs = System.currentTimeMillis() - entry.storedAtMs
        return when {
            entry.value != null && ageMs < POSITIVE_TTL_MS -> CachedLookup.Hit(entry.value)
            entry.value == null && ageMs < NEGATIVE_TTL_MS -> CachedLookup.Absent
            else -> CachedLookup.NotReady
        }
    }

    /**
     * Performs package/resource discovery off the SystemUI hot path.
     *
     * The cache key includes package, user and version code. A negative result is retained briefly
     * so packages without a dedicated resource do not trigger a resource scan on every bind.
     */
    internal fun resolveBlocking(context: Context, packageName: String, userId: Int): Resolved? {
        if (packageName.isBlank() || userId < 0) return null
        val appContext = context.applicationContext ?: context
        val userContext = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val method = Context::class.java.getMethod(
                    "createContextAsUser",
                    UserHandle::class.java,
                    Int::class.javaPrimitiveType,
                )
                val userHandle = UserHandle::class.java.getMethod(
                    "of",
                    Int::class.javaPrimitiveType,
                ).invoke(null, userId)
                method.invoke(appContext, userHandle, 0) as? Context
            } else {
                appContext
            }
        }.getOrElse { appContext } ?: appContext
        val packageContext = runCatching { userContext.createPackageContext(packageName, 0) }.getOrNull()
        val versionCode = runCatching {
            val info = packageContext?.packageManager?.getPackageInfo(packageName, 0)
                ?: return@runCatching 0L
            info.longVersionCode
        }.getOrDefault(0L)
        val resolved = packageContext?.let { findCandidate(it, packageName, userId, versionCode) }
        val cacheKey = "$packageName#$userId#$versionCode"
        latest[cacheKey] = CacheEntry(resolved, System.currentTimeMillis())
        latestKeyByScope["$packageName#$userId"] = cacheKey
        return resolved
    }

    private fun findCandidate(
        packageContext: Context,
        packageName: String,
        userId: Int,
        versionCode: Long,
    ): Resolved? {
        val resources = packageContext.resources
        for (name in candidateNames) {
            for (type in arrayOf("drawable", "mipmap")) {
                val resId = runCatching { resources.getIdentifier(name, type, packageName) }.getOrDefault(0)
                if (resId == 0) continue
                val owned = runCatching { resources.getResourcePackageName(resId) == packageName }.getOrDefault(false)
                if (!owned) continue
                val drawable = runCatching { packageContext.getDrawable(resId) }.getOrNull() ?: continue
                val bitmap = runCatching { ImgUtils.drawableToBitmap(drawable) }.getOrNull() ?: continue
                if (!isCandidateBitmap(bitmap)) continue
                return Resolved(packageName, "$type/$name", userId, versionCode, bitmap)
            }
        }
        return null
    }

    private fun isCandidateBitmap(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) return false
        if (width.toLong() * height > MAX_PIXELS) return false
        val pixels = runCatching {
            IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
        }.getOrNull() ?: return false
        return DedicatedNotificationIconPolicy.isValidStatusGlyph(width, height, pixels)
    }
}
