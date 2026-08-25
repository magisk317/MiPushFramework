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

    fun forceStopXmsfForUser(userId: Int): Boolean {
        if (userId < 0 || !refreshRootAccessIfGranted()) return false
        val result = runCatching {
            Shell.cmd(forceStopXmsfCommand(userId)).exec()
        }.getOrNull()
        val success = result?.isSuccess == true
        this.logI {
            "xmsf_recovery action=force_stop userId=$userId success=$success code=${result?.code}"
        }
        return success
    }

    /**
     * Restarts XMSF for one Android user after a failed Binder recovery cycle.
     *
     * force-stop intentionally leaves the package stopped and does not replay BOOT_COMPLETED.
     * Clear that state first, then use the exported XMSF BootReceiver as the normal startup
     * entrypoint. Every command remains explicitly scoped to the requesting user.
     */
    fun recoverXmsfForUser(userId: Int): Boolean {
        if (userId < 0 || !refreshRootAccessIfGranted()) return false

        val forceStop = runCatching {
            Shell.cmd(forceStopXmsfCommand(userId)).exec()
        }.getOrNull()
        if (forceStop?.isSuccess != true) {
            this.logI {
                "xmsf_recovery action=recover stage=force_stop userId=$userId " +
                    "success=false code=${forceStop?.code}"
            }
            return false
        }

        val unstop = runCatching {
            Shell.cmd(unstopXmsfCommand(userId)).exec()
        }.getOrNull()
        if (unstop?.isSuccess != true) {
            this.logI {
                "xmsf_recovery action=recover stage=unstop userId=$userId " +
                    "success=false code=${unstop?.code}"
            }
            return false
        }

        val boot = runCatching {
            Shell.cmd(bootCompletedXmsfCommand(userId)).exec()
        }.getOrNull()
        val success = boot?.isSuccess == true
        this.logI {
            "xmsf_recovery action=recover stage=boot_receiver userId=$userId " +
                "success=$success code=${boot?.code}"
        }
        return success
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
        this.logI {
            "root_probe role=manager source=$source userId=$userId uid=$uid " +
                "grantState=${Shell.isAppGrantedRoot()} available=$available code=${result?.code} stderr=$stderr"
        }
        return available
    }

    companion object {
        internal fun forceStopXmsfCommand(userId: Int): String {
            require(userId >= 0) { "userId must be non-negative" }
            return "am force-stop --user $userId com.xiaomi.xmsf"
        }

        internal fun unstopXmsfCommand(userId: Int): String {
            require(userId >= 0) { "userId must be non-negative" }
            return "pm unstop --user $userId com.xiaomi.xmsf"
        }

        internal fun bootCompletedXmsfCommand(userId: Int): String {
            require(userId >= 0) { "userId must be non-negative" }
            return "am broadcast --user $userId -a android.intent.action.BOOT_COMPLETED " +
                "-n com.xiaomi.xmsf/io.github.magisk317.mipush.receiver.BootReceiver"
        }

        private const val PER_USER_RANGE = 100_000
        private const val MAX_LOGGED_STDERR_LENGTH = 160
        private const val UNKNOWN_UID = -1
        private const val UNKNOWN_USER_ID = -1
    }
}
