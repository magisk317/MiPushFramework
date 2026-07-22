package io.github.magisk317.mipush.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.mipush.feature.wizard.WelcomeActivity
import io.github.magisk317.mipush.manager.di.ManagerDependencies

/**
 * Launcher / LSPosed module-settings entry for the standalone manager host.
 * Opens the in-package manager UI rather than redirecting into XMSF.
 */
class ManagerLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ManagerDependencies.startAsRemoteHost(applicationContext)
        startActivity(
            Intent(this, WelcomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
