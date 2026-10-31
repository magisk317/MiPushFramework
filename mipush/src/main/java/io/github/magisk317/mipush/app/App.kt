package io.github.magisk317.mipush.app

import android.app.Application
import io.github.magisk317.mipush.app.di.billingModule
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.di.ManagerDependencies
import io.github.magisk317.mipush.manager.logging.ManagerRuntimeFileLog
import io.github.magisk317.xposed.logging.MagiskLogPolicy

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Standalone manager builds run this Application instead of MiPushFrameworkApp, so the
        // policy has to be installed here too or the manager logs under the neutral default.
        MagiskLogPolicy.install(
            // -PminifyDebug makes the debug variant non-debuggable, which clears
            // BuildConfig.DEBUG; the build type still identifies the capture channel.
            isDebugBuild = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD,
            allowPlaintextLogs = BuildConfig.ALLOW_PLAINTEXT_LOGS,
        )
        Utils.setApplicationContext(this)
        ManagerRuntimeFileLog.init(this)
        // Standalone manager host: remote gateways + Compose UI, Binder to XMSF runtime.
        ManagerDependencies.startAsRemoteHost(this, billingModule)
    }
}
