package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.common.island.IslandOptions
import io.github.magisk317.mipush.data.IslandSettingsSnapshot
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.common.utils.logW
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal data class IslandOptionsSnapshot(
    val options: IslandOptions,
    val logSanitizationEnabled: Boolean,
)

internal object IslandOptionsSnapshotReader {
    private val cacheStarted = AtomicBoolean(false)
    private val cachedGlobalSettings = AtomicReference<IslandSettingsSnapshot?>(null)

    fun initialize(context: Context, scope: CoroutineScope) {
        if (!cacheStarted.compareAndSet(false, true)) return
        refreshAsync(context, scope)
    }

    fun refreshAsync(context: Context, scope: CoroutineScope) {
        if (!cacheStarted.get()) return
        val appContext = context.applicationContext ?: context
        scope.launch(Dispatchers.IO) {
            runCatching {
                PreferenceRepository(appContext.dataStore).readIslandSettingsSnapshot()
            }.onSuccess(cachedGlobalSettings::set)
                .onFailure {
                    logW("failed to refresh cached island settings", it)
                }
        }
    }

    fun read(
        context: Context,
        packageName: String? = null,
        userId: Int? = null,
    ): IslandOptionsSnapshot {
        val appContext = context.applicationContext ?: context
        val cachedSettings = cachedGlobalSettings.get()
        if (cacheStarted.get() && cachedSettings == null) {
            return unavailableSnapshot()
        }
        return runCatching {
            val settings = cachedSettings ?: runBlocking {
                PreferenceRepository(appContext.dataStore).readIslandSettingsSnapshot()
            }
            val normalizedPackage = packageName?.takeIf { it.isNotBlank() }
            val packageSettings = normalizedPackage?.let {
                RegisteredApplicationDb.getIslandSettings(it, userId)
            }
            merge(
                settings = settings,
                appEnabled = packageSettings?.enabled,
                appFocusNotification = packageSettings?.focusNotification,
                packageScoped = normalizedPackage != null,
            )
        }.getOrElse { error ->
            logW(
                "island options unavailable; disabling island proxy and preserving original notification",
                error,
            )
            unavailableSnapshot()
        }
    }

    internal fun unavailableSnapshot(): IslandOptionsSnapshot = IslandOptionsSnapshot(
        options = IslandOptions(
            enabled = false,
            showNotification = false,
            showOriginalNotification = true,
            focusNotification = false,
        ),
        logSanitizationEnabled = false,
    )

    internal fun updateCachedSettings(settings: IslandSettingsSnapshot) {
        cacheStarted.set(true)
        cachedGlobalSettings.set(settings)
    }

    internal fun clearCachedSettings() {
        cachedGlobalSettings.set(null)
        cacheStarted.set(false)
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
            rendererMode = io.github.magisk317.mipush.common.island.IslandRendererMode.parse(
                settings.rendererMode,
            ),
            visualEnabled = settings.visualEnabled,
            dynamicColor = settings.dynamicColor,
            blurEnabled = settings.blurEnabled,
            glassEnabled = settings.glassEnabled,
            outerGlowEnabled = settings.outerGlowEnabled,
            animationEnabled = settings.animationEnabled,
            colorStatusBarIcon = settings.colorStatusBarIcon,
            colorStatusBarIconGlobal = settings.colorStatusBarIconGlobal,
            dualAppEnabled = settings.dualAppEnabled,
        ),
        logSanitizationEnabled = settings.logSanitizationEnabled,
    )
}
