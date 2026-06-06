package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal data class MiPushIslandOptions(
    val enabled: Boolean = true,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val showOriginalNotification: Boolean = true,
    val focusNotification: Boolean = true,
) {
    val canBuildFocusPayload: Boolean
        get() = enabled && focusNotification && enableFloat
}

internal object MiPushIslandPreferences {
    fun read(context: Context, packageName: String? = null): MiPushIslandOptions {
        return runCatching {
            val appContext = context.applicationContext ?: context
            val repository = PreferenceRepository(appContext.dataStore)
            runBlocking {
                val globalEnabled = repository.islandEnabled.first()
                val globalFocusNotification = repository.islandFocusNotification.first()
                val appEnabled = packageName?.takeIf { it.isNotBlank() }
                    ?.let(RegisteredApplicationDb::getIslandEnabled)
                val appFocusNotification = packageName?.takeIf { it.isNotBlank() }
                    ?.let(RegisteredApplicationDb::getIslandFocusNotificationEnabled)
                MiPushIslandOptions(
                    enabled = globalEnabled && (appEnabled ?: true),
                    timeoutSecs = repository.islandTimeout.first().coerceAtLeast(1),
                    firstFloat = repository.islandFirstFloat.first(),
                    enableFloat = repository.islandEnableFloat.first(),
                    showNotification = repository.islandShowNotification.first(),
                    showOriginalNotification = repository.islandShowOriginalNotification.first(),
                    focusNotification = globalFocusNotification && (appFocusNotification ?: true),
                )
            }
        }.getOrDefault(MiPushIslandOptions())
    }
}
