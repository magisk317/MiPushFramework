package io.github.magisk317.mipush.utils

import com.topjohnwu.superuser.Shell
import io.github.magisk317.mipush.runtime.BuildConfig

object PrivilegeElevator {
    init {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setTimeout(10)
        )
    }

    @JvmStatic
    fun tryToElevate() {
        Shell.getShell()
    }
}
