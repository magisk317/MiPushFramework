package io.github.magisk317.mipush.feature.wizard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.feature.main.WelcomeIslandNotifier
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
        startActivity(Intent(this, RequestPermissionPage::class.java))
    }

    private fun jumpToMainActivity() {
        val startRoute = intent?.getStringExtra(MainActivity.EXTRA_START_ROUTE)
        val startTab = intent?.getStringExtra(MainActivity.EXTRA_START_TAB)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                if (!startRoute.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_START_ROUTE, startRoute)
                }
                if (!startTab.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_START_TAB, startTab)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
