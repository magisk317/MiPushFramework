package io.github.magisk317.mipush.utils

import android.content.Context
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object DiagnosticExportModes {
    fun fromDebugLoggingSetting(context: Context): DiagnosticExportMode {
        val debugLoggingEnabled = runCatching {
            runBlocking {
                PreferenceRepository(context.applicationContext.dataStore).isDebugMode.first()
            }
        }.getOrDefault(false)
        return DiagnosticExportMode.fromDebugLogging(debugLoggingEnabled)
    }
}
