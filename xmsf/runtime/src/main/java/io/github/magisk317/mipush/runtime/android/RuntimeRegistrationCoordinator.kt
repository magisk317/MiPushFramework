package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.mipush.runtime.core.PushRuntimeComponents

/** Coordinates application-registration replay and dedupe while using the runtime's sole state lock. */
internal class RuntimeRegistrationCoordinator(
    private val state: AndroidPushRuntimeState,
    private val packageScope: (String, Int) -> String,
    private val buildReason: (String, String?) -> String,
    private val replayWindowMs: Long,
    private val pruneWindows: (Long) -> Unit,
) {
    fun clearReplayDedupeForForce(
        packageName: String,
        androidUserId: Int,
    ): Boolean = state.withLock {
        val packageKey = packageScope(packageName, androidUserId)
        if (activeRegistrationDispatches.contains(packageKey)) return false
        recentRegistrationReplays.remove(packageKey)
        recentPackageActions.remove("$packageKey:registration:Registering")
        true
    }

    fun replayPending(
        source: String,
        reason: String,
        limit: Int = 8,
        androidUserId: Int,
    ): Int {
        val nowMs = System.currentTimeMillis()
        val pendingPackages = state.withLock {
            pruneWindows(nowMs)
            if (executionHost == null) return 0
            registrationRecords.values
                .asSequence()
                .filter { it.packageName != PushRuntimeComponents.SERVICE_PACKAGE }
                .filter {
                    it.state == PushRegistrationState.Registering ||
                        it.state == PushRegistrationState.Failed ||
                        it.state == PushRegistrationState.NotRegistered
                }
                .filterNot { activeRegistrationDispatches.contains(packageScope(it.packageName, androidUserId)) }
                .filter { shouldReplayLocked(it.packageName, nowMs, androidUserId) }
                .take(limit)
                .map { it.packageName }
                .toList()
        }
        val dispatched = pendingPackages.count { dispatchApplication(it, source, reason, androidUserId) }
        if (dispatched > 0) {
            logD("replayed pending application registrations count=$dispatched source=$source reason=$reason")
        }
        return dispatched
    }

    fun dispatchApplication(
        packageName: String,
        source: String,
        reason: String?,
        androidUserId: Int,
    ): Boolean {
        val host = state.withLock {
            val packageKey = packageScope(packageName, androidUserId)
            if (activeRegistrationDispatches.contains(packageKey)) {
                logD("skip active application registration package=$packageName source=$source reason=$reason")
                return false
            }
            val activeHost = executionHost ?: return false
            recentRegistrationReplays[packageKey] = System.currentTimeMillis()
            activeRegistrationDispatches += packageKey
            activeHost
        }
        return runCatching {
            host.requestApplicationRegistration(packageName, buildReason(source, reason))
        }.getOrElse {
            logE("requestApplicationRegistration failed package=$packageName", it)
            false
        }.also {
            state.withLock { activeRegistrationDispatches -= packageScope(packageName, androidUserId) }
        }
    }

    private fun shouldReplayLocked(
        packageName: String,
        nowMs: Long,
        androidUserId: Int,
    ): Boolean {
        val previous = state.recentRegistrationReplays[packageScope(packageName, androidUserId)] ?: return true
        return (nowMs - previous) > replayWindowMs
    }
}
