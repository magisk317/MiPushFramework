package io.github.magisk317.mipush.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.platform.support.ManagerUiEntryPoints

class ConfigNavigationHelper constructor(
    private val context: Context,
    private val configCenter: ConfigCenter,
    private val syncRepository: ConfigSyncRepository,
) {
    suspend fun createIntentForPackage(packageName: String): Intent {
        val treeUri: Uri? = configCenter.getConfigurationDirectoryAsync()
        val matchedPath = syncRepository.resolvePackageConfigPath(packageName, treeUri)
        return ManagerUiEntryPoints.configurationsIntent(
            context = context,
            initialQuery = packageName.takeUnless { matchedPath != null },
            initialPath = matchedPath,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun openForPackage(packageName: String) {
        context.startActivity(createIntentForPackage(packageName))
    }
}
