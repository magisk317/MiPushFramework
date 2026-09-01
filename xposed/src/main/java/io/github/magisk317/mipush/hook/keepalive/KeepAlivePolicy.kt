package io.github.magisk317.mipush.hook.keepalive

import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.platform.KeepAlivePolicyCore

internal data class KeepAliveFlags(
    val ready: Boolean = false,
    val oomAdj: Boolean = false,
    val antiKill: Boolean = false,
    val standbyBypass: Boolean = false,
    val dozeBypass: Boolean = false,
)

internal object KeepAlivePolicy {
    const val FOREGROUND_APP_ADJ = KeepAlivePolicyCore.FOREGROUND_APP_ADJ
    const val STANDBY_BUCKET_ACTIVE = KeepAlivePolicyCore.STANDBY_BUCKET_ACTIVE

    private const val AUTOMATIC_KILL_REASON = 13
    private val restrictedBuckets = setOf(20, 30, 40, 45, 50)
    private val automaticKillSubReasons = setOf(2, 3, 4, 6, 15, 18)

    fun desiredOomAdj(flags: KeepAliveFlags, processName: String?, currentAdj: Int): Int? {
        return KeepAlivePolicyCore.desiredOomAdj(flags.ready, flags.oomAdj, XMSF_PACKAGE_NAME, processName, currentAdj)
    }

    fun desiredStandbyBucket(flags: KeepAliveFlags, packageName: String?, currentBucket: Int): Int? {
        return KeepAlivePolicyCore.desiredStandbyBucket(flags.ready, flags.standbyBypass, XMSF_PACKAGE_NAME, packageName, currentBucket)
    }

    fun desiredIdleState(flags: KeepAliveFlags, packageName: String?, idle: Boolean): Boolean? {
        return KeepAlivePolicyCore.desiredIdleState(flags.ready, flags.standbyBypass, XMSF_PACKAGE_NAME, packageName, idle)
    }

    fun shouldSuppressKill(
        flags: KeepAliveFlags,
        processName: String?,
        reason: Int?,
        subReason: Int?,
        currentNameMapping: Boolean,
        currentPidMapping: Boolean,
    ): Boolean {
        return KeepAlivePolicyCore.shouldSuppressKill(flags.ready, flags.antiKill, XMSF_PACKAGE_NAME, processName, reason, subReason, currentNameMapping, currentPidMapping)
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
        return KeepAlivePolicyCore.shouldSuppressPackageKill(
            enabled = flags.ready,
            target = flags.antiKill,
            targetPackage = XMSF_PACKAGE_NAME,
            packageName = packageName,
            reason = reason,
            subReason = subReason,
            callerWillRestart = callerWillRestart,
            doit = doit,
            evenPersistent = evenPersistent,
            setRemoved = setRemoved,
            uninstalling = uninstalling,
        )
    }
}
