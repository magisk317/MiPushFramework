package top.trumeet.mipushframework.wizard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import top.trumeet.mipushframework.main.MainPage

/**
 * Wizard welcome page
 */
class WelcomeActivity : AppCompatActivity() {
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
        startActivity(Intent(this, MainPage::class.java))
    }
}
