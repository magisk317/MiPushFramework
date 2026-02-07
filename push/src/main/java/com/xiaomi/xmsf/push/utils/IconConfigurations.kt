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
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.nihility.Global
import org.json.JSONArray
import org.json.JSONException
import top.trumeet.common.utils.Utils

class IconConfigurations private constructor() {
    private val iconConfigs = hashMapOf<String, IconConfig>()

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

            if (loadedFiles.isNotEmpty() && Global.ConfigCenter().isShowConfigurationListOnLoaded(context)) {
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

    @Throws(JSONException::class)
    private fun parse(json: String) {
        JSONArray(json)
        val configs = Gson().fromJson(json, Array<IconConfig>::class.java) ?: return
        for (config in configs) {
            val pkg = config.packageName ?: continue
            iconConfigs[pkg] = config
        }
    }
}
