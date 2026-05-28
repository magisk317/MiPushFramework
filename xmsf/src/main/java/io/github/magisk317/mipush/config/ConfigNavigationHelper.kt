package io.github.magisk317.mipush.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints

class ConfigNavigationHelper constructor(
    private val context: Context,
    private val configCenter: ConfigCenter,
    private val syncRepository: ConfigSyncRepository,
) {
    constructor() : this(
        Utils.getApplication()!!,
        io.github.magisk317.mipush.common.utils.Singleton.instance<ConfigCenter>(),
        ConfigSyncRepository(),
    )

    suspend fun createIntentForPackage(packageName: String): Intent {
        val treeUri: Uri? = configCenter.getConfigurationDirectoryAsync()
        val matchedPath = syncRepository.resolvePackageConfigPath(packageName, treeUri)
        val route = if (matchedPath != null) {
            "config_editor/${java.net.URLEncoder.encode(matchedPath, java.nio.charset.StandardCharsets.UTF_8.name())}"
        } else {
            "configs_search/${java.net.URLEncoder.encode(packageName, java.nio.charset.StandardCharsets.UTF_8.name())}"
        }
        return LegacyUiEntryPoints.mainActivityIntent(
            context = context,
            startRoute = route,
        )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun openForPackage(packageName: String) {
        context.startActivity(createIntentForPackage(packageName))
    }
}
