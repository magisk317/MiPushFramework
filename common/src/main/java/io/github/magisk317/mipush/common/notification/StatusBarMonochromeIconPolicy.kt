package io.github.magisk317.mipush.common.notification

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import io.github.magisk317.mipush.common.utils.ImgUtils
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
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
    @JvmStatic
    fun clearCacheForTest() {
        iconPackCache.clear()
        silhouetteCache.clear()
        inFlightIconPackPrefetches.clear()
        inFlightSilhouettePrefetches.clear()
    }

    @JvmStatic
    internal fun setIconPackPrefetchExecutorForTest(executor: Executor?) {
        iconPackPrefetchExecutor = executor ?: defaultIconPackPrefetchExecutor
    }

    @JvmStatic
    internal fun putIconPackEntryForTest(key: String, icon: Icon?, storedAtMs: Long) {
        iconPackCache[key] = IconPackEntry(icon, storedAtMs)
    }

    @JvmStatic
    internal fun putSilhouetteEntryForTest(key: String, icon: Icon?, storedAtMs: Long) {
        silhouetteCache[key] = IconPackEntry(icon, storedAtMs)
    }

    /**
     * White-alpha package silhouette for status-bar use, generated from the app's *shaped*
     * adaptive layer (monochrome when the app ships one, foreground otherwise).
     *
     * This is the second step of the fallback chain icon-pack -> silhouette -> posted glyph.
     * The icon pack only covers apps someone configured; new apps appear constantly, so the
     * silhouette keeps strong monochrome consistent for them too.
     *
     * Same hot-path contract as [iconPackIconForPackageOrNull]: the calling thread only reads
     * the cache, a miss schedules background generation and returns null so SystemUI keeps
     * the original icon (and defers the tint to the system renderer, see HookSystemUI).
     */
    @JvmStatic
    fun whiteIconForPackageOrNull(context: Context, packageName: String): Icon? {
        if (packageName.isBlank()) return null
        val entry = silhouetteCache[packageName]
        return when (decideIconPackCache(entry, System.currentTimeMillis())) {
            IconPackCacheDecision.HIT_FRESH -> entry?.icon
            IconPackCacheDecision.HIT_STALE -> {
                // Serve the stale silhouette immediately; refresh in the background.
                scheduleSilhouettePrefetch(context, packageName)
                entry?.icon
            }
            IconPackCacheDecision.HIT_NEGATIVE_FRESH -> null
            IconPackCacheDecision.MISS -> {
                scheduleSilhouettePrefetch(context, packageName)
                null
            }
        }
    }

    /** Raw package icon fallback for status-bar paths that are not monochrome-only. */
    @JvmStatic
    fun applicationIconForPackageOrNull(context: Context, packageName: String): Icon? {
        if (packageName.isBlank()) return null
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            Icon.createWithBitmap(ImgUtils.drawableToBitmap(drawable))
        }.getOrNull()
    }


    /** Exact Android logo used by the device icon-pack, independent of android's wrench app icon. */
    @JvmStatic
    fun frameworkAndroidLogoIconOrNull(): Icon? = frameworkAndroidLogoIcon

    private val frameworkAndroidLogoIcon: Icon? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            val bytes = Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAAD4AAAA+CAQAAADZyGDPAAABvElEQVR42u3RNRcUMRhGYdzd3X4F7tagDd7h7hXe4FbSoBVa" +
                    "4+4N7lDj7jYXyfpm82XPyazmaec9uTOTCl6oPM/zPHqykZW0d3pmB1axkZ7SbBIB/3ygv7N0fz7wT8Ak06wS74j6Qn9H6S9E" +
                    "vaVS5mETlHjeXVppbBo/0OTdpe+Z5135YspTm0EsZidXeMIrvvOdVzzhCjtYxCBqG9Nf6Cq/rTZPSxZygm+YfOM4C2hhOif7" +
                    "/GwO8RNbPzjEbLu0Bj35hAtyWs67T8s//wcu/Mg+XZk1uLKNqtmkG3IMl47SwDbdmJu4dp2GNul6XCUMV6grpatxhrCcFO6e" +
                    "rYRpoyk9lIAwBYzMlG7FO3TusJFN3EUm79/SQh/fg85aqvx/WpV1yOT9Tl26m/aXn6BibFGRk0jkfUCX9PgldEYnbcYisNpf" +
                    "SP9uvUFJq8EILPddkuMH0VuatFqOwHK/L3HUll/ofaBTbNWJjwgs9z9pHY/PJLPnjKce9ZnACyT2++nx+HFy7Ug0XZ8f5Np3" +
                    "6qn4APKhr4ovIB/mqvhu8mGHil8mHy6q+CPy4b6KvyAfnqn4N/LhWwXP8zzPKyF/ALkcsiqMC92GAAAAAElFTkSuQmCC",
                Base64.DEFAULT,
            )
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(Icon::createWithBitmap)
        }.getOrNull()
    }
    // --- XMSF icon-pack bridge ---------------------------------------------------------------
    // SystemUI calls iconPackIconForPackageOrNull from IconManager.getIconDescriptor on the main
    // thread, inside a ConcurrentHashMap.computeIfAbsent bin lock. A synchronous
    // ContentResolver.query() there blocks on the XMSF IconPackProvider: at boot the provider is
    // cold and its process start exceeds the 5s input-dispatch timeout, ANR-killing SystemUI in
    // a loop until LSPosed enters safe mode (Pixel 17, CP3A.260905.009). Contract: callers on
    // any thread only read the cache here; a miss returns null (SystemUI keeps the original
    // icon) and a background worker performs the query. The next icon rebuild picks the
    // prefetched bitmap up.
    private const val ICON_PACK_CACHE_MAX = 256
    private const val ICON_PACK_POSITIVE_TTL_MS = 10 * 60 * 1000L
    private const val ICON_PACK_NEGATIVE_TTL_MS = 15 * 1000L

    internal enum class IconPackCacheDecision { HIT_FRESH, HIT_STALE, HIT_NEGATIVE_FRESH, MISS }

    internal data class IconPackEntry(val icon: Icon?, val storedAtMs: Long)

    private val iconPackCache: MutableMap<String, IconPackEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, IconPackEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IconPackEntry>?): Boolean {
                return size > ICON_PACK_CACHE_MAX
            }
        },
    )

    private val inFlightIconPackPrefetches: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val defaultIconPackPrefetchExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mipush-iconpack").apply { isDaemon = true }
    }

    @Volatile
    private var iconPackPrefetchExecutor: Executor = defaultIconPackPrefetchExecutor

    internal fun iconPackCacheKey(userId: Int, packageName: String): String = "$userId:$packageName"

    internal fun decideIconPackCache(entry: IconPackEntry?, nowMs: Long): IconPackCacheDecision {
        if (entry == null) return IconPackCacheDecision.MISS
        val ageMs = nowMs - entry.storedAtMs
        return when {
            entry.icon != null && ageMs < ICON_PACK_POSITIVE_TTL_MS -> IconPackCacheDecision.HIT_FRESH
            entry.icon != null -> IconPackCacheDecision.HIT_STALE
            ageMs < ICON_PACK_NEGATIVE_TTL_MS -> IconPackCacheDecision.HIT_NEGATIVE_FRESH
            else -> IconPackCacheDecision.MISS
        }
    }

    @JvmStatic
    fun iconPackIconForPackageOrNull(context: Context, packageName: String, userId: Int): Icon? {
        if (packageName.isBlank() || userId < 0) return null
        val key = iconPackCacheKey(userId, packageName)
        val entry = iconPackCache[key]
        return when (decideIconPackCache(entry, System.currentTimeMillis())) {
            IconPackCacheDecision.HIT_FRESH -> entry?.icon
            IconPackCacheDecision.HIT_STALE -> {
                // Serve the stale bitmap immediately; refresh in the background.
                scheduleIconPackPrefetch(context, key, packageName, userId)
                entry?.icon
            }
            IconPackCacheDecision.HIT_NEGATIVE_FRESH -> null
            IconPackCacheDecision.MISS -> {
                scheduleIconPackPrefetch(context, key, packageName, userId)
                null
            }
        }
    }

    private fun scheduleIconPackPrefetch(context: Context, key: String, packageName: String, userId: Int) {
        if (!inFlightIconPackPrefetches.add(key)) return
        val appContext = context.applicationContext ?: context
        iconPackPrefetchExecutor.execute {
            try {
                val icon = queryIconPackProvider(appContext, packageName, userId)
                iconPackCache[key] = IconPackEntry(icon, System.currentTimeMillis())
            } catch (_: Throwable) {
                // Provider failures are expected while XMSF is cold at boot: retain any
                // previously resolved bitmap and back off through the entry timestamp.
                val retained = iconPackCache[key]?.icon
                iconPackCache[key] = IconPackEntry(retained, System.currentTimeMillis())
            } finally {
                inFlightIconPackPrefetches.remove(key)
            }
        }
    }

    /**
     * Background worker only. Blocks while AMS cold-starts the XMSF provider process — exactly
     * the wait that must never run on SystemUI's main thread (see iconPackIconForPackageOrNull).
     */
    private fun queryIconPackProvider(context: Context, packageName: String, userId: Int): Icon? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(ICON_PACK_PREF_AUTHORITY)
            .appendPath(ICON_PACK_PREF_PATH_ICON)
            .appendQueryParameter(ICON_PACK_PREF_COLUMN_PACKAGE, packageName)
            .appendQueryParameter(ICON_PACK_PREF_COLUMN_USER, userId.toString())
            .build()
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(ICON_PACK_PREF_COLUMN_BITMAP)
            if (index < 0) return@use null
            val bytes = cursor.getBlob(index)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(Icon::createWithBitmap)
        }
    }

    // --- White silhouette cache ---------------------------------------------------------------
    // Same background-generation contract as the icon pack above: SystemUI hot paths only read
    // the cache, a miss returns null and a single background worker builds the silhouette.
    private const val SILHOUETTE_CACHE_MAX = 128

    private val silhouetteCache: MutableMap<String, IconPackEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, IconPackEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IconPackEntry>?): Boolean {
                return size > SILHOUETTE_CACHE_MAX
            }
        },
    )

    private val inFlightSilhouettePrefetches: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private fun scheduleSilhouettePrefetch(context: Context, packageName: String) {
        if (!inFlightSilhouettePrefetches.add(packageName)) return
        val appContext = context.applicationContext ?: context
        iconPackPrefetchExecutor.execute {
            var reason = "ok"
            try {
                val icon = createWhiteSilhouetteIcon(appContext, packageName)
                silhouetteCache[packageName] = IconPackEntry(icon, System.currentTimeMillis())
                if (icon == null) reason = "null_icon"
            } catch (t: Throwable) {
                // Package manager / bitmap failures are expected (uninstalled app, work profile,
                // OEM icon quirk). Retain any previously generated silhouette and back off
                // through the entry timestamp.
                val retained = silhouetteCache[packageName]?.icon
                silhouetteCache[packageName] = IconPackEntry(retained, System.currentTimeMillis())
                reason = "throw=${t.javaClass.simpleName}:${t.message}"
            } finally {
                inFlightSilhouettePrefetches.remove(packageName)
                silhouetteDiag(packageName, reason)
            }
        }
    }

    private fun silhouetteDiag(packageName: String, reason: String) {
        runCatching { android.util.Log.i("StatusBarSilhouette", "pkg=$packageName reason=$reason") }
    }

    /**
     * Background worker only: builds a white-alpha silhouette from the app's shaped adaptive
     * layer, mirroring the XMSF `createMonochromeSilhouette` producer.
     *
     * Legacy (non-adaptive) launcher icons ship as flattened bitmaps. Monochrome mode tints the
     * posted glyph regardless, so keeping such an opaque bitmap produces a solid white block;
     * converting it to white-alpha keeps the logo silhouette instead, which is strictly better
     * than a colorless block even when the bitmap is fully opaque.
     */
    private fun createWhiteSilhouetteIcon(context: Context, packageName: String): Icon? {
        val appIcon = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: run {
            silhouetteDiag(packageName, "no_app_icon")
            return null
        }
        val shaped: Drawable = when (appIcon) {
            is AdaptiveIconDrawable ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appIcon.monochrome ?: appIcon.foreground
                } else {
                    appIcon.foreground
                }
            is BitmapDrawable -> appIcon
            else -> run {
                silhouetteDiag(packageName, "not_adaptive=${appIcon.javaClass.name}")
                return null
            }
        } ?: run {
            silhouetteDiag(packageName, "no_shaped_layer")
            return null
        }
        if (shaped.intrinsicWidth <= 0 || shaped.intrinsicHeight <= 0) {
            silhouetteDiag(packageName, "no_intrinsic")
            return null
        }
        val white = runCatching {
            ImgUtils.convertToTransparentAndWhite(ImgUtils.drawableToBitmap(shaped))
        }.getOrNull() ?: run {
            silhouetteDiag(packageName, "convert_failed")
            return null
        }
        silhouetteDiag(packageName, "bitmap=${white.width}x${white.height}")
        return runCatching { Icon.createWithBitmap(white) }.getOrNull()
    }

}
