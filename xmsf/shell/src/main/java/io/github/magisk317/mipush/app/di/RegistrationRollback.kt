package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimePendingRegistrationRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow

/** Restores temporary admission on failure, including coroutine cancellation during launch/wait. */
internal suspend fun <T> withRegistrationRollback(
    isConfirmed: (T) -> Boolean,
    rollback: () -> Unit,
    operation: suspend () -> T,
): T {
    var confirmed = false
    try {
        return operation().also { confirmed = isConfirmed(it) }
    } finally {
        if (!confirmed) rollback()
    }
}

internal data class ForceRegistrationAdmission(
    val previous: RuntimeRegisteredApplicationRow,
    val pending: RuntimePendingRegistrationRow?,
    val policyVersion: Long,
)

/** Clear stale confirmation before launch; only a new result can confirm this attempt. */
internal fun prepareForceRegistrationAdmission(packageName: String, userId: Int): ForceRegistrationAdmission? =
    PackageRegistrationGuard.withPackage(packageName, userId) {
        val previous = RegisteredApplicationDb.getRegisteredApplication(packageName)
            ?: RegisteredApplicationDb.registerApplication(packageName)
        val pending = RegisteredApplicationDb.getPendingRegistration(packageName, userId)
        if (!RegisteredApplicationDb.setRegistrationPolicy(packageName, RegisteredAppType.ALLOW, userId)) {
            return@withPackage null
        }
        val allowed = RegisteredApplicationDb.getRegisteredApplication(packageName) ?: return@withPackage null
        RegisteredApplicationDb.update(allowed.copy(registeredType = RegisteredAppRegisteredType.Unregistered))
        ForceRegistrationAdmission(previous, pending, PackageRegistrationGuard.policyVersion(packageName, userId))
    }

/** Restore only our temporary policy, never a newer explicit choice or a confirmed result. */
internal fun restoreForceRegistrationAdmission(admission: ForceRegistrationAdmission) {
    val previous = admission.previous
    PackageRegistrationGuard.withPackage(previous.packageName, previous.userId) {
        if (PackageRegistrationGuard.policyVersion(previous.packageName, previous.userId) != admission.policyVersion) {
            return@withPackage
        }
        val latest = RegisteredApplicationDb.getRegisteredApplication(previous.packageName) ?: return@withPackage
        if (latest.registeredType == RegisteredAppRegisteredType.Registered) return@withPackage
        val previousPolicy = when {
            previous.blocked -> RegisteredAppType.DENY
            previous.type == RegisteredAppType.ASK -> RegisteredAppType.ASK
            previous.type == RegisteredAppType.ALLOW || previous.type == RegisteredAppType.ALLOW_ONCE -> RegisteredAppType.ALLOW
            else -> RegisteredAppType.DENY
        }
        if (!RegisteredApplicationDb.setRegistrationPolicy(previous.packageName, previousPolicy, previous.userId)) {
            return@withPackage
        }
        val restored = RegisteredApplicationDb.getRegisteredApplication(previous.packageName) ?: return@withPackage
        RegisteredApplicationDb.update(restored.copy(registeredType = previous.registeredType))
        if (admission.pending != null) RegisteredApplicationDb.savePendingRegistration(admission.pending)
    }
}
