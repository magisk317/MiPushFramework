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
import io.github.magisk317.xposed.logging.XLog
import androidx.core.graphics.createBitmap
import android.os.Build
import android.util.Base64
import io.github.magisk317.mipush.common.utils.ImgUtils
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import io.github.magisk317.mipush.common.ICON_PACK_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ICON_PACK_PREF_PATH_SNAPSHOT
import io.github.magisk317.mipush.common.notification.iconpack.IconPackSnapshotCodec

/** Status-bar-only monochrome icon helpers.
 *
 * The original notification smallIcon must stay untouched: SystemUI reuses it when binding
 * expanded notification rows, while the status bar has its own getSmallIcon/icon tint path.
 */
object StatusBarMonochromeIconPolicy {
    private const val SILHOUETTE_LOG_TAG = "MiPushSilhouette"
    private const val ICON_PACK_LOG_TAG = "MiPushIconPack"
    private const val FULLY_OPAQUE_ALPHA = 0xFF

    private val silhouetteLogger = XLog.tagged(SILHOUETTE_LOG_TAG)
    private val iconPackLogger = XLog.tagged(ICON_PACK_LOG_TAG)

    @JvmStatic
    fun clearCacheForTest() {
        silhouetteCache.clear()
        shadeLayerCache.clear()
        decodedIconCache.clear()
        snapshotIndex = null
        snapshotFetchedAtMs = 0L
        snapshotInFlight = false
        lastSnapshotAttemptMs = 0L
        inFlightSilhouettePrefetches.clear()
    }

    /** Test seam: publish an index directly, so snapshot semantics are testable without a provider. */
    @JvmStatic
    internal fun putSnapshotIndexForTest(index: Map<String, ByteArray>, fetchedAtMs: Long = System.currentTimeMillis()) {
        snapshotIndex = index
        snapshotFetchedAtMs = fetchedAtMs
        decodedIconCache.clear()
    }

    @JvmStatic
    internal fun setIconPackPrefetchExecutorForTest(executor: Executor?) {
        iconPackPrefetchExecutor = executor ?: defaultIconPackPrefetchExecutor
    }

