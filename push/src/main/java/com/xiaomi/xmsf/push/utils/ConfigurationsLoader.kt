package com.xiaomi.xmsf.push.utils

import android.content.Context
import android.net.Uri
import android.util.Pair
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.elvishew.xlog.XLog
import com.nihility.Global
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
import top.trumeet.common.utils.Utils

class ConfigurationsLoader {
    private var version: String? = null
    private var packageConfigs: MutableMap<String, MutableList<Any>> = hashMapOf()

    private var mContext: Context? = null
    private var mTreeUri: Uri? = null
    private var mDocumentFile: DocumentFile? = null
    private var mLastLoadTime: Long = 0

    fun getConfigs(): MutableMap<String, MutableList<Any>> = packageConfigs

    fun init(context: Context?, treeUri: Uri?): Boolean {
        mLastLoadTime = System.currentTimeMillis()
        packageConfigs.clear()
        do {
            if (context == null || treeUri == null) {
                break
            }
            val exceptions = mutableListOf<Pair<DocumentFile, JSONException>>()
            val loadedFiles = mutableListOf<DocumentFile>()
            parseDirectory(context, treeUri, exceptions, loadedFiles)

            if (loadedFiles.isNotEmpty() && Global.ConfigCenter().isShowConfigurationListOnLoaded(context)) {
                val loadedList = StringBuilder("loaded configuration list:")
                for (file in loadedFiles) {
                    loadedList.append('\n')
                    loadedList.append(file.name)
                }
                Utils.makeText(context, loadedList, Toast.LENGTH_SHORT)
            }
            if (exceptions.isNotEmpty()) {
                for (pair in exceptions) {
                    val errmsg = getJsonExceptionMessage(context, pair)
                    logger.e(errmsg)
                    Utils.makeText(context, errmsg.toString(), Toast.LENGTH_LONG)
                }
                break
            }
            return true
        } while (false)
        return false
    }

