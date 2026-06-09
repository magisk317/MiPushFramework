package io.github.magisk317.mipush.config

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import io.github.magisk317.mipush.utils.ConfigJsonSupport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.utils.ConfigDocumentContent
import io.github.magisk317.mipush.common.utils.Utils

class LocalConfigRepository constructor(
    private val context: Context,
) {
    constructor() : this(Utils.getApplication()!!)

    suspend fun listLocalFiles(treeUri: Uri?): List<LocalConfigFile> = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext emptyList()
        val directory = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val rootFiles = directory.listFiles()
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val path = ConfigLocalPathSupport.parseOrNull(name)?.path ?: return@mapNotNull null
                inspectFile(file, path)
            }
        val iconFiles = directory.findFile(ConfigLocalPathSupport.ICON_DIRECTORY)
            ?.takeIf { it.isDirectory }
            ?.listFiles()
            ?.mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val path = ConfigLocalPathSupport.parseOrNull(
                    "${ConfigLocalPathSupport.ICON_DIRECTORY}/$name",
                )?.path ?: return@mapNotNull null
                inspectFile(file, path)
            }
            .orEmpty()
        (rootFiles + iconFiles).sortedBy { it.path }
    }

    suspend fun readLocalFile(treeUri: Uri?, path: String): ConfigDocumentContent? = withContext(Dispatchers.IO) {
        val file = findFile(treeUri, path) ?: return@withContext null
        val rawText = readText(file.uri)
        val validation = ConfigJsonSupport.validateAndFormat(rawText)
        ConfigDocumentContent(
            rawText = rawText,
            displayText = validation.formatted ?: rawText,
            validation = validation,
        )
    }

    suspend fun writeLocalFile(treeUri: Uri, path: String, content: String): LocalConfigFile = withContext(Dispatchers.IO) {
        val directory = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
            "Configuration directory is unavailable"
        }
        val configPath = ConfigLocalPathSupport.parse(path)
        val parentDirectory = requireNotNull(resolveParentDirectory(directory, configPath, create = true)) {
            "Unable to create configuration directory: ${configPath.parentSegments.joinToString("/")}"
        }
        val existing = parentDirectory.findFile(configPath.fileName)
        val target = when {
            existing == null -> requireNotNull(parentDirectory.createFile("application/json", configPath.fileName)) {
                "Unable to create configuration file: ${configPath.path}"
            }
            existing.isFile -> existing
            else -> error("Configuration path is not a file: ${configPath.path}")
        }
        backupExisting(configPath.path, target)
        writeText(target.uri, content)
        requireNotNull(inspectFile(target, configPath.path)) { "Unable to inspect saved configuration: ${configPath.path}" }
    }

    suspend fun importDocuments(treeUri: Uri, uris: List<Uri>, isIcon: Boolean = false): List<LocalConfigFile> = withContext(Dispatchers.IO) {
        val directory = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
            "Configuration directory is unavailable"
        }
        val targetDirectory = if (isIcon) {
            directory.findFile(ConfigLocalPathSupport.ICON_DIRECTORY)
                ?: requireNotNull(directory.createDirectory(ConfigLocalPathSupport.ICON_DIRECTORY)) {
                    "Unable to create icon directory"
                }
        } else {
            directory
        }
        val prefix = if (isIcon) "${ConfigLocalPathSupport.ICON_DIRECTORY}/" else ""
        uris.mapNotNull { sourceUri ->
            val fileName = queryDisplayName(sourceUri)
                ?.takeIf { it.lowercase().endsWith(".json") }
                ?: return@mapNotNull null
            val target = targetDirectory.findFile(fileName) ?: requireNotNull(
                targetDirectory.createFile("application/json", fileName),
            ) {
                "Unable to create imported configuration: $fileName"
            }
            val relativePath = prefix + fileName
            backupExisting(relativePath, target)
            writeText(target.uri, readText(sourceUri))
            inspectFile(target, relativePath)
        }
    }

    private fun findFile(treeUri: Uri?, path: String): DocumentFile? {
        val configPath = ConfigLocalPathSupport.parseOrNull(path) ?: return null
        if (treeUri == null) return null
        val directory = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return resolveParentDirectory(directory, configPath, create = false)
            ?.findFile(configPath.fileName)
            ?.takeIf { it.isFile }
    }

    private fun inspectFile(file: DocumentFile, path: String): LocalConfigFile? {
        if (!file.isFile) return null
        val configPath = ConfigLocalPathSupport.parseOrNull(path) ?: return null
        val rawText = readText(file.uri)
        val validation = ConfigJsonSupport.validateAndFormat(rawText)
        return LocalConfigFile(
            path = configPath.path,
            name = configPath.name,
            uri = file.uri,
            sha = ConfigJsonSupport.stableSha(rawText),
            size = file.length(),
            lastModified = file.lastModified(),
            isValid = validation.valid,
            validationError = validation.errorMessage,
        )
    }

    private fun resolveParentDirectory(
        directory: DocumentFile,
        configPath: ConfigLocalPath,
        create: Boolean,
    ): DocumentFile? {
        var current = directory
        for (segment in configPath.parentSegments) {
            val existing = current.findFile(segment)
            current = when {
                existing == null && create -> current.createDirectory(segment) ?: return null
                existing?.isDirectory == true -> existing
                else -> return null
            }
        }
        return current
    }

    private fun writeText(uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            val stream = requireNotNull(output) { "Unable to open configuration for writing: $uri" }
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }

    private fun readText(uri: Uri): String {
        return context.contentResolver.openInputStream(uri).use { input ->
            val stream = requireNotNull(input) { "Unable to open configuration: $uri" }
            stream.bufferedReader().use { it.readText() }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            .use { cursor ->
                val safeCursor: Cursor = cursor ?: return null
                if (!safeCursor.moveToFirst()) return null
                val columnIndex = safeCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex < 0) return null
                return safeCursor.getString(columnIndex)
            }
    }

    private fun backupExisting(path: String, file: DocumentFile) {
        if (!file.exists() || file.length() <= 0L) return
        val configPath = ConfigLocalPathSupport.parseOrNull(path) ?: return
        val content = readText(file.uri)
        val timestamp = System.currentTimeMillis()
        val backupDir = File(context.filesDir, "config-backups/$timestamp").apply { mkdirs() }
        val backupFile = File(backupDir, configPath.path)
        backupFile.parentFile?.mkdirs()
        backupFile.writeText(content, Charsets.UTF_8)
    }
}
