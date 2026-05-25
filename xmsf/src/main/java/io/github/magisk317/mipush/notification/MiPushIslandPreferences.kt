package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal data class MiPushIslandOptions(
    val enabled: Boolean = true,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val focusNotification: Boolean = true,
) {
    val canBuildFocusPayload: Boolean
        get() = enabled && focusNotification
}

internal object MiPushIslandPreferences {
    fun read(context: Context): MiPushIslandOptions {
        return runCatching {
            val appContext = context.applicationContext ?: context
            val repository = PreferenceRepository(appContext.dataStore)
            runBlocking {
                MiPushIslandOptions(
                    enabled = repository.islandEnabled.first(),
                    timeoutSecs = repository.islandTimeout.first().coerceAtLeast(1),
                    firstFloat = repository.islandFirstFloat.first(),
                    enableFloat = repository.islandEnableFloat.first(),
                    showNotification = repository.islandShowNotification.first(),
                    focusNotification = repository.islandFocusNotification.first(),
                )
            }
        }.getOrDefault(MiPushIslandOptions())
    }
}
