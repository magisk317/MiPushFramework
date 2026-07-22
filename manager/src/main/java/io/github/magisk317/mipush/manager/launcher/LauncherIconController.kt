package io.github.magisk317.mipush.manager.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the visible desktop icon by enabling exactly one launcher activity-alias.
 * The real [ManagerLauncherActivity] stays enabled for cross-package redirects and LSPosed settings.
 */
object LauncherIconController {
    const val ICON_DEFAULT = "default"
    const val ICON_LEGACY = "legacy"
    const val ICON_XMSF = "xmsf"

    private val aliases = linkedMapOf(
        ICON_DEFAULT to "io.github.magisk317.mipush.app.ManagerLauncherActivityDefault",
        ICON_LEGACY to "io.github.magisk317.mipush.app.ManagerLauncherActivityLegacy",
        ICON_XMSF to "io.github.magisk317.mipush.app.ManagerLauncherActivityXmsf",
    )

    fun apply(context: Context, iconId: String) {
        val selected = if (iconId in aliases) iconId else ICON_DEFAULT
        val pm = context.packageManager
        for ((id, className) in aliases) {
            val state = if (id == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, className),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
