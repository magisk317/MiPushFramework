package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.net.Uri
import android.util.Pair
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.configurations.ConfigJson
import io.github.magisk317.mipush.common.configurations.ConfigJsonArray
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import io.github.magisk317.mipush.common.configurations.ConfigJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.Utils

class ConfigurationsLoader private constructor(
    private val configCenter: ConfigCenter?,
    @Suppress("unused") private val jsonOnly: Boolean,
) {
    constructor(configCenter: ConfigCenter) : this(configCenter, false)

    internal constructor() : this(null, true)

    private var version: String? = null
    private var packageConfigs: MutableMap<String, MutableList<Any>> = hashMapOf()

    private var mContext: Context? = null
    private var mTreeUri: Uri? = null
    private var mDocumentFile: DocumentFile? = null
    private var mLastLoadTime: Long = 0

    fun getConfigs(): MutableMap<String, MutableList<Any>> = packageConfigs

    fun init(context: Context?, treeUri: Uri?, configurations: Configurations): Boolean {
        mLastLoadTime = System.currentTimeMillis()
        packageConfigs.clear()
        do {
            if (context == null || treeUri == null) {
                break
            }
            val exceptions = mutableListOf<Pair<DocumentFile, ConfigJsonException>>()
            val loadedFiles = mutableListOf<DocumentFile>()
            parseDirectory(context, treeUri, exceptions, loadedFiles, configurations)

            if (loadedFiles.isNotEmpty() && runBlocking { configCenter?.isShowConfigurationListOnLoadedAsync() ?: false }) {
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
                    logE(errmsg.toString())
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
        exceptions: MutableList<Pair<DocumentFile, ConfigJsonException>>,
        loadedFiles: MutableList<DocumentFile>,
        configurations: Configurations
    ): Boolean {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return true
        mContext = context
        mTreeUri = treeUri
        mDocumentFile = documentFile
        logD("parseDirectory uri: [${treeUri.path}]")
        val files = documentFile.listFiles()
        for (file in files) {
            logD("file: [${file.name}], type: [${file.type}]")
            val name = file.name ?: continue
            if (!name.lowercase().endsWith(".json")) {
                continue
            }
            val json = readTextFromUri(context, file.uri)
            try {
                parse(json, configurations)
                loadedFiles.add(file)
            } catch (e: ConfigJsonException) {
                exceptions.add(Pair(file, e))
            }
        }
        return false
    }

    @Throws(ConfigJsonException::class)
    fun load(json: String, configurations: Configurations) {
        parse(json, configurations)
    }

    @Throws(ConfigJsonException::class)
    private fun parse(json: String, configurations: Configurations) {
        val jsonObject = ConfigJson.parseObject(json)
        version = jsonObject.getString("version")
        val packageConfigsObj = jsonObject.getConfigJsonObject("configs")
        val packageNames = packageConfigsObj.keys()
        while (packageNames.hasNext()) {
            val packageName = packageNames.next()
            val configsObj = packageConfigsObj.getConfigJsonArray(packageName)
            packageConfigs[packageName] = parseConfigs(configsObj, configurations)
        }
    }

    @Throws(ConfigJsonException::class)
    private fun parseConfigs(configsObj: ConfigJsonArray, configurations: Configurations): MutableList<Any> {
        val configs = mutableListOf<Any>()
        for (i in 0 until configsObj.length()) {
            val config = configsObj.get(i)
            when (config) {
                is ConfigJsonArray -> configs.add(config)
                is String -> configs.add(config)
                else -> configs.add(parseConfig(configsObj.getConfigJsonObject(i), configurations))
            }
        }
        return configs
    }

    @Throws(ConfigJsonException::class)
    fun parseConfig(configObj: ConfigJsonObject, configurations: Configurations): PackageConfig {
        val config = PackageConfig(configurations)
        if (!configObj.isNull(PackageConfig.KEY_META_INFO)) {
            val obj = ConfigJsonObject()
            obj.put(PackageConfig.KEY_META_INFO, configObj.getConfigJsonObject(PackageConfig.KEY_META_INFO))
            config.cfgMatch = obj
        }
        if (!configObj.isNull(PackageConfig.KEY_NEW_META_INFO)) {
            val obj = ConfigJsonObject()
            obj.put(PackageConfig.KEY_META_INFO, configObj.getConfigJsonObject(PackageConfig.KEY_NEW_META_INFO))
            config.cfgReplace = obj
        }
        if (!configObj.isNull(PackageConfig.KEY_MATCH)) {
            config.cfgMatch = configObj.getConfigJsonObject(PackageConfig.KEY_MATCH)
        }
        if (!configObj.isNull(PackageConfig.KEY_REPLACE)) {
            config.cfgReplace = configObj.getConfigJsonObject(PackageConfig.KEY_REPLACE)
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

    fun reInitIfDirectoryUpdated(configurations: Configurations) {
        val context = mContext ?: return
        val treeUri = mTreeUri ?: return
        val documentFile = mDocumentFile ?: return
        if (documentFile.lastModified() > mLastLoadTime) {
            init(context, treeUri, configurations)
        }
    }

    companion object {
        private val TAG = ConfigurationsLoader::class.java.simpleName

        @JvmStatic
        fun getJsonExceptionMessage(
            context: Context,
            pair: Pair<DocumentFile, ConfigJsonException>
        ): StringBuilder {
            val file = pair.first
            val e = pair.second
            Napier.e("JSON parse error in ${file.name}", e, tag = TAG)

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
                    .replace("ConfigJsonException: ", "")
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
                Napier.e("readTextFromUri failed", e, tag = TAG)
                Utils.makeText(context, e.toString(), Toast.LENGTH_LONG)
            }
            return stringBuilder.toString()
        }
    }
}
