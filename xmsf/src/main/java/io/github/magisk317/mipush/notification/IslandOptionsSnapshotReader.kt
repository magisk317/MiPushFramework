package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.common.island.IslandOptions
import io.github.magisk317.mipush.data.IslandSettingsSnapshot
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import kotlinx.coroutines.runBlocking

internal data class IslandOptionsSnapshot(
    val options: IslandOptions,
    val sensitiveDebugLogMode: Boolean,
)

internal object IslandOptionsSnapshotReader {
    fun read(context: Context, packageName: String? = null): IslandOptionsSnapshot {
        val appContext = context.applicationContext ?: context
        return runCatching {
            val settings = runBlocking {
                PreferenceRepository(appContext.dataStore).readIslandSettingsSnapshot()
            }
            val normalizedPackage = packageName?.takeIf { it.isNotBlank() }
            merge(
                settings = settings,
                appEnabled = normalizedPackage?.let(RegisteredApplicationDb::getIslandEnabled),
                appFocusNotification = normalizedPackage
                    ?.let(RegisteredApplicationDb::getIslandFocusNotificationEnabled),
                packageScoped = normalizedPackage != null,
            )
        }.getOrElse {
            IslandOptionsSnapshot(
                options = IslandOptions(),
                sensitiveDebugLogMode = false,
            )
        }
    }

    internal fun merge(
        settings: IslandSettingsSnapshot,
        appEnabled: Boolean?,
        appFocusNotification: Boolean?,
        packageScoped: Boolean = appFocusNotification != null,
    ): IslandOptionsSnapshot = IslandOptionsSnapshot(
        options = IslandOptions(
            enabled = settings.enabled && (appEnabled ?: true),
            timeoutSecs = settings.timeoutSecs.coerceAtLeast(1),
            firstFloat = settings.firstFloat,
            enableFloat = settings.enableFloat,
            showNotification = settings.showNotification,
            showOriginalNotification = settings.showOriginalNotification,
            // The global snapshot drives SystemUI's authorization bypass. Only a package-scoped
            // read needs the registered-app focus opt-in to narrow generated payloads.
            focusNotification = settings.focusNotification &&
                (!packageScoped || appFocusNotification == true),
            colorStatusBarIcon = settings.colorStatusBarIcon,
            colorStatusBarIconGlobal = settings.colorStatusBarIconGlobal,
            dualAppEnabled = settings.dualAppEnabled,
        ),
        sensitiveDebugLogMode = settings.sensitiveDebugLogMode,
    )
}