    /** Silhouette work runs on its own worker, so tests capture it through its own seam. */
    @JvmStatic
    internal fun setSilhouettePrefetchExecutorForTest(executor: Executor?) {
        silhouettePrefetchExecutor = executor ?: defaultSilhouettePrefetchExecutor
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

    /**
     * The app's own themed (monochrome) icon layer, rendered without any threshold rework.
     *
     * [whiteIconForPackageOrNull] runs `convertToTransparentAndWhite` because it is fed colorful
     * launcher bitmaps. An app that ships a monochrome layer already authored that artwork for
     * single-color rendering, and thresholding it erases the glyph -- that is the blank
     * notification-center header seen on Alipay. Shade callers take this first and fall back to
     * the manufactured silhouette only when the app ships no such layer.
     *
     * Cache-only contract, same as [whiteIconForPackageOrNull]: a miss schedules background work
     * and returns null so the caller keeps whatever SystemUI already rendered.
     */
    @JvmStatic
    fun shadeIconForPackageOrNull(context: Context, packageName: String): Icon? {
        if (packageName.isBlank()) return null
        val entry = shadeLayerCache[packageName]
        return when (decideIconPackCache(entry, System.currentTimeMillis())) {
            IconPackCacheDecision.HIT_FRESH -> entry?.icon
            IconPackCacheDecision.HIT_STALE -> {
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
     *   icon cannot be inspected or when even the re-synthesized glyph would still be a block
     *   (callers keep the original in that case; the pack bitmap is then trusted as-authored).
     */
    @JvmStatic
    fun statusSafeGlyphOrNull(context: Context?, icon: Icon): Icon? {
        val drawable = runCatching { icon.loadDrawable(context) }.getOrNull()
        if (drawable == null) {
            silhouetteLogger.w("statusSafe loadDrawable failed; trusting pack bitmap")
            return null
        }
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        if (bitmap == null) {
            silhouetteLogger.w("statusSafe drawable not bitmap; trusting pack bitmap")
            return null
        }
        val inspected = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        if (inspected == null) {
            silhouetteLogger.w("statusSafe hardware copy failed; trusting pack bitmap")
            return null
        }
        return if (hasOpaqueBacking(inspected)) {
            silhouetteLogger.i("statusSafe opaque backing detected; re-synthesizing silhouette")
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
        return hasOpaqueBackingPixels(width, height, pixels)
    }

    internal fun hasOpaqueBackingPixels(width: Int, height: Int, pixels: IntArray): Boolean {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return false
        fun opaque(x: Int, y: Int): Boolean = (pixels[y * width + x] ushr 24) == FULLY_OPAQUE_ALPHA
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
        silhouetteIconOrNull(normalizeSilhouetteBounds(ImgUtils.convertToTransparentAndWhite(bitmap)), "pack")
    }.getOrNull()

    /**
     * Wrap a manufactured white-alpha glyph for the status bar, refusing it when the opaque region
     * reaches every edge.
     *
     * A bitmap [Icon] is drawn without an adaptive mask, so unlike an app's shaped layers the
     * platform never clips it to the icon slot's outline. [normalizeSilhouetteBounds] returns a
     * full-bleed bitmap unchanged, which means a monochrome layer filled edge to edge (or an
     * ANIP-style pass-through of one) would be cached as a solid white block -- the defect this
     * module exists to prevent. Rejected glyphs fall through to the next link of the chain.
     */
    private fun silhouetteIconOrNull(bitmap: Bitmap, from: String): Icon? {
        if (hasOpaqueBacking(bitmap)) {
            silhouetteLogger.w("silhouette rejected from=$from opaque-backing ${bitmap.width}x${bitmap.height}")
            return null
        }
        return runCatching { Icon.createWithBitmap(bitmap) }.getOrNull()
    }

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
    // --- XMSF icon-pack snapshot --------------------------------------------------------------
    // SystemUI calls iconPackIconForPackageOrNull from IconManager.getIconDescriptor on the main
    // thread, inside a ConcurrentHashMap.computeIfAbsent bin lock. A synchronous binder call there
    // blocks while AMS cold-starts the XMSF provider: at boot that exceeded the 5s input-dispatch
    // timeout and ANR-killed SystemUI in a loop (Pixel 17, CP3A.260905.009). Callers on any thread
    // only read the in-memory index; a background worker fetches the whole snapshot file.
    //
    // Granularity is the point. A per-package query could only answer "no icon", so the runtime's
    // cold moments -- the one-time ANIP release load failing -- were indistinguishable from "this
    // package is not configured", and the answer got cached, making the same notification flip
    // between pack art and silhouette. Here, "no index yet" is never cached: it is retried on the
    // next lookup, while "package absent from the index" is authoritative until the index is swapped.
    // Shared entry shape for the package-keyed caches that still resolve per package with a TTL:
    // the manufactured silhouette and the app's themed monochrome layer. The icon-pack tier no longer
    // needs one because the snapshot is a whole index: absence is authoritative for its generation.
    internal enum class IconPackCacheDecision { HIT_FRESH, HIT_STALE, HIT_NEGATIVE_FRESH, MISS }

    internal data class IconPackEntry(val icon: Icon?, val storedAtMs: Long)

    private const val SILHOUETTE_POSITIVE_TTL_MS = 10 * 60 * 1000L
    private const val SILHOUETTE_NEGATIVE_TTL_MS = 15 * 1000L

    internal fun decideIconPackCache(entry: IconPackEntry?, nowMs: Long): IconPackCacheDecision {
        if (entry == null) return IconPackCacheDecision.MISS
        val ageMs = nowMs - entry.storedAtMs
        return when {
            entry.icon != null && ageMs < SILHOUETTE_POSITIVE_TTL_MS -> IconPackCacheDecision.HIT_FRESH
            entry.icon != null -> IconPackCacheDecision.HIT_STALE
            ageMs < SILHOUETTE_NEGATIVE_TTL_MS -> IconPackCacheDecision.HIT_NEGATIVE_FRESH
            else -> IconPackCacheDecision.MISS
        }
    }

    private val defaultIconPackPrefetchExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mipush-iconpack").apply { isDaemon = true }
    }

    // Silhouette work must never queue behind an icon-pack snapshot read: the notification center
    // asks for silhouettes on every bound row, and a shared worker would delay the status bar's
    // pack lookups long enough to make it fall back to a silhouette.
    private val defaultSilhouettePrefetchExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mipush-silhouette").apply { isDaemon = true }
    }

    @Volatile
    private var iconPackPrefetchExecutor: Executor = defaultIconPackPrefetchExecutor

    @Volatile
    private var silhouettePrefetchExecutor: Executor = defaultSilhouettePrefetchExecutor

    private const val SNAPSHOT_REFRESH_MS = 10 * 60 * 1000L
    private const val SNAPSHOT_RETRY_BACKOFF_MS = 30 * 1000L
    private const val SNAPSHOT_DECODED_CACHE_MAX = 256

    @Volatile
    private var snapshotIndex: Map<String, ByteArray>? = null

    @Volatile
    private var snapshotFetchedAtMs = 0L

    @Volatile
    private var snapshotInFlight = false

    private var lastSnapshotAttemptMs = 0L

    private val snapshotFetchLock = Any()

    private val decodedIconCache: MutableMap<String, Icon> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Icon>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Icon>?): Boolean {
                return size > SNAPSHOT_DECODED_CACHE_MAX
            }
        },
    )

