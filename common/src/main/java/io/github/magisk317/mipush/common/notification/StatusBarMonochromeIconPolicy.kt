package io.github.magisk317.mipush.common.notification

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import co.touchlab.kermit.Logger
import androidx.core.graphics.createBitmap
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
    private const val SILHOUETTE_LOG_TAG = "MiPushSilhouette"
    private const val ICON_PACK_LOG_TAG = "MiPushIconPack"
    private const val FULLY_OPAQUE_ALPHA = 0xFF

    private val silhouetteLogger = Logger.withTag(SILHOUETTE_LOG_TAG)
    private val iconPackLogger = Logger.withTag(ICON_PACK_LOG_TAG)

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

    /**
     * Guarantees the glyph cannot tint into a solid white block on the status bar.
     *
     * Icon-pack assets are authored per app and usually ship transparent, but a mis-authored
     * opaque bitmap pushed through the pack would tint into a full white square. The check is
     * the outer pixel ring: a shape asset always has transparent margins, an opaque backdrop
     * bleeds to every edge. Opaque glyphs are re-synthesized as white-alpha silhouettes, so the
     * module's product promise ("icon pack or native rendering, never a white block") holds
     * regardless of pack authoring quality.
     *
     * @return the original icon when safe, a white-alpha silhouette when opaque, null when the
     *   icon cannot be inspected (callers keep the original in that case; the pack bitmap is
     *   then trusted as-authored).
     */
    @JvmStatic
    fun statusSafeGlyphOrNull(context: Context?, icon: Icon): Icon? {
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull()
        if (drawable == null) {
            silhouetteLogger.w { "statusSafe loadDrawable failed; trusting pack bitmap" }
            return null
        }
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        if (bitmap == null) {
            silhouetteLogger.w { "statusSafe drawable not bitmap; trusting pack bitmap" }
            return null
        }
        val inspected = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        if (inspected == null) {
            silhouetteLogger.w { "statusSafe hardware copy failed; trusting pack bitmap" }
            return null
        }
        return if (hasOpaqueBacking(inspected)) {
            silhouetteLogger.i { "statusSafe opaque backing detected; re-synthesizing silhouette" }
            whiteGlyphFrom(inspected) ?: icon
        } else {
            icon
        }
    }

    private fun hasOpaqueBacking(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        fun opaque(x: Int, y: Int): Boolean = Color.alpha(pixels[y * width + x]) == FULLY_OPAQUE_ALPHA
        for (x in 0 until width) {
            if (!opaque(x, 0) || !opaque(x, height - 1)) return false
        }
        for (y in 0 until height) {
            if (!opaque(0, y) || !opaque(width - 1, y)) return false
        }
        // Every edge pixel opaque => the backdrop reaches the border => tint would fill the slot.
        return true
    }

    private fun whiteGlyphFrom(bitmap: Bitmap): Icon? = runCatching {
        val white = ImgUtils.convertToTransparentAndWhite(bitmap)
        Icon.createWithBitmap(normalizeSilhouetteBounds(white))
    }.getOrNull()

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
        val decision = decideIconPackCache(entry, System.currentTimeMillis())
        val resolved = when (decision) {
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
        iconPackLogger.d {
            "lookup key=$key decision=$decision age=${entry?.let { System.currentTimeMillis() - it.storedAtMs }}" +
            " inFlight=${inFlightIconPackPrefetches.contains(key)} hit=${resolved != null}"
        }
        return resolved
    }

    private fun scheduleIconPackPrefetch(context: Context, key: String, packageName: String, userId: Int) {
        if (!inFlightIconPackPrefetches.add(key)) {
            // A prefetch that never returns pins its key here forever, so every later lookup
            // silently keeps missing the pack. Log it: this is the "never resolves" symptom.
            iconPackLogger.w { "prefetch skipped: key=$key already in flight (stuck worker?)" }
            return
        }
        val appContext = context.applicationContext ?: context
        iconPackPrefetchExecutor.execute {
            val startedAtMs = System.currentTimeMillis()
            try {
                val icon = queryIconPackProvider(appContext, packageName, userId)
                iconPackCache[key] = IconPackEntry(icon, System.currentTimeMillis())
                iconPackLogger.i { "prefetch done key=$key hit=${icon != null} took=${System.currentTimeMillis() - startedAtMs}ms" }
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                // Provider failures are expected while XMSF is cold at boot: retain any
                // previously resolved bitmap and back off through the entry timestamp.
                val retained = iconPackCache[key]?.icon
                iconPackCache[key] = IconPackEntry(retained, System.currentTimeMillis())
                iconPackLogger.w {
                    "prefetch failed key=$key retained=${retained != null} " +
                    "took=${System.currentTimeMillis() - startedAtMs}ms: ${t.message}"
                }
            } finally {
                inFlightIconPackPrefetches.remove(key)
            }
        }
        iconPackLogger.d { "prefetch scheduled key=$key" }
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
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor == null) {
            iconPackLogger.w { "provider returned no cursor pkg=$packageName user=$userId" }
            return null
        }
        return cursor.use {
            if (!it.moveToFirst()) {
                iconPackLogger.d { "provider empty cursor pkg=$packageName user=$userId (not configured)" }
                return@use null
            }
            val index = it.getColumnIndex(ICON_PACK_PREF_COLUMN_BITMAP)
            if (index < 0) {
                iconPackLogger.w { "provider cursor has no bitmap column pkg=$packageName" }
                return@use null
            }
            val bytes = it.getBlob(index)
            if (bytes == null) {
                iconPackLogger.w { "provider returned null blob pkg=$packageName user=$userId" }
                return@use null
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            iconPackLogger.d { "provider row pkg=$packageName user=$userId bytes=${bytes.size} decoded=${bitmap != null}" }
            bitmap?.let(Icon::createWithBitmap)
        }
    }

    // --- White silhouette cache ---------------------------------------------------------------
    // Same background-generation contract as the icon pack above: SystemUI hot paths only read
    // the cache, a miss returns null and a single background worker builds the silhouette.
    private const val SILHOUETTE_CACHE_MAX = 128

    /** Side margin (fraction of the glyph longer side) kept when re-centering a cropped
     * silhouette, so the glyph does not touch the slot edge. */
    private const val SILHOUETTE_MARGIN_RATIO = 0.06f

    private val silhouetteCache: MutableMap<String, IconPackEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, IconPackEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IconPackEntry>?): Boolean {
                return size > SILHOUETTE_CACHE_MAX
            }
        },
    )

    private val inFlightSilhouettePrefetches: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // Background silhouette prefetch must survive any single-app failure (OEM icon quirks,
    // work-profile PackageManager, OOM from oversized bitmaps) without killing the executor.
    @Suppress("TooGenericExceptionCaught")
    private fun scheduleSilhouettePrefetch(context: Context, packageName: String) {
        if (!inFlightSilhouettePrefetches.add(packageName)) return
        val appContext = context.applicationContext ?: context
        iconPackPrefetchExecutor.execute {
            try {
                val icon = createWhiteSilhouetteIcon(appContext, packageName)
                silhouetteCache[packageName] = IconPackEntry(icon, System.currentTimeMillis())
            } catch (_: Throwable) {
                // Package manager / bitmap failures are expected (uninstalled app, work profile,
                // OEM icon quirk). Retain any previously generated silhouette and back off
                // through the entry timestamp.
                val retained = silhouetteCache[packageName]?.icon
                silhouetteCache[packageName] = IconPackEntry(retained, System.currentTimeMillis())
            } finally {
                inFlightSilhouettePrefetches.remove(packageName)
            }
        }
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
        }.getOrNull() ?: return null
        // The layer actually consumed decides how spec-compliant the app is: a shipped monochrome
        // layer (Android 13 themed icons), an adaptive icon without one, or a legacy flattened
        // bitmap. The log line below records which one every package takes.
        val shaped: Drawable
        val layer: String
        when (appIcon) {
            is AdaptiveIconDrawable -> {
                val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appIcon.monochrome
                } else {
                    null
                }
                shaped = monochrome ?: appIcon.foreground
                layer = if (monochrome != null) "monochrome" else "foreground"
            }
            is BitmapDrawable -> {
                shaped = appIcon
                layer = "legacy"
            }
            else -> return null
        }
        if (shaped.intrinsicWidth <= 0 || shaped.intrinsicHeight <= 0) {
            return null
        }
        val white = runCatching {
            ImgUtils.convertToTransparentAndWhite(ImgUtils.drawableToBitmap(shaped))
        }.getOrNull() ?: return null
        val normalized = normalizeSilhouetteBounds(white)
        silhouetteLogger.i {
            "silhouette pkg=$packageName layer=$layer " +
            "intrinsic=${shaped.intrinsicWidth}x${shaped.intrinsicHeight} " +
            "src=${white.width}x${white.height} out=${normalized.width}x${normalized.height} " +
            "changed=${normalized !== white}"
        }
        return runCatching { Icon.createWithBitmap(normalized) }.getOrNull()
    }

    /**
     * Crops transparent padding around a generated silhouette and re-centers the glyph on a
     * square canvas.
     *
     * Adaptive foreground/monochrome layers draw their artwork centered inside the full canvas
     * (the Play Store triangle occupies roughly half of it), and the white-alpha rework only
     * masks a circle without cropping. Rendering such a bitmap in the status-bar slot scales
     * the whole canvas down, leaving the glyph at the artwork share of the slot. Cropping to
     * the alpha bounds with a small margin makes the glyph fill the slot like icon-pack
     * bitmaps do.
     */
    private fun normalizeSilhouetteBounds(
        bitmap: Bitmap,
        marginRatio: Float = SILHOUETTE_MARGIN_RATIO,
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (Color.alpha(pixels[row + x]) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) {
            silhouetteLogger.d { "normalize in=${width}x$height empty-alpha keep original" }
            return bitmap
        }
        val contentWidth = maxX - minX + 1
        val contentHeight = maxY - minY + 1
        silhouetteLogger.d {
            "normalize in=${width}x$height bbox=[$minX,$minY,$maxX,$maxY] " +
            "content=${contentWidth}x$contentHeight"
        }
        if (contentWidth >= width - 1 && contentHeight >= height - 1) {
            silhouetteLogger.d { "normalize full-bleed keep original" }
            return bitmap
        }
        val contentMax = maxOf(contentWidth, contentHeight)
        val margin = (contentMax * marginRatio).toInt().coerceAtLeast(1)
        val side = contentMax + margin * 2
        val normalized = createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(normalized)
        val left = (side - contentWidth) / 2
        val top = (side - contentHeight) / 2
        canvas.drawBitmap(
            bitmap,
            Rect(minX, minY, maxX + 1, maxY + 1),
            Rect(left, top, left + contentWidth, top + contentHeight),
            null,
        )
        return normalized
    }

}
