package io.github.magisk317.mipush.platform

/** Platform-neutral keep-alive decisions; process and system mutation stay in Xposed adapters. */
object KeepAlivePolicyCore {
    const val FOREGROUND_APP_ADJ = 0
    const val STANDBY_BUCKET_ACTIVE = 10
    private const val AUTOMATIC_KILL_REASON = 13
    private val restrictedBuckets = setOf(20, 30, 40, 45, 50)
    private val automaticKillSubReasons = setOf(2, 3, 4, 6, 15, 18)

    fun desiredOomAdj(enabled: Boolean, target: Boolean, targetProcess: String, processName: String?, currentAdj: Int): Int? =
        FOREGROUND_APP_ADJ.takeIf { enabled && target && processName == targetProcess && currentAdj > FOREGROUND_APP_ADJ }

    fun desiredStandbyBucket(enabled: Boolean, target: Boolean, targetPackage: String, packageName: String?, currentBucket: Int): Int? =
        STANDBY_BUCKET_ACTIVE.takeIf { enabled && target && packageName == targetPackage && currentBucket in restrictedBuckets }

    fun desiredIdleState(enabled: Boolean, target: Boolean, targetPackage: String, packageName: String?, idle: Boolean): Boolean? =
        false.takeIf { enabled && target && packageName == targetPackage && idle }

    fun shouldSuppressKill(enabled: Boolean, target: Boolean, targetProcess: String, processName: String?, reason: Int?, subReason: Int?, nameMapping: Boolean, pidMapping: Boolean): Boolean =
        enabled && target && processName == targetProcess && reason == AUTOMATIC_KILL_REASON && subReason in automaticKillSubReasons && nameMapping && pidMapping

    fun shouldSuppressPackageKill(enabled: Boolean, target: Boolean, targetPackage: String, packageName: String?, reason: Int?, subReason: Int?, callerWillRestart: Boolean?, doit: Boolean?, evenPersistent: Boolean?, setRemoved: Boolean?, uninstalling: Boolean?): Boolean =
        enabled && target && packageName == targetPackage && reason == AUTOMATIC_KILL_REASON && subReason in automaticKillSubReasons && callerWillRestart == false && doit == true && evenPersistent == false && setRemoved == false && uninstalling == false
}
