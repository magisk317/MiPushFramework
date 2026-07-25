package com.xiaomi.xmsf.app.compat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.xiaomi.xmsf.app.R
import io.github.magisk317.mipush.platform.support.LegacyComponentNames

/**
 * Thin XMSF-side compatibility entry for the runtime-only app package.
 * Forwards legacy launcher / component names into the standalone manager package.
 *
 * Manager UI Activities in :mipush are not exported; only [ManagerLauncherActivity] is a safe
 * cross-package entry. Deep-link targets are passed as extras for the launcher to open in-process.
 */
class ManagerUiRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedClass = intent.component?.className
            ?.takeIf { it in LEGACY_MANAGER_ACTIVITIES }
            ?: LegacyComponentNames.WELCOME_ACTIVITY
        val redirect = buildRedirectIntent(requestedClass)
        try {
            startActivity(redirect)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "manager package missing for $requestedClass", error)
            Toast.makeText(this, R.string.manager_ui_unavailable, Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Log.w(TAG, "manager entry blocked for $requestedClass", error)
            Toast.makeText(this, R.string.manager_ui_unavailable, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun buildRedirectIntent(requestedClass: String): Intent {
        val launch = packageManager.getLaunchIntentForPackage(LegacyComponentNames.MANAGER_PACKAGE)
        val base = if (launch != null) {
            Intent(launch)
        } else {
            Intent().setClassName(
                LegacyComponentNames.MANAGER_PACKAGE,
                LegacyComponentNames.MANAGER_LAUNCHER_ACTIVITY,
            )
        }
        return base
            .putExtra(EXTRA_LEGACY_TARGET_CLASS, requestedClass)
            .putExtras(intent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private companion object {
        const val TAG = "ManagerUiRedirect"
        const val EXTRA_LEGACY_TARGET_CLASS =
            "io.github.magisk317.mipush.extra.LEGACY_TARGET_CLASS"
        val LEGACY_MANAGER_ACTIVITIES = LegacyComponentNames.manifestActivities
    }
}
