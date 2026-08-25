package io.github.magisk317.mipush.feature.wizard.support

import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import io.github.magisk317.mipush.data.PreferenceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Compatibility helper around wizard completion state.
 */
internal object WizardSPUtils {
    suspend fun shouldShowWizard(preferenceRepository: PreferenceRepository): Boolean =
        preferenceRepository.showWizard.first()

    suspend fun setShouldShowWizard(preferenceRepository: PreferenceRepository, value: Boolean) =
        preferenceRepository.setShowWizard(value)

    fun finishWizard(context: ComponentActivity, preferenceRepository: PreferenceRepository) {
        context.lifecycleScope.launch {
            setShouldShowWizard(preferenceRepository, false)
            ActivityCompat.finishAffinity(context)
        }
    }
}
