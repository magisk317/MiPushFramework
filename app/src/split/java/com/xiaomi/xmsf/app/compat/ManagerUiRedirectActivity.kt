package com.xiaomi.xmsf.app.compat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.xiaomi.xmsf.app.R
import io.github.magisk317.mipush.platform.support.LegacyComponentNames

/**
 * Thin XMSF-side compatibility entry used by the default split packaging.
 * Forwards legacy component names into the standalone manager package.
 */
class ManagerUiRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetClass = intent.component?.className
            ?.takeIf { it in LEGACY_MANAGER_ACTIVITIES }
            ?: LegacyComponentNames.WELCOME_ACTIVITY
        val redirect = Intent(intent)
            .setClassName(LegacyComponentNames.MANAGER_PACKAGE, targetClass)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(redirect)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.manager_ui_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.manager_ui_unavailable, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private companion object {
        val LEGACY_MANAGER_ACTIVITIES = LegacyComponentNames.manifestActivities
    }
}
