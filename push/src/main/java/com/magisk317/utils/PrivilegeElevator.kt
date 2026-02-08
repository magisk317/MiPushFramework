package com.magisk317.utils

import com.topjohnwu.superuser.Shell
import com.xiaomi.xmsf.BuildConfig

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
