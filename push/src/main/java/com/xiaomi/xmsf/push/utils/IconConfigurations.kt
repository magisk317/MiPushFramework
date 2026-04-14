package com.xiaomi.xmsf.push.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.util.Pair
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.json.JSONException
import io.github.magisk317.mipush.Global
import io.github.magisk317.mipush.common.utils.Singleton
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.app.ConfigCenter
import kotlinx.coroutines.runBlocking

import javax.inject.Inject
import javax.inject.Singleton as JavaxSingleton

@JavaxSingleton
class IconConfigurations @Inject constructor(
    private val configCenter: ConfigCenter
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(io.github.magisk317.mipush.common.utils.Singleton.instance<ConfigCenter>())

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (t: Throwable) {
            io.github.aakira.napier.Napier.w("Singleton.reset failed for IconConfigurations", t, tag = "IconConfigurations")
        }
    }
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
            val exceptions = mutableListOf<Pair<DocumentFile, JSONException>>()
            val loadedFiles = mutableListOf<DocumentFile>()
            parseDirectory(context, treeUri, exceptions, loadedFiles)

            if (loadedFiles.isNotEmpty() && runBlocking { configCenter.isShowConfigurationListOnLoadedAsync() }) {
                val loadedList = StringBuilder("loaded icon configuration list:")
                for (file in loadedFiles) {
                    loadedList.append('\n')
                    loadedList.append(file.name)
                }
                Utils.makeText(context, loadedList, Toast.LENGTH_SHORT)
            }
            if (exceptions.isNotEmpty()) {
                for (pair in exceptions) {
                    val errmsg = ConfigurationsLoader.getJsonExceptionMessage(context, pair)
                    Utils.makeText(context, errmsg.toString(), Toast.LENGTH_LONG)
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
        exceptions: MutableList<Pair<DocumentFile, JSONException>>,
        loadedFiles: MutableList<DocumentFile>
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
                loadedFiles.add(file)
            } catch (e: JSONException) {
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
