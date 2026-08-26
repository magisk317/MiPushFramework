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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class IslandOptionsSnapshot(
    val options: IslandOptions,
    val logSanitizationEnabled: Boolean,
)

/**
 * Observes the shared DataStore via Flow and keeps an in-memory cache of the latest
 * IslandSettingsSnapshot. This replaces the previous ACTION_PREF_CHANGED broadcast
 * refresh path: the cache now updates automatically on every DataStore write.
 */
object IslandOptionsSnapshotReader {
    private val cachedGlobalSettings = AtomicReference<IslandSettingsSnapshot?>(null)
    private val initialized = AtomicBoolean(false)

    /**
     * Start collecting DataStore changes. Idempotent: subsequent calls are no-ops.
     */
    fun initialize(context: Context, scope: CoroutineScope) {
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        scope.launch(Dispatchers.IO) {
            appContext.dataStore.data.collect { preferences ->
                val repo = PreferenceRepository(appContext.dataStore)
                val snapshot = repo.toSnapshot(preferences)
                cachedGlobalSettings.set(snapshot)
                // The shell owns the concrete SystemUI refresh operation.
                NotificationShellBridge.triggerStatusBarRefresh()
            }
        }
    }

    /**
     * No-op retained for backward compatibility with tests. The Flow collector in
     * [initialize] already keeps the cache fresh on every DataStore write.
     */
    @Suppress("unused")
    fun refreshAsync(context: Context, scope: CoroutineScope) {
        // Cache is updated by the Flow collector; nothing to do here.
    }

    fun read(
        context: Context,
        packageName: String? = null,
        userId: Int? = null,
    ): IslandOptionsSnapshot {
        val appContext = context.applicationContext ?: context
        val cachedSettings = cachedGlobalSettings.get()
        if (initialized.get() && cachedSettings == null) {
            return unavailableSnapshot()
        }
        return runCatching {
            val settings = cachedSettings ?: runCatching {
                // Fallback: synchronous read for callers before Flow emits the first value.
                kotlinx.coroutines.runBlocking {
                    val prefs = appContext.dataStore.data.first()
                    PreferenceRepository(appContext.dataStore).toSnapshot(prefs)
                }
            }.getOrNull() ?: return unavailableSnapshot()

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
        cachedGlobalSettings.set(settings)
        initialized.set(true)
    }

    internal fun clearCachedSettings() {
        cachedGlobalSettings.set(null)
        initialized.set(false)
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
