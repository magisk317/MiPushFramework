package io.github.magisk317.mipush.feature.wizard.support

import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob

/**
 * Compatibility helper around wizard completion state.
 */
internal object WizardSPUtils {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val preferenceRepository by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceRepository()
    }

    @JvmStatic
    fun shouldShowWizard(context: Context): Boolean =
        runBlocking { preferenceRepository.showWizard.first() }

    @JvmStatic
    fun setShouldShowWizard(value: Boolean, context: Context) {
        scope.launch {
            preferenceRepository.setShowWizard(value)
        }
    }

    @JvmStatic
    fun finishWizard(context: Activity) {
        setShouldShowWizard(false, context)
        ActivityCompat.finishAffinity(context)
        context.startActivity(LegacyUiEntryPoints.mainActivityIntent(context))
    }
}
