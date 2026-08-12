package io.github.magisk317.mipush.hook.keepalive

import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME

internal data class KeepAliveFlags(
    val ready: Boolean = false,
    val oomAdj: Boolean = false,
    val antiKill: Boolean = false,
    val standbyBypass: Boolean = false,
    val dozeBypass: Boolean = false,
)

internal object KeepAlivePolicy {
    const val FOREGROUND_APP_ADJ = 0
    const val STANDBY_BUCKET_ACTIVE = 10

    private const val AUTOMATIC_KILL_REASON = 13
    private val restrictedBuckets = setOf(20, 30, 40, 45, 50)
    private val automaticKillSubReasons = setOf(2, 3, 4, 6, 15, 18)

    fun desiredOomAdj(flags: KeepAliveFlags, processName: String?, currentAdj: Int): Int? {
        if (!flags.ready || !flags.oomAdj || processName != XMSF_PACKAGE_NAME || currentAdj <= FOREGROUND_APP_ADJ) {
            return null
        }
        return FOREGROUND_APP_ADJ
    }

    fun desiredStandbyBucket(flags: KeepAliveFlags, packageName: String?, currentBucket: Int): Int? {
        if (!flags.ready || !flags.standbyBypass || packageName != XMSF_PACKAGE_NAME) return null
        return STANDBY_BUCKET_ACTIVE.takeIf { currentBucket in restrictedBuckets }
    }

    fun desiredIdleState(flags: KeepAliveFlags, packageName: String?, idle: Boolean): Boolean? {
        if (!flags.ready || !flags.standbyBypass || packageName != XMSF_PACKAGE_NAME || !idle) return null
        return false
    }

    fun shouldSuppressKill(
        flags: KeepAliveFlags,
        processName: String?,
        reason: Int?,
        subReason: Int?,
        currentNameMapping: Boolean,
        currentPidMapping: Boolean,
    ): Boolean {
        return flags.ready &&
            flags.antiKill &&
            processName == XMSF_PACKAGE_NAME &&
            reason == AUTOMATIC_KILL_REASON &&
            subReason in automaticKillSubReasons &&
            currentNameMapping &&
            currentPidMapping
    }

    fun shouldSuppressPackageKill(
        flags: KeepAliveFlags,
        packageName: String?,
        reason: Int?,
        subReason: Int?,
        callerWillRestart: Boolean?,
        doit: Boolean?,
        evenPersistent: Boolean?,
        setRemoved: Boolean?,
        uninstalling: Boolean?,
    ): Boolean {
        return flags.ready &&
            flags.antiKill &&
            packageName == XMSF_PACKAGE_NAME &&
            reason == AUTOMATIC_KILL_REASON &&
            subReason in automaticKillSubReasons &&
            callerWillRestart == false &&
            doit == true &&
            evenPersistent == false &&
            setRemoved == false &&
            uninstalling == false
    }
}