    /**
     * Icon-pack glyph for [packageName], or null when the pack does not cover it or no index has
     * been fetched yet. Never blocks: a missing index schedules a background fetch and reports null,
     * and the caller keeps SystemUI's native rendering until the next icon rebuild.
     */
    @JvmStatic
    fun iconPackIconForPackageOrNull(context: Context, packageName: String): Icon? {
        if (packageName.isBlank()) return null
        if (snapshotIndex == null || isSnapshotStale()) scheduleSnapshotFetch(context)
        val index = snapshotIndex ?: run {
            iconPackLogger.d("lookup pkg=$packageName decision=NOT_READY")
            return null
        }
        decodedIconCache[packageName]?.let { return it }
        val png = index[packageName]
        if (png == null) {
            iconPackLogger.d("lookup pkg=$packageName decision=MISS entries=${index.size}")
            return null
        }
        val bitmap = runCatching { BitmapFactory.decodeByteArray(png, 0, png.size) }.getOrNull()
        if (bitmap == null) {
            iconPackLogger.w("snapshot entry undecodable pkg=$packageName bytes=${png.size}")
            return null
        }
        return Icon.createWithBitmap(bitmap).also {
            decodedIconCache[packageName] = it
            iconPackLogger.d("lookup pkg=$packageName decision=HIT bytes=${png.size}")
        }
    }

    /** A loaded index is refreshed in the background once past its window; the old one keeps serving. */
    private fun isSnapshotStale(): Boolean =
        snapshotIndex != null && System.currentTimeMillis() - snapshotFetchedAtMs >= SNAPSHOT_REFRESH_MS

    private fun scheduleSnapshotFetch(context: Context) {
        val due = synchronized(snapshotFetchLock) {
            if (snapshotInFlight) return@synchronized false
            val nowMs = System.currentTimeMillis()
            val ready = if (snapshotIndex == null) {
                nowMs - lastSnapshotAttemptMs >= SNAPSHOT_RETRY_BACKOFF_MS
            } else {
                nowMs - snapshotFetchedAtMs >= SNAPSHOT_REFRESH_MS
            }
            if (ready) {
                snapshotInFlight = true
                lastSnapshotAttemptMs = nowMs
            }
            ready
        }
        if (!due) return
        val appContext = context.applicationContext ?: context
        iconPackPrefetchExecutor.execute {
            val startedAtMs = System.currentTimeMillis()
            val loaded = runCatching { loadSnapshot(appContext) }
            snapshotInFlight = false
            val index = loaded.getOrNull()
            if (index == null) {
                iconPackLogger.w(
                    "snapshot load failed kept=${snapshotIndex?.size ?: -1} " +
                    "took=${System.currentTimeMillis() - startedAtMs}ms: " +
                    "${loaded.exceptionOrNull()?.javaClass?.simpleName}",
                )
                return@execute
            }
            snapshotIndex = index
            decodedIconCache.clear()
            snapshotFetchedAtMs = System.currentTimeMillis()
            iconPackLogger.i(
                "snapshot loaded entries=${index.size} took=${System.currentTimeMillis() - startedAtMs}ms",
            )
        }
    }

    /** Background worker only: reads the whole index through the provider in one open. */
    private fun loadSnapshot(context: Context): Map<String, ByteArray> {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(ICON_PACK_PREF_AUTHORITY)
            .appendPath(ICON_PACK_PREF_PATH_SNAPSHOT)
            .build()
        val stream = context.contentResolver.openInputStream(uri) ?: error("provider served no snapshot")
        val snapshot = stream.use { IconPackSnapshotCodec.read(it) } ?: error("snapshot unreadable")
        return snapshot.indexOfPackage()
    }

