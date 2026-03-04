package top.trumeet.mipushframework.wizard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import top.trumeet.mipushframework.main.MainActivity

import dagger.hilt.android.AndroidEntryPoint

/**
 * Wizard welcome page
 */
@AndroidEntryPoint
class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (WizardSPUtils.shouldShowWizard(this)) {
            jumpToRequestPermissionPage()
        } else {
            jumpToMainActivity()
        }
        finish()
    }

    private fun jumpToRequestPermissionPage() {
        startActivity(Intent(this, RequestPermissionPage::class.java))
    }

    private fun jumpToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}
