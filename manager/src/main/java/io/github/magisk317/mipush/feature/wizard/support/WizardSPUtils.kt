package io.github.magisk317.mipush.feature.wizard.support

import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Compatibility helper around wizard completion state.
 */
internal object WizardSPUtils {
    private val preferenceRepository by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceRepository()
    }

    suspend fun shouldShowWizard(): Boolean = preferenceRepository.showWizard.first()

    suspend fun setShouldShowWizard(value: Boolean) = preferenceRepository.setShowWizard(value)

    fun finishWizard(context: ComponentActivity) {
        context.lifecycleScope.launch {
            setShouldShowWizard(false)
            ActivityCompat.finishAffinity(context)
            context.startActivity(LegacyUiEntryPoints.mainActivityIntent(context))
        }
    }
}
