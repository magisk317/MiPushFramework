package io.github.magisk317.mipush.manager.root

import android.os.Process
import com.topjohnwu.superuser.Shell
import io.github.magisk317.mipush.common.utils.logI

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

    fun cachedGrantState(): Boolean? = cached

    fun refreshRootAccessIfGranted(): Boolean {
        return probeRootAccess(source = "refresh")
    }

    fun requestRootAccess(): Boolean {
        runCatching { Shell.getShell() }
        return probeRootAccess(source = "request")
    }

    private fun probeRootAccess(source: String): Boolean {
        val result = runCatching { Shell.cmd("id -u").exec() }.getOrNull()
        val available = result?.isSuccess == true && result.out.firstOrNull()?.trim() == "0"
        cached = available
        val uid = runCatching { Process.myUid() }.getOrDefault(UNKNOWN_UID)
        val userId = if (uid >= 0) uid / PER_USER_RANGE else UNKNOWN_USER_ID
        val stderr = result?.err.orEmpty()
            .joinToString(" ")
            .take(MAX_LOGGED_STDERR_LENGTH)
            .ifBlank { "none" }
        this.logI(
            "root_probe role=manager source=$source userId=$userId uid=$uid " +
                "grantState=${Shell.isAppGrantedRoot()} available=$available code=${result?.code} stderr=$stderr",
        )
        return available
    }

    companion object {
        private const val PER_USER_RANGE = 100_000
        private const val MAX_LOGGED_STDERR_LENGTH = 160
        private const val UNKNOWN_UID = -1
        private const val UNKNOWN_USER_ID = -1
    }
}