    // --- White silhouette cache ---------------------------------------------------------------
    // Same background-generation contract as the icon pack above: SystemUI hot paths only read
    // the cache, a miss returns null and a single background worker builds the silhouette.
    private const val SILHOUETTE_CACHE_MAX = 128

    /** Side margin (fraction of the glyph longer side) kept when re-centering a cropped
     * silhouette, so the glyph does not touch the slot edge. */
    private const val SILHOUETTE_MARGIN_RATIO = 0.06f

    // Separate from the manufactured silhouettes: one entry per package per kind, and the layer
    // lookups must not evict the silhouettes the status bar is relying on.
    private const val SHADE_LAYER_CACHE_MAX = 128

    private val shadeLayerCache: MutableMap<String, IconPackEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, IconPackEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IconPackEntry>?): Boolean {
                return size > SHADE_LAYER_CACHE_MAX
            }
        },
    )

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
        silhouettePrefetchExecutor.execute {
            try {
                val icon = createWhiteSilhouetteIcon(appContext, packageName)
                silhouetteCache[packageName] = IconPackEntry(icon, System.currentTimeMillis())
                shadeLayerCache[packageName] = IconPackEntry(
                    createMonochromeLayerIcon(appContext, packageName),
                    System.currentTimeMillis(),
                )
            } catch (_: Throwable) {
                // Package manager / bitmap failures are expected (uninstalled app, work profile,
                // OEM icon quirk). Retain any previously generated silhouette and back off
                // through the entry timestamp.
                val retained = silhouetteCache[packageName]?.icon
                silhouetteCache[packageName] = IconPackEntry(retained, System.currentTimeMillis())
                val retainedLayer = shadeLayerCache[packageName]?.icon
                shadeLayerCache[packageName] = IconPackEntry(retainedLayer, System.currentTimeMillis())
            } finally {
                inFlightSilhouettePrefetches.remove(packageName)
            }
        }
    }

    /**
     * Background worker only: renders the app's shipped themed layer as authored.
     *
     * A legacy flattened launcher icon has no separate monochrome layer and returns null, which is
     * the signal for the caller to fall back to the threshold-manufactured silhouette.
     */
    private fun createMonochromeLayerIcon(context: Context, packageName: String): Icon? {
        val appIcon = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        if (appIcon !is AdaptiveIconDrawable || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        val layer = runCatching { appIcon.monochrome }.getOrNull() ?: return null
        if (layer.intrinsicWidth <= 0 || layer.intrinsicHeight <= 0) return null
        val bitmap = runCatching {
            ImgUtils.drawableToBitmap(layer.constantState?.newDrawable() ?: layer)
        }.getOrNull() ?: return null
        val normalized = normalizeSilhouetteBounds(bitmap)
        val icon = silhouetteIconOrNull(normalized, "pkg=$packageName layer=monochrome-direct")
        silhouetteLogger.i(
            "shade layer pkg=$packageName src=${bitmap.width}x${bitmap.height} " +
                "out=${normalized.width}x${normalized.height} accepted=${icon != null}",
        )
        return icon
    }

    /**
     * Background worker only: builds a white-alpha silhouette from the app's shaped adaptive
     * layer, mirroring the XMSF `createMonochromeSilhouette` producer.
     *
     * Legacy (non-adaptive) launcher icons ship as flattened bitmaps. Monochrome mode tints the
     * posted glyph regardless, so keeping such an opaque bitmap produces a solid white block;
     * converting it to white-alpha keeps the logo silhouette instead. When the rework leaves the
     * glyph opaque to every edge, the artwork carries no shape to recover and the result is
     * rejected rather than cached.
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
        silhouetteLogger.i(
            "silhouette pkg=$packageName layer=$layer " +
            "intrinsic=${shaped.intrinsicWidth}x${shaped.intrinsicHeight} " +
            "src=${white.width}x${white.height} out=${normalized.width}x${normalized.height} " +
            "changed=${normalized !== white}"
        )
        return silhouetteIconOrNull(normalized, "pkg=$packageName layer=$layer")
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
            silhouetteLogger.d("normalize in=${width}x$height empty-alpha keep original")
            return bitmap
        }
        val contentWidth = maxX - minX + 1
        val contentHeight = maxY - minY + 1
        silhouetteLogger.d(
            "normalize in=${width}x$height bbox=[$minX,$minY,$maxX,$maxY] " +
            "content=${contentWidth}x$contentHeight"
        )
        if (contentWidth >= width - 1 && contentHeight >= height - 1) {
            silhouetteLogger.d("normalize full-bleed keep original")
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
