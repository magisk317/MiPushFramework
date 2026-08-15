package io.github.magisk317.mipush.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.app.ConfigCenter

class IconConfigurations constructor(
    @Suppress("unused") configCenter: ConfigCenter? = null
) {
    @Volatile
    private var iconConfigs: Map<String, IconConfig> = emptyMap()

    @Volatile
    private var embeddedIconConfigs: Map<String, IconConfig> = emptyMap()

    @Serializable
    class IconConfig {
        var appName: String? = null
        var packageName: String? = null
        var iconBitmap: String? = null
        var iconColor: String? = null
        var contributorName: String? = null
        var isEnabled: Boolean? = null
        var isEnabledAll: Boolean? = null

        fun bitmap(): Bitmap? {
            return try {
                val bitmapArray = Base64.decode(iconBitmap, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.size)
            } catch (_: Throwable) {
                null
            }
        }

        fun color(): Int {
            if (iconColor == null) {
                return NotificationCompat.COLOR_DEFAULT
            }
            return Color.parseColor(iconColor)
        }
    }

    fun init(context: Context?, treeUri: Uri?): Boolean {
        if (context == null || treeUri == null) {
            iconConfigs = embeddedIconConfigs
            return true
        }
        val loaded = loadDirectory(context, treeUri).getOrElse { error ->
            logE("Failed to load icon configurations", error)
            iconConfigs = embeddedIconConfigs
            return false
        }
        // A user directory may override an embedded entry, but it is never required for the
        // built-in catalog to work after a reboot or before SAF permissions are restored.
        iconConfigs = embeddedIconConfigs + loaded
        return true
    }

    fun initFromAssets(context: Context?): Boolean {
        if (context == null) return false
        val loaded: Map<String, IconConfig> = runCatching {
            buildMap {
                for (fileName in context.assets.list(EMBEDDED_ASSET_DIRECTORY).orEmpty()) {
                    if (!fileName.endsWith(".json", ignoreCase = true)) continue
                    val json = context.assets.open(
                        "$EMBEDDED_ASSET_DIRECTORY/$fileName",
                    ).bufferedReader().use { it.readText() }
                    putAll(parse(json))
                }
            }
        }.getOrElse { error ->
            logE("Failed to load embedded icon configurations", error)
            emptyMap<String, IconConfig>()
        }
        embeddedIconConfigs = loaded
        iconConfigs = loaded
        logI("Loaded embedded icon configurations: ${loaded.size}")
        return loaded.isNotEmpty()
    }

    fun get(pkg: String): IconConfig? = iconConfigs[pkg]

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
                logI("Successfully loaded icon configuration: $name")
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

    private companion object {
        const val EMBEDDED_ASSET_DIRECTORY = "icon"
    }
}
