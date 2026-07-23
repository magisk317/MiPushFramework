package io.github.magisk317.mipush.feature.wizard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.magisk317.mipush.feature.main.WelcomeIslandNotifier
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.feature.wizard.support.WizardSPUtils
import kotlinx.coroutines.launch

/**
 * Wizard welcome page
 */
open class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WelcomeIslandNotifier.notifyAfterInstallOrUpdate(this)
        lifecycleScope.launch {
            if (WizardSPUtils.shouldShowWizard()) {
                jumpToRequestPermissionPage()
            } else {
                jumpToMainActivity()
            }
            finish()
        }
    }

    private fun jumpToRequestPermissionPage() {
        startActivity(LegacyUiEntryPoints.requestPermissionIntent(this))
    }

    private fun jumpToMainActivity() {
        val startRoute = intent?.getStringExtra("extra_start_route")
        val startTab = intent?.getStringExtra("extra_start_tab")
        startActivity(
            LegacyUiEntryPoints.mainActivityIntent(
                context = this,
                startRoute = startRoute,
                startTab = startTab,
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
