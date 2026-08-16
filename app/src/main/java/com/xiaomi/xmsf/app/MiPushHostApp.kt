package com.xiaomi.xmsf.app

import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.manager.di.ManagerDependencies
import io.github.magisk317.mipush.runtime.MaintenanceCycleBridge

/**
 * Packaged application host for the XMSF runtime process.
 */
class MiPushHostApp : MiPushFrameworkApp() {
    override fun onAppDependenciesStarted() {
        if (PushControllerUtils.isAppMainProc(this)) {
            ManagerDependencies.startFromAppShell(this)
            MaintenanceCycleBridge.setListener { sequence, action ->
                ManagerDependencies.onMaintenanceTick(sequence, action)
            }
        }
    }
}
