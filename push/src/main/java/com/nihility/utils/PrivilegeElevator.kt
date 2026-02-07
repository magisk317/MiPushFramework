package com.nihility.utils

import com.topjohnwu.superuser.Shell
import com.xiaomi.xmsf.BuildConfig

class PrivilegeElevator {
    companion object {
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }

        @JvmStatic
        fun tryToElevate() {
            Shell.getShell()
        }
    }
}
