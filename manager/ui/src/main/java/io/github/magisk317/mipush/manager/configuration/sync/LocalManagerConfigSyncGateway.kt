package io.github.magisk317.mipush.manager.configuration.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.magisk317.mipush.manager.application.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigListSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.configuration.ConfigEditorSnapshot
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.configuration.LocalConfigRepository
import io.github.magisk317.mipush.configuration.toSummary
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadRequestDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.core.configuration.LocalConfigSummary
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Manager-process config sync: SAF tree + GitHub/GitLab catalog stay local;
 * activating configs on the runtime uses [ManagerRuntimeClient.uploadConfiguration].
 */
class LocalManagerConfigSyncGateway(
    private val context: Context,
    private val syncRepository: ConfigSyncRepository,
    private val preferenceRepository: PreferenceRepository,
    private val client: ManagerRuntimeClient,
) : ManagerConfigSyncGateway {
    override suspend fun loadLocalSnapshot(treeUri: Uri?): ManagerConfigListSnapshot {
        val snapshot = syncRepository.loadLocalSnapshot(treeUri)
        return ManagerConfigListSnapshot(items = snapshot.items, remoteError = snapshot.remoteError)
    }

    override suspend fun loadRemoteSnapshot(treeUri: Uri?): ManagerConfigListSnapshot {
        return try {
            val snapshot = syncRepository.loadRemoteSnapshot(treeUri)
            ManagerConfigListSnapshot(items = snapshot.items, remoteError = snapshot.remoteError)
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            ManagerConfigListSnapshot(items = emptyList(), remoteError = error.message ?: "remote_fetch_failed")
        }
    }

    override suspend fun readLocalEditorSnapshot(treeUri: Uri?, path: String): ManagerConfigEditorSnapshot {
        val snapshot = syncRepository.readLocalEditorSnapshot(treeUri, path)
        return snapshot.toManager()
    }

    override suspend fun readRemoteEditorSnapshot(treeUri: Uri?, path: String): ManagerConfigEditorSnapshot {
        return try {
            val snapshot = syncRepository.readRemoteEditorSnapshot(treeUri, path)
            snapshot.toManager()
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            ManagerConfigEditorSnapshot(path = path, remoteError = error.message ?: "remote_fetch_failed")
        }
    }

    override suspend fun pullAll(
        treeUri: Uri,
        onProgress: ((current: Int, total: Int, path: String) -> Unit)?,
    ): Int = syncRepository.pullAll(treeUri, onProgress)

    override suspend fun importDocuments(treeUri: Uri, uris: List<Uri>, isIcon: Boolean): Int =
        syncRepository.importDocuments(treeUri, uris, isIcon)

    override suspend fun saveLocal(treeUri: Uri, path: String, content: String): LocalConfigSummary {
        val local = syncRepository.saveLocal(treeUri, path, content)
        // Activate non-icon JSON on the runtime immediately.
        if (!path.startsWith("icon/")) {
            uploadToRuntime(path = path, content = content)
        }
        return local.toSummary()
    }

    override suspend fun resetToRemote(treeUri: Uri, path: String): LocalConfigSummary {
        val local = syncRepository.resetToRemote(treeUri, path)
        if (!path.startsWith("icon/")) {
            val content = localConfigText(treeUri, path)
            if (content != null) {
                uploadToRuntime(path = path, content = content)
            }
        }
        return local.toSummary()
    }

    override suspend fun openForPackage(packageName: String) {
        val treeUri = preferenceRepository.configDirectory.first()?.let(Uri::parse)
        val matchedPath = syncRepository.resolvePackageConfigPath(packageName, treeUri)
        val encoded = java.net.URLEncoder.encode(
            matchedPath ?: packageName,
            StandardCharsets.UTF_8.name(),
        )
        val route = if (matchedPath != null) {
            "config_editor/$encoded"
        } else {
            "configs_search/$encoded"
        }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun activateAllLocalConfigs(treeUri: Uri?): Int = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext 0
        val files = LocalConfigRepository(context).listLocalFiles(treeUri)
            .filter { !it.path.startsWith("icon/") && it.isValid }
        var activated = 0
        for (file in files) {
            val content = localConfigText(treeUri, file.path) ?: continue
            if (uploadToRuntime(path = file.path, content = content)) {
                activated++
            }
        }
        activated
    }

    private suspend fun localConfigText(treeUri: Uri, path: String): String? {
        return LocalConfigRepository(context).readLocalFile(treeUri, path)?.rawText
    }

    private suspend fun uploadToRuntime(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > ManagerProtocol.MAX_CONFIGURATION_UPLOAD_BYTES) return@withContext false
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                output.write(bytes)
                output.flush()
            }
            val request = ManagerConfigurationUploadRequestDto(
                path = path.substringAfterLast('/').ifBlank { path },
                contentLength = bytes.size,
                parcelFileDescriptor = readSide,
            )
            when (val result = client.uploadConfiguration(request)) {
                is ManagerRuntimeResult.Success -> result.value.success
                else -> false
            }
        } catch (_: Exception) {
            false
        } finally {
            // Binder duplicates the descriptor for the runtime process; this original manager-side
            // read end remains caller-owned and must be closed after every synchronous call result.
            runCatching { readSide.close() }
            runCatching { writeSide.close() }
        }
    }

    private fun ConfigEditorSnapshot.toManager() = ManagerConfigEditorSnapshot(
        path = path,
        local = local,
        remote = remote,
        remoteMeta = remoteMeta,
        localMeta = localMeta?.toSummary(),
        remoteError = remoteError,
    )
}
