package io.github.magisk317.mipush.feature.wizard.support

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.magisk317.mipush.data.PreferenceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Visibility policy for the onboarding report.
 *
 * The only question asked is "has this build version been dismissed". Permission and
 * root state never enter the decision: the report is where an unmet requirement is
 * explained, so gating it on the requirement would hide the explanation exactly when
 * the user needs it.
 */
internal object WizardSPUtils {
    fun currentVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    suspend fun shouldShowWizard(
        context: Context,
        preferenceRepository: PreferenceRepository,
    ): Boolean = preferenceRepository.wizardSeenVersionCode.first() != currentVersionCode(context)

    /**
     * Dismisses the report for this build version. A plain finish is enough: the main
     * screen is already composed underneath, so entering the app costs one tap and never
     * depends on the requirement state.
     */
    fun finishWizard(context: ComponentActivity, preferenceRepository: PreferenceRepository) {
        context.lifecycleScope.launch {
            preferenceRepository.setWizardSeenVersionCode(currentVersionCode(context))
            context.finish()
        }
    }
}
