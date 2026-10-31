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
 * The report is a one-shot per build version: it is evaluated only on the first launch after
 * an install or an upgrade, and the version is consumed whether or not the report is shown.
 * A fully granted device therefore never sees it, an upgrade with a genuinely outstanding
 * requirement shows it exactly once, and ordinary cold or warm starts never surface it again
 * (the explanation stays reachable from 设置 -> 服务权限 -> 权限检查).
 */
internal object WizardSPUtils {
    fun currentVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /**
     * Consumes the launch check for this build version.
     *
     * @return true only when this is the first evaluation for this version AND a requirement
     *         is outstanding. The version is recorded in both cases, so the second cold start
     *         of the same build can never replay the report.
     */
    suspend fun consumeLaunchReport(
        context: Context,
        preferenceRepository: PreferenceRepository,
        hasOutstandingRequirement: Boolean,
    ): Boolean {
        val versionCode = currentVersionCode(context)
        if (preferenceRepository.wizardSeenVersionCode.first() == versionCode) return false
        preferenceRepository.setWizardSeenVersionCode(versionCode)
        return hasOutstandingRequirement
    }

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
