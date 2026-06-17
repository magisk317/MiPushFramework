package com.xiaomi.xmsf.app

import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.manager.di.ManagerDependencies

class MiPushHostApp : MiPushFrameworkApp() {
    override fun onAppDependenciesStarted() {
        if (PushControllerUtils.isAppMainProc(this)) {
            ManagerDependencies.start(this)
        }
    }
}
