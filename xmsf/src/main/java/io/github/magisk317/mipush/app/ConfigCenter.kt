package io.github.magisk317.mipush.app

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.platform.support.LegacyComponentNames
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.utils.Configurations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.Constants

/**
 * Push 配置
 */
class ConfigCenter constructor(
    private val context: Context,
    private val preferenceRepository: PreferenceRepository
) {
    private companion object {
        const val TAG = "MiPushConfigCenter"
    }
    @Volatile
    private var permissionDirectory: String? = null

    suspend fun getConfigurationDirectoryAsync(): Uri? {
        val uri = preferenceRepository.configDirectory.first()
        if (uri.isNullOrBlank()) return null
        if (uri != permissionDirectory) {
            val parsed = Uri.parse(uri)
            persistDirectoryPermission(parsed)
            permissionDirectory = uri
        }
        return Uri.parse(uri)
    }

    suspend fun setConfigurationDirectoryAsync(treeUri: Uri): Boolean {
        persistDirectoryPermission(treeUri)
        preferenceRepository.setConfigDirectory(treeUri.toString())
        return true
    }

    suspend fun getXMPPServerAsync(): String? = preferenceRepository.xmppServer.first()

    suspend fun setXMPPServerAsync(host: String): Boolean {
        preferenceRepository.setXmppServer(host)
        return true
    }

    suspend fun isDebugModeAsync(): Boolean = preferenceRepository.isDebugMode.first()

    suspend fun isShowAllEventsAsync(): Boolean = preferenceRepository.isShowAllEvents.first()

    suspend fun isStartForegroundServiceAsync(): Boolean = preferenceRepository.isStartForeground.first()

    suspend fun shouldStartPushAsForegroundServiceAsync(): Boolean =
        preferenceRepository.startPushAsForegroundService.first()

    suspend fun loadConfigurationsNow(context: Context): Boolean =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val appContext = context.applicationContext
            val directory = getConfigurationDirectoryAsync()
            loadConfigurationsFromDirectory(appContext, directory)
        }

    internal fun loadConfigurationsFromDirectory(context: Context, directory: Uri?): Boolean {
        directory?.let { persistDirectoryPermission(it) }
        val appContext = context.applicationContext
        val configLoaded = Configurations.getInstance().init(appContext, directory)
        val iconLoaded = Global.iconConfigurations().init(appContext, directory)
        if (!PushControllerUtils.isAppMainProc(appContext)) {
            val intent = PushRuntimeComponents.newCoreServiceIntent(
                appContext,
                Constants.CONFIGURATIONS_UPDATE_ACTION
            )
            PushServiceStarter.start(appContext, intent)
        }
        return configLoaded && iconLoaded
    }

    private fun persistDirectoryPermission(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onSuccess {
            Logger.withTag(TAG).i { "persisted configuration directory permission uri=$treeUri" }
        }.onFailure { error ->
            Logger.withTag(TAG).w(error) { "unable to persist configuration directory permission uri=$treeUri" }
        }
        runCatching {
            context.grantUriPermission(
                LegacyComponentNames.MANAGER_PACKAGE,
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            Logger.withTag(TAG).i { "granted manager configuration directory permission uri=$treeUri" }
        }.onFailure { error ->
            Logger.withTag(TAG).w(error) { "unable to grant manager configuration directory permission uri=$treeUri" }
        }
    }
}
