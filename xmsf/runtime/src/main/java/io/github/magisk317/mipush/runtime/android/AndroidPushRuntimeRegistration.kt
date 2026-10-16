package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.runtime.core.PushRegistrationRecord
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.mipush.runtime.core.PushRuntimeComponents
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow

/**
 * Registration-record seams of [AndroidPushRuntime], split out of the facade
 * object so the runtime file keeps only host wiring, lifecycle triggers and
 * payload dispatch.
 *
 * These are extension functions in the runtime package: Kotlin call sites keep
 * the plain `AndroidPushRuntime.foo(...)` syntax (an import is required outside
 * this package), and every mutation still funnels through the single
 * [AndroidPushRuntimeState] lock.
 */

private const val PERSISTED_REGISTRATION_SOURCE = "registered_application_db"

/**
 * Rebuild terminal registration observations from the persistent application registry.
 *
 * Registration records describe the current process' observations, while the application
 * registry survives process death. Only terminal states are restored here: a persisted
 * NotRegistered row is also used as a proactive-registration candidate and must not be
 * turned into a runtime record merely by opening the health snapshot.
 */
fun AndroidPushRuntime.synchronizePersistedRegistrationState(
    androidUserId: Int = RuntimeDeterministicCoordinator.currentUserId(),
): Int {
    val userId = Utils.requireValidUserId(androidUserId)
    val applications = runCatching {
        RegisteredApplicationDb.getList(null, userId)
    }.getOrElse {
        logW("unable to restore persisted registration state userId=$userId", it)
        return 0
    }
    return applyPersistedRegistrationState(applications, userId, System.currentTimeMillis())
}

/** Testable projection seam; the database read remains in the Android adapter above. */
internal fun AndroidPushRuntime.applyPersistedRegistrationState(
    applications: Collection<RuntimeRegisteredApplicationRow>,
    androidUserId: Int,
    nowMs: Long,
): Int {
    val userId = Utils.requireValidUserId(androidUserId)
    val restored = applications.mapNotNull { application ->
        if (application.userId != userId) return@mapNotNull null
        val state = when (application.registeredType) {
            RegisteredAppRegisteredType.Registered -> PushRegistrationState.Registered
            RegisteredAppRegisteredType.Unregistered -> PushRegistrationState.Unregistered
            else -> return@mapNotNull null
        }
        RuntimeDeterministicCoordinator.packageScope(application.packageName, userId) to
            PushRegistrationRecord(
                packageName = application.packageName,
                state = state,
                updatedAtMs = nowMs,
                source = PERSISTED_REGISTRATION_SOURCE,
                reason = "registered_application_db",
                androidUserId = userId,
            )
    }
    var applied = 0
    state.withLock {
        val restoredKeys = restored.mapTo(hashSetOf()) { it.first }
        registrationRecords.entries.removeIf { (key, record) ->
            key.startsWith("$userId:") &&
                record.source == PERSISTED_REGISTRATION_SOURCE &&
                key !in restoredKeys
        }
        restored.forEach { (key, record) ->
            val current = registrationRecords[key]
            // A live request/result observed after startup is newer than the persisted
            // projection and must not be replaced by a stale database read.
            if (current == null || current.source == PERSISTED_REGISTRATION_SOURCE) {
                registrationRecords[key] = record
                applied += 1
            }
        }
        AndroidPushRuntimeWindowSupport.evictOldestIfNeeded(
            registrationRecords,
            AndroidPushRuntimeWindowSupport.MAX_REGISTRATION_RECORDS,
        )
    }
    if (applied > 0) {
        logD("restored persisted registration state count=$applied userId=$userId")
    }
    return applied
}

internal fun AndroidPushRuntime.requestFrameworkRegistrationForUser(
    source: String,
    reason: String?,
    androidUserId: Int,
): Boolean {
    Utils.requireValidUserId(androidUserId)
    val host = state.withLock { executionHost } ?: return false
    if (!host.isFrameworkRegistrationEnabled()) {
        logI("framework self-registration disabled; skip source=$source userId=$androidUserId")
        return false
    }
    observeRegistrationRequest(
        packageName = PushRuntimeComponents.SERVICE_PACKAGE,
        source = source,
        reason = reason,
        androidUserId = androidUserId,
    )
    return runCatching {
        host.requestFrameworkRegistration(reason = RuntimeDeterministicCoordinator.buildReason(source, reason))
    }.getOrElse {
        logE("framework registration dispatch failed", it)
        false
    }
}

