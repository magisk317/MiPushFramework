package io.github.magisk317.mipush.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.feature.wizard.WelcomeActivity
import io.github.magisk317.mipush.platform.support.LegacyComponentNames

/**
 * Launcher / LSPosed module-settings entry for the standalone manager host.
 *
 * This trampoline is noHistory only. It immediately hands the current task to the real
 * Welcome/Main activity, so the multi-task switcher keeps a stable MainActivity root instead of
 * inheriting an excluded launcher shell. It is the only exported Activity that XMSF compatibility
 * aliases may start across package boundaries.
 */
class ManagerLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(resolveTargetIntent())
        finish()
    }

    private fun resolveTargetIntent(): Intent {
        val targetClass = intent.getStringExtra(EXTRA_LEGACY_TARGET_CLASS)
            ?.takeIf { it in LegacyComponentNames.manifestActivities }
        val component = when (targetClass) {
            null, LegacyComponentNames.WELCOME_ACTIVITY -> WelcomeActivity::class.java
            LegacyComponentNames.MAIN_ACTIVITY -> MainActivity::class.java
            else -> runCatching { Class.forName(targetClass).asSubclass(Activity::class.java) }
                .getOrDefault(WelcomeActivity::class.java)
        }
        return Intent(this, component)
            .putExtras(intent)
    }

    private companion object {
        const val EXTRA_LEGACY_TARGET_CLASS =
            "io.github.magisk317.mipush.extra.LEGACY_TARGET_CLASS"
    }
}
