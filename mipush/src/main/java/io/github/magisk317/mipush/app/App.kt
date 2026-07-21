package io.github.magisk317.mipush.app

import android.app.Application
import io.github.magisk317.mipush.app.runtime.ManagerRuntimeProbe
import io.github.magisk317.mipush.app.runtime.ManagerRuntimeProbeFactory

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    lateinit var managerRuntimeProbe: ManagerRuntimeProbe
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        managerRuntimeProbe = ManagerRuntimeProbeFactory.start(this)
    }

    override fun onTerminate() {
        if (::managerRuntimeProbe.isInitialized) {
            managerRuntimeProbe.close()
        }
        super.onTerminate()
    }
}
