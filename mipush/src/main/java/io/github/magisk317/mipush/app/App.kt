package io.github.magisk317.mipush.app

import android.app.Application
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.di.ManagerDependencies

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Utils.setApplicationContext(this)
        // Standalone manager host: remote gateways + Compose UI, Binder to XMSF runtime.
        ManagerDependencies.startAsRemoteHost(this)
    }
}
