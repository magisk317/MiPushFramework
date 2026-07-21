package io.github.magisk317.mipush.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.github.magisk317.mipush.platform.support.LegacyComponentNames

class ManagerLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val managerIntent = Intent().setClassName(
            LegacyComponentNames.SERVICE_PACKAGE,
            LegacyComponentNames.WELCOME_ACTIVITY,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(managerIntent)
        } catch (_: ActivityNotFoundException) {
            showUnavailableMessage()
        } catch (_: SecurityException) {
            showUnavailableMessage()
        }
        finish()
    }

    private fun showUnavailableMessage() {
        Toast.makeText(this, R.string.manager_runtime_unavailable, Toast.LENGTH_LONG).show()
    }
}
