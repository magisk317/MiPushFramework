package io.github.magisk317.mipush.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.util.Pair
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.app.ConfigCenter

class IconConfigurations constructor(
    @Suppress("unused") configCenter: ConfigCenter
) {
    private val iconConfigs = hashMapOf<String, IconConfig>()

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
        iconConfigs.clear()
        do {
            if (context == null || treeUri == null) {
                break
            }
            val exceptions = mutableListOf<Pair<DocumentFile, ConfigJsonException>>()
            parseDirectory(context, treeUri, exceptions)

            if (exceptions.isNotEmpty()) {
                for (pair in exceptions) {
                    val errmsg = ConfigurationsLoader.getJsonExceptionMessage(context, pair)
                    logE(errmsg.toString())
                }
                break
            }
            return true
        } while (false)
        return false
    }

    fun get(pkg: String): IconConfig? = iconConfigs[pkg]

    private fun parseDirectory(
        context: Context,
        treeUri: Uri,
        exceptions: MutableList<Pair<DocumentFile, ConfigJsonException>>
    ): Boolean {
        var documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return true
        documentFile = documentFile.findFile("icon") ?: return true
        val files = documentFile.listFiles()
        for (file in files) {
            val name = file.name ?: continue
            if (!name.lowercase().endsWith(".json")) {
                continue
            }
            val json = ConfigurationsLoader.readTextFromUri(context, file.uri)
            try {
                parse(json)
            } catch (e: ConfigJsonException) {
                exceptions.add(Pair(file, e))
            }
        }
        return false
    }

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Throws(Exception::class)
    private fun parse(json: String) {
        val configs = jsonFormat.decodeFromString<List<IconConfig>>(json)
        for (config in configs) {
            val pkg = config.packageName ?: continue
            iconConfigs[pkg] = config
        }
    }
}
