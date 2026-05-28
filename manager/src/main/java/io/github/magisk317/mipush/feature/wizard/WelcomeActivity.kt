package io.github.magisk317.mipush.feature.wizard

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.magisk317.mipush.feature.main.WelcomeIslandNotifier
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.feature.wizard.support.WizardSPUtils

/**
 * Wizard welcome page
 */
open class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WelcomeIslandNotifier.notifyAfterInstallOrUpdate(this)
        if (WizardSPUtils.shouldShowWizard(this)) {
            jumpToRequestPermissionPage()
        } else {
            jumpToMainActivity()
        }
        finish()
    }

    private fun jumpToRequestPermissionPage() {
        startActivity(LegacyUiEntryPoints.requestPermissionIntent(this))
    }

    private fun jumpToMainActivity() {
        startActivity(LegacyUiEntryPoints.mainActivityIntent(this))
    }
}