fun AndroidPushRuntime.observeRegistrationRequest(
    packageName: String,
    source: String,
    reason: String? = null,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
): PushRegistrationRecord {
    return updateRegistrationRecord(
        packageName = packageName,
        state = PushRegistrationState.Registering,
        source = source,
        reason = reason,
        nowMs = nowMs,
        androidUserId = androidUserId,
    )
}

fun AndroidPushRuntime.observeRegistrationResult(
    packageName: String,
    success: Boolean,
    source: String,
    reason: String? = null,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
): PushRegistrationRecord {
    return updateRegistrationRecord(
        packageName = packageName,
        state = if (success) PushRegistrationState.Registered else PushRegistrationState.Failed,
        source = source,
        reason = reason,
        nowMs = nowMs,
        androidUserId = androidUserId,
    )
}

fun AndroidPushRuntime.observeUnregistration(
    packageName: String,
    source: String,
    reason: String? = null,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
): PushRegistrationRecord {
    return updateRegistrationRecord(
        packageName = packageName,
        state = PushRegistrationState.Unregistered,
        source = source,
        reason = reason,
        nowMs = nowMs,
        androidUserId = androidUserId,
    )
}

fun AndroidPushRuntime.observeRegistrationState(
    packageName: String,
    state: PushRegistrationState,
    source: String,
    reason: String? = null,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
): PushRegistrationRecord {
    return updateRegistrationRecord(
        packageName = packageName,
        state = state,
        source = source,
        reason = reason,
        nowMs = nowMs,
        androidUserId = androidUserId,
    )
}

fun AndroidPushRuntime.getRegistrationRecord(
    packageName: String,
    androidUserId: Int,
): PushRegistrationRecord? = snapshotProjectionCoordinator.getRegistrationRecord(packageName, androidUserId)

fun AndroidPushRuntime.getRegistrationRecords(): List<PushRegistrationRecord> =
    snapshotProjectionCoordinator.getRegistrationRecords()

fun AndroidPushRuntime.forceTriggerRegistration(
    packageName: String,
    source: String,
    reason: String? = null,
    androidUserId: Int,
): Boolean {
    Utils.requireValidUserId(androidUserId)
    if (!registrationCoordinator.clearReplayDedupeForForce(packageName, androidUserId)) {
        logD("skip reentrant application registration package=$packageName source=$source reason=$reason")
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "runtime_force",
                "reason" to "reentrant",
                "target_package" to packageName,
                "source" to source,
            ),
            statusOk = true,
        )
        return false
    }
    val triggered = requestApplicationRegistration(packageName, source, reason, androidUserId)
    MagiskOtel.event(
        name = "push.register",
        attributes = mapOf(
            "result" to if (triggered) "ok" else "skip",
            "duration_ms" to "0",
            "process" to "xmsf",
            "stage" to "runtime_force",
            "reason" to (reason ?: "force"),
            "target_package" to packageName,
            "source" to source,
        ),
        statusOk = true,
    )
    return triggered
}

internal fun AndroidPushRuntime.updateRegistrationRecord(
    packageName: String,
    state: PushRegistrationState,
    source: String,
    reason: String?,
    nowMs: Long,
    androidUserId: Int,
): PushRegistrationRecord {
    Utils.requireValidUserId(androidUserId)
    val record = PushRegistrationRecord(
        packageName = packageName,
        state = state,
        updatedAtMs = nowMs,
        source = source,
        reason = reason,
        androidUserId = androidUserId,
    )
    this.state.withLock {
        registrationRecords[RuntimeDeterministicCoordinator.packageScope(packageName, androidUserId)] = record
        AndroidPushRuntimeWindowSupport.evictOldestIfNeeded(
            registrationRecords,
            AndroidPushRuntimeWindowSupport.MAX_REGISTRATION_RECORDS,
        )
        lastRegistrationPackage = packageName
        lastRegistrationState = state
        updateLastObservationLocked(packageName, "registration:${state.name}")
    }
    return record
}

internal fun AndroidPushRuntime.replayPendingApplicationRegistrations(
    source: String,
    reason: String,
    limit: Int = 8,
    androidUserId: Int,
): Int = registrationCoordinator.replayPending(source, reason, limit, androidUserId)

internal fun AndroidPushRuntime.replayApplicationRegistration(
    packageName: String,
    source: String,
    reason: String?,
    androidUserId: Int,
): Boolean = registrationCoordinator.dispatchApplication(packageName, source, reason, androidUserId)
