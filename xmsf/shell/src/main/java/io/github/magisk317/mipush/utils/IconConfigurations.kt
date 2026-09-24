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
        legacyIconConfigs[pkg]?.let { return it }

        // 2. Query ANIP SDK
        val anip = anipInstance ?: return null
        val icon: NotificationIcon = anip.getIcon(pkg) ?: return null
        val bitmap = runCatching {
            runBlocking(Dispatchers.IO) { icon.loadBitmap() }
        }.getOrNull()

        return IconConfig(
            appName = icon.label,
            packageName = icon.packageName,
            bitmap = bitmap,
            colorInt = icon.color,
            overlay = icon.overlay,
        )
    }

    private fun ensureAnipInitialized(appContext: Context): Anip {
        anipInstance?.let { return it }
        synchronized(this) {
            anipInstance?.let { return it }
            val config = AnipConfig(
                systemVariant = SystemVariant.MIOS,
                source = RemoteSource.GitHub(
                    repository = ConfigDefaults.ICON_REMOTE_REPOSITORY
                ),
            )
            val instance = Anip(appContext, config)
            anipInstance = instance
            runCatching {
                runBlocking(Dispatchers.IO) { instance.reload() }
            }
            return instance
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
}
