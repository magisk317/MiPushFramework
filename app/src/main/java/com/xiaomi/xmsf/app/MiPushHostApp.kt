package com.xiaomi.xmsf.app

import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.control.PushControllerUtils

class MiPushHostApp : MiPushFrameworkApp() {
    override fun onAppDependenciesStarted() {
        if (!BuildConfig.BUNDLED_MANAGER) {
            return
        }
        if (PushControllerUtils.isAppMainProc(this)) {
            // Class is only on the classpath for the bundled composition flavor.
            Class.forName("io.github.magisk317.mipush.manager.di.ManagerDependencies")
                .getMethod("start", android.content.Context::class.java)
                .invoke(null, this)
        }
    }
}
