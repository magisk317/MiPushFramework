package io.github.magisk317.mipush.manager.root

import com.topjohnwu.superuser.Shell

class ManagerRootAccess {
    @Volatile
    private var cached: Boolean? = null

    init {
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setTimeout(10),
        )
    }

    fun hasCachedRootAccess(): Boolean = cached == true

    fun refreshRootAccessIfGranted(): Boolean {
        if (Shell.isAppGrantedRoot() != true) {
            cached = false
            return false
        }
        return probeRootAccess()
    }

    fun requestRootAccess(): Boolean {
        runCatching { Shell.getShell() }
        return probeRootAccess()
    }

    private fun probeRootAccess(): Boolean {
        val result = runCatching { Shell.cmd("id -u").exec() }.getOrNull()
        val available = result?.isSuccess == true && result.out.firstOrNull()?.trim() == "0"
        cached = available
        return available
    }
}
