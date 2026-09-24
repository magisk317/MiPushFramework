package io.github.magisk317.mipush.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.util.Base64
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.highcapable.anip.sdk.Anip
import com.highcapable.anip.sdk.config.AnipConfig
import com.highcapable.anip.sdk.config.RemoteSource
import com.highcapable.anip.sdk.entity.NotificationIcon
import com.highcapable.anip.sdk.type.IconCategory
import com.highcapable.anip.sdk.type.SystemVariant
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Manages notification icon configurations using the official ANIP SDK
 * (Android Notification Icon Project) while maintaining compatibility with legacy JSON structures.
 */
class IconConfigurations constructor(
    @Suppress("unused") configCenter: ConfigCenter? = null
) {
    @Volatile
    private var legacyIconConfigs: Map<String, IconConfig> = emptyMap()

    @Volatile
    private var anipInstance: Anip? = null

    @Serializable
    class IconConfig {
        var appName: String? = null
        var packageName: String? = null
        var iconBitmap: String? = null
        var iconColor: String? = null
        var contributorName: String? = null
        var isEnabled: Boolean? = true
        var isEnabledAll: Boolean? = false

        @kotlinx.serialization.Transient
        private var preloadedBitmap: Bitmap? = null

        constructor()

        constructor(
            appName: String?,
            packageName: String?,
            bitmap: Bitmap?,
            colorInt: Int?,
            overlay: Boolean = false,
        ) {
            this.appName = appName
            this.packageName = packageName
            this.preloadedBitmap = bitmap
            this.iconColor = colorInt?.let { String.format("#%06X", 0xFFFFFF and it) }
            this.isEnabled = true
            this.isEnabledAll = overlay
        }

        fun bitmap(): Bitmap? {
            preloadedBitmap?.let { if (!it.isRecycled) return it }
            return try {
                val base64 = iconBitmap ?: return null
                val bitmapArray = Base64.getDecoder().decode(base64)
                BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.size)
            } catch (_: Throwable) {
                null
            }
        }

        fun color(): Int {
            if (iconColor == null) {
                return NotificationCompat.COLOR_DEFAULT
            }
            return try {
                Color.parseColor(iconColor)
            } catch (_: Throwable) {
                NotificationCompat.COLOR_DEFAULT
            }
        }
    }

    /**
     * Initializes the ANIP SDK instance and optionally loads local user directory overrides.
     */
    fun init(context: Context?, treeUri: Uri?): Boolean {
        if (context == null) return false
        val appContext = context.applicationContext

        ensureAnipInitialized(appContext)

        if (treeUri == null) {
            legacyIconConfigs = emptyMap()
            return true
        }

        val loaded = loadDirectory(appContext, treeUri).getOrElse { error ->
            logE("Failed to load user icon configurations", error)
            emptyMap()
        }
        legacyIconConfigs = loaded
        return true
    }

    /**
     * Initializes icon resources from local cache / embedded state.
     */
    fun initFromAssets(context: Context?): Boolean {
        if (context == null) return false
        val appContext = context.applicationContext
        ensureAnipInitialized(appContext)
        return runBlocking {
            runCatching {
                anipInstance?.reload() ?: false
            }.getOrDefault(false)
        }
    }

    /**
     * Asynchronously checks for and downloads the latest icon resources from the ANIP release bundle.
     */
    suspend fun fetchLatest(context: Context?): Anip.FetchResult = withContext(Dispatchers.IO) {
        if (context == null) {
            return@withContext Anip.FetchResult(
                "Context is null",
                Anip.FetchResult.Status.FAILED
            )
        }
        val anip = ensureAnipInitialized(context.applicationContext)
        runCatching {
            anip.fetch()
        }.getOrElse { error ->
            logW("ANIP fetch failed: ${error.message}", error)
            Anip.FetchResult(error.message ?: "Fetch failed", Anip.FetchResult.Status.FAILED)
        }
    }

    /**
     * Retrieves an icon configuration for [pkg].
     * Priority: User custom directory override > ANIP SDK icon.
     */
    fun get(pkg: String): IconConfig? {
        if (pkg.isBlank()) return null

        // 1. User manual override from treeUri/icon/*.json
        legacyIconConfigs[pkg]?.let {
            logI("IconConfigurations: resolved '$pkg' from custom user directory")
            return it
        }

        // 2. Query ANIP SDK
        val anip = anipInstance
        if (anip == null) {
            logW("IconConfigurations: ANIP instance is null when resolving '$pkg'")
            return null
        }
        val icon: NotificationIcon = anip.getIcon(pkg) ?: run {
            logI("IconConfigurations: ANIP returned no icon for '$pkg'")
            return null
        }
        val bitmap = runCatching {
            runBlocking(Dispatchers.IO) { icon.loadBitmap() }
        }.getOrNull()

        logI("IconConfigurations: successfully resolved '$pkg' from ANIP (label=${icon.label}, color=${icon.color}, hasBitmap=${bitmap != null})")

        return IconConfig(
            appName = icon.label,
            packageName = icon.packageName,
            bitmap = bitmap,
            colorInt = icon.color,
            overlay = icon.overlay,
        )
    }

    /** One ANIP icon library entry, for the manager-side preview grid (metadata only). */
    @Serializable
    data class LibraryEntry(
        val packageName: String,
        val label: String,
        val color: Int? = null,
        val overlay: Boolean = false,
        /** Epoch millis of the icon bundle the entry came from; null when the SDK has no timestamp. */
        val updatedAt: Long? = null,
        /** Icon library category: "app" | "game" | "system" (maps to ANIP IconCategory). */
        val category: String = "app",
    )

    /** (ANIP IconCategory, wire key) pairs, rendered in this order so the flat page stays grouped. */
    private val LIBRARY_CATEGORIES = listOf(
        IconCategory.APP to "app",
        IconCategory.GAME to "game",
        IconCategory.SYSTEM to "system",
    )

    fun encodeLibraryPage(entries: List<LibraryEntry>): String = jsonFormat.encodeToString(entries)

    /**
     * Returns a page of ANIP icon library metadata ordered by package name, so the manager can
     * page through the catalog without exceeding the binder payload ceiling.
     */
    suspend fun libraryPage(context: Context?, offset: Int, limit: Int): List<LibraryEntry> =
        withContext(Dispatchers.IO) {
        if (context == null) return@withContext emptyList()
        val anip = ensureAnipInitialized(context.applicationContext)
        val updatedAt = runCatching { anip.timestamp }.getOrNull()?.takeIf { it > 0L }
        // Gather icons per ANIP category so each entry carries its app/game/system bucket.
        val all = LIBRARY_CATEGORIES.flatMap { (category, key) ->
            anip.getIcons(category)
                .sortedBy { it.packageName }
                .map { icon ->
                    LibraryEntry(
                        packageName = icon.packageName,
                        label = icon.label,
                        color = icon.color,
                        overlay = icon.overlay,
                        updatedAt = updatedAt,
                        category = key,
                    )
                }
        }
        val page = all
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, LIBRARY_PAGE_MAX_ITEMS))
        logI("IconConfigurations: libraryPage offset=$offset limit=$limit total=${all.size} page=${page.size}")
            page
        }

    /** Base64-encoded PNG of the icon for [pkg], or null when the library has no such icon. */
    suspend fun iconBitmapBase64(context: Context?, pkg: String): String? =
        withContext(Dispatchers.IO) {
            if (context == null || pkg.isBlank()) return@withContext null
            val anip = ensureAnipInitialized(context.applicationContext)
            val icon = anip.getIcon(pkg) ?: return@withContext null
            val bitmap = runCatching { icon.loadBitmap() }.getOrNull() ?: return@withContext null
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            Base64.getEncoder().encodeToString(out.toByteArray())
        }

    private fun ensureAnipInitialized(appContext: Context): Anip {        anipInstance?.let { return it }
        synchronized(this) {
            anipInstance?.let { return it }
            logI("IconConfigurations: initializing ANIP engine (MIOS, repo=${ConfigDefaults.ICON_REMOTE_REPOSITORY})...")
            val source = RemoteSource.GitHub(
                repository = ConfigDefaults.ICON_REMOTE_REPOSITORY
            )
            val config = AnipConfig(
                systemVariant = SystemVariant.MIOS,
                source = source,
            )
            // Seed the ANIP cache from the pre-bundled asset ZIP when no
            // release has been fetched yet (cold start / fresh install).
            seedBundledAnipIfNeeded(appContext)
            val instance = Anip(appContext, config)
            anipInstance = instance
            runCatching {
                runBlocking(Dispatchers.IO) { instance.reload() }
            }.onSuccess {
                logI("IconConfigurations: ANIP reload completed successfully")
            }.onFailure { e ->
                logW("IconConfigurations: ANIP reload failed: ${e.message}", e)
            }
            return instance
        }
    }

    /**
     * Extracts the pre-bundled `anip-bundle.zip` from APK assets into the ANIP
     * SDK cache directory so that [Anip.reload] finds it on the first launch
     * without requiring any network access.
     *
     * The extraction only runs when the `releases` directory is empty or
     * missing, so subsequent [Anip.fetch] downloads are never overwritten.
     */
    private fun seedBundledAnipIfNeeded(appContext: Context) {
        try {
            val cacheDir = appContext.cacheDir.resolve("anip-icon-resources")
            val releasesDir = cacheDir.resolve("releases")
            // Only seed when the cache has never been populated.
            if (releasesDir.isDirectory && releasesDir.list()?.isNotEmpty() == true) return

            val assetStream = runCatching {
                appContext.assets.open("anip-bundle.zip")
            }.getOrNull() ?: run {
                logI("IconConfigurations: no bundled anip-bundle.zip in assets; skipping seed")
                return
            }

            logI("IconConfigurations: seeding ANIP cache from bundled asset...")
            cacheDir.mkdirs()
            val bundledDir = releasesDir.resolve("bundled")
            bundledDir.mkdirs()

            // Extract the ZIP into releases/bundled/
            java.util.zip.ZipInputStream(assetStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = bundledDir.resolve(entry.name)
                    // Guard against zip-slip
                    if (!outFile.canonicalPath.startsWith(bundledDir.canonicalPath)) {
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().buffered().use { out ->
                            zip.copyTo(out)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Write the source identity so BundleStore.isCurrentSource() passes.
            // The SDK's internal createIdentity() is SHA-256(manifestUrl).
            val manifestUrl = "https://github.com/${ConfigDefaults.ICON_REMOTE_REPOSITORY}/releases/latest/download/anip-release.json"
            val sourceIdentity = java.security.MessageDigest.getInstance("SHA-256")
                .digest(manifestUrl.toByteArray())
                .joinToString(separator = "") { byte ->
                    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                }
            cacheDir.resolve("source").writeText(sourceIdentity)
            // Write a dummy timestamp so the SDK recognises a valid cache.
            cacheDir.resolve("timestamp").writeText("1")

            logI("IconConfigurations: ANIP cache seeded successfully from bundled asset")
        } catch (e: Throwable) {
            logW("IconConfigurations: failed to seed ANIP cache from bundled asset: ${e.message}", e)
        }
    }

    private fun loadDirectory(
        context: Context,
        treeUri: Uri,
    ): Result<Map<String, IconConfig>> = runCatching {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@runCatching emptyMap()
        val iconDirectory = root.findFile("icon")
            ?: return@runCatching emptyMap()
        buildMap {
            for (file in iconDirectory.listFiles()) {
                val name = file.name ?: continue
                if (!name.endsWith(".json", ignoreCase = true)) continue
                putAll(parse(ConfigurationsLoader.readTextFromUri(context, file.uri)))
                logI("Successfully loaded user icon configuration: $name")
            }
        }
    }

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    internal fun parse(json: String): Map<String, IconConfig> =
        jsonFormat.decodeFromString<List<IconConfig>>(json)
            .mapNotNull { config ->
                config.packageName?.takeIf { it.isNotBlank() }?.let { it to config }
            }
            .toMap()

    companion object {
        private const val LIBRARY_PAGE_MAX_ITEMS = 200
    }
}
