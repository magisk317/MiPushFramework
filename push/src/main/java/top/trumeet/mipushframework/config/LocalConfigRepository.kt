package top.trumeet.mipushframework.config

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.trumeet.common.utils.Utils

@Singleton
class LocalConfigRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    constructor() : this(Utils.getApplication()!!)

    suspend fun listLocalFiles(treeUri: Uri?): List<LocalConfigFile> = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext emptyList()
        val directory = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        directory.listFiles()
            .filter { it.isFile }
            .filter { it.name.orEmpty().lowercase().endsWith(".json") }
            .sortedBy { it.name.orEmpty() }
            .mapNotNull { inspectFile(it) }
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
        val target = directory.findFile(path) ?: requireNotNull(directory.createFile("application/json", path)) {
            "Unable to create configuration file: $path"
        }
        backupExisting(path, target)
        writeText(target.uri, content)
        requireNotNull(inspectFile(target)) { "Unable to inspect saved configuration: $path" }
    }

    suspend fun importDocuments(treeUri: Uri, uris: List<Uri>): List<LocalConfigFile> = withContext(Dispatchers.IO) {
        val directory = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
            "Configuration directory is unavailable"
        }
        uris.mapNotNull { sourceUri ->
            val fileName = queryDisplayName(sourceUri)
                ?.takeIf { it.lowercase().endsWith(".json") }
                ?: return@mapNotNull null
            val target = directory.findFile(fileName) ?: requireNotNull(
                directory.createFile("application/json", fileName),
            ) {
                "Unable to create imported configuration: $fileName"
            }
            backupExisting(fileName, target)
            writeText(target.uri, readText(sourceUri))
            inspectFile(target)
        }
    }

    private fun findFile(treeUri: Uri?, path: String): DocumentFile? {
        if (treeUri == null) return null
        val directory = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return directory.findFile(path)
    }

    private fun inspectFile(file: DocumentFile): LocalConfigFile? {
        val name = file.name ?: return null
        val rawText = readText(file.uri)
        val validation = ConfigJsonSupport.validateAndFormat(rawText)
        return LocalConfigFile(
            path = name,
            name = name.removeSuffix(".json"),
            uri = file.uri,
            sha = ConfigJsonSupport.stableSha(rawText),
            size = file.length(),
            lastModified = file.lastModified(),
            isValid = validation.valid,
            validationError = validation.errorMessage,
        )
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
        val content = readText(file.uri)
        val timestamp = System.currentTimeMillis()
        val backupDir = File(context.filesDir, "config-backups/$timestamp").apply { mkdirs() }
        File(backupDir, path).writeText(content, Charsets.UTF_8)
    }
}