    private fun parseDirectory(
        context: Context,
        treeUri: Uri,
        exceptions: MutableList<Pair<DocumentFile, JSONException>>,
        loadedFiles: MutableList<DocumentFile>
    ): Boolean {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return true
        mContext = context
        mTreeUri = treeUri
        mDocumentFile = documentFile
        logger.i("parseDirectory uri: [%s]", treeUri.path)
        val files = documentFile.listFiles()
        for (file in files) {
            logger.i("file: [%s], type: [%s]", file.name, file.type)
            val name = file.name ?: continue
            if (!name.lowercase().endsWith(".json")) {
                continue
            }
            val json = readTextFromUri(context, file.uri)
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
    fun load(json: String) {
        parse(json)
    }

    @Throws(JSONException::class)
    private fun parse(json: String) {
        val jsonObject = JSONObject(json)
        version = jsonObject.getString("version")
        val packageConfigsObj = jsonObject.getJSONObject("configs")
        val packageNames = packageConfigsObj.keys()
        while (packageNames.hasNext()) {
            val packageName = packageNames.next()
            val configsObj = packageConfigsObj.getJSONArray(packageName)
            packageConfigs[packageName] = parseConfigs(configsObj)
        }
    }

    @Throws(JSONException::class)
    private fun parseConfigs(configsObj: JSONArray): MutableList<Any> {
        val configs = mutableListOf<Any>()
        for (i in 0 until configsObj.length()) {
            val config = configsObj.get(i)
            when (config) {
                is JSONArray -> configs.add(config)
                is String -> configs.add(config)
                else -> configs.add(parseConfig(configsObj.getJSONObject(i)))
            }
        }
        return configs
    }

    @Throws(JSONException::class)
    fun parseConfig(configObj: JSONObject): PackageConfig {
        val config = PackageConfig(Configurations.getInstance())
        if (!configObj.isNull(PackageConfig.KEY_META_INFO)) {
            val obj = JSONObject()
            obj.put(PackageConfig.KEY_META_INFO, configObj.getJSONObject(PackageConfig.KEY_META_INFO))
            config.cfgMatch = obj
        }
        if (!configObj.isNull(PackageConfig.KEY_NEW_META_INFO)) {
            val obj = JSONObject()
            obj.put(PackageConfig.KEY_META_INFO, configObj.getJSONObject(PackageConfig.KEY_NEW_META_INFO))
            config.cfgReplace = obj
        }
        if (!configObj.isNull(PackageConfig.KEY_MATCH)) {
            config.cfgMatch = configObj.getJSONObject(PackageConfig.KEY_MATCH)
        }
        if (!configObj.isNull(PackageConfig.KEY_REPLACE)) {
            config.cfgReplace = configObj.getJSONObject(PackageConfig.KEY_REPLACE)
        }
        if (!configObj.isNull(PackageConfig.KEY_OPERATION)) {
            val operations = configObj.getString(PackageConfig.KEY_OPERATION)
            config.operation = operations.split("[\\s|]+".toRegex()).toMutableSet()
        }
        if (!configObj.isNull(PackageConfig.KEY_STOP)) {
            config.stop = configObj.getBoolean(PackageConfig.KEY_STOP)
        }
        return config
    }

    fun reInitIfDirectoryUpdated() {
        val context = mContext ?: return
        val treeUri = mTreeUri ?: return
        val documentFile = mDocumentFile ?: return
        if (documentFile.lastModified() > mLastLoadTime) {
            init(context, treeUri)
        }
    }

    companion object {
        private val logger = XLog.tag(ConfigurationsLoader::class.java.simpleName).build()

        @JvmStatic
        fun getJsonExceptionMessage(
            context: Context,
            pair: Pair<DocumentFile, JSONException>
        ): StringBuilder {
            val file = pair.first
            val e = pair.second
            e.printStackTrace()

            var errmsg = StringBuilder(e.toString())
            val pattern = Pattern.compile(" character (\\d+) of ")
            val matcher = pattern.matcher(errmsg.toString())
            if (matcher.find()) {
                val pos = matcher.group(1)?.toIntOrNull() ?: 0
                val json = readTextFromUri(context, file.uri)
                val beforeErr = json.substring(0, pos).split("\n")
                val errorLine = beforeErr.size
                val errorColumn = beforeErr[beforeErr.size - 1].length
                val exceptionMessage = errmsg.substring(0, matcher.start())
                    .replace("org.json.JSONException: ", "")
                    .replaceFirst("(after )(.*)( at)".toRegex(), "$1\"$2\"$3")
                errmsg = StringBuilder("$exceptionMessage line $errorLine column $errorColumn")

                val jsonLine = json.split("\n").toMutableList()
                jsonLine[errorLine - 1] =
                    jsonLine[errorLine - 1].substring(0, errorColumn - 1) +
                    "┋" +
                    jsonLine[errorLine - 1].substring(errorColumn - 1)
                for (i in maxOf(0, errorLine - 2)..minOf(jsonLine.size - 1, errorLine)) {
                    errmsg.append('\n')
                    errmsg.append(i + 1)
                    errmsg.append(": ")
                    errmsg.append(jsonLine[i])
                }
            }
            errmsg.insert(0, "${file.name}\n")
            return errmsg
        }

        @JvmStatic
        fun readTextFromUri(context: Context, uri: Uri): String {
            val stringBuilder = StringBuilder()
            try {
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    val safeInputStream = inputStream ?: return@use
                    BufferedReader(InputStreamReader(safeInputStream)).use { reader ->
                        val buffer = CharArray(1024)
                        while (true) {
                            val len = reader.read(buffer)
                            if (len == -1) {
                                break
                            }
                            stringBuilder.append(buffer, 0, len)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Utils.makeText(context, e.toString(), Toast.LENGTH_LONG)
            }
            return stringBuilder.toString()
        }
    }
}
