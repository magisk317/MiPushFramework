package io.github.magisk317.mipush.feature.wizard.support

import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.app.MiPushFrameworkApp

/**
 * Compatibility helper around wizard completion state.
 */
internal object WizardSPUtils {
    private val preferenceRepository by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceRepository()
    }

    @JvmStatic
    fun shouldShowWizard(context: Context): Boolean =
        runBlocking { preferenceRepository.showWizard.first() }

    @JvmStatic
    fun setShouldShowWizard(value: Boolean, context: Context) {
        MiPushFrameworkApp.applicationScope.launch {
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
