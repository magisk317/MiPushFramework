package io.github.magisk317.mipush.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xiaomi.xmsf.utils.ConfigCenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.feature.navigation.AppDestinations
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints

@Singleton
class ConfigNavigationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val configCenter: ConfigCenter,
    private val syncRepository: ConfigSyncRepository,
) {
    constructor() : this(
        Utils.getApplication()!!,
        com.magisk317.utils.Singleton.instance<ConfigCenter>(),
        ConfigSyncRepository(),
    )

    suspend fun createIntentForPackage(packageName: String): Intent {
        val treeUri: Uri? = configCenter.getConfigurationDirectoryAsync()
        val matchedPath = syncRepository.resolvePackageConfigPath(packageName, treeUri)
        val route = if (matchedPath != null) {
            AppDestinations.ConfigEditor.route(matchedPath)
        } else {
            AppDestinations.ConfigsSearch.route(packageName)
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
